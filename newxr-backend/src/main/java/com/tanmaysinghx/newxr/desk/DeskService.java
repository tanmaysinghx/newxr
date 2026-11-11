package com.tanmaysinghx.newxr.desk;

import com.tanmaysinghx.newxr.common.ApiException;
import com.tanmaysinghx.newxr.events.DomainEvent;
import com.tanmaysinghx.newxr.events.EventBus;
import com.tanmaysinghx.newxr.hub.HubContributor;
import com.tanmaysinghx.newxr.hub.WorkItem;
import com.tanmaysinghx.newxr.security.CurrentUser;
import com.tanmaysinghx.newxr.user.User;
import com.tanmaysinghx.newxr.user.UserRef;
import com.tanmaysinghx.newxr.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Service desks: queues, requests with SLAs, replies and internal notes, and the customer help center
 * ({@code /api/desk/help/**}), which never exposes internal notes and only shows a customer their own requests.
 */
@RestController
@RequestMapping("/api/desk")
@Transactional
public class DeskService implements HubContributor {

    static final List<String> STATUSES = List.of("WAITING_FOR_SUPPORT", "IN_PROGRESS", "WAITING_FOR_CUSTOMER", "ESCALATED", "RESOLVED", "CLOSED");
    static final List<String> DONE = List.of("RESOLVED", "CLOSED");
    static final List<String> PRIORITIES = List.of("P1", "P2", "P3", "P4");
    /** Breaching soon = less than this left on a running clock. */
    static final Duration SOON = Duration.ofMinutes(30);

    /** First response and resolution targets per priority. */
    public record SlaPolicy(String priority, String label, long firstResponseMinutes, long resolutionMinutes) {}

    static final Map<String, SlaPolicy> POLICIES = new LinkedHashMap<>();

    static {
        POLICIES.put("P1", new SlaPolicy("P1", "Critical", 30, 4 * 60));
        POLICIES.put("P2", new SlaPolicy("P2", "High", 60, 8 * 60));
        POLICIES.put("P3", new SlaPolicy("P3", "Medium", 4 * 60, 24 * 60));
        POLICIES.put("P4", new SlaPolicy("P4", "Low", 8 * 60, 72 * 60));
    }

    /**
     * @param state     RUNNING, BREACHED (clock still running, past due), PAUSED, MET, MISSED (stopped after the due time)
     * @param minutes   minutes left (negative once breached) while running/paused; time taken once stopped
     */
    public record Sla(String name, Instant due, String state, long minutes) {}

    public record DeskSummary(Long id, String key, String name, String description, String color, String icon, long open) {}

    public record TypeView(Long id, String name, String description, String icon, String category, Long deskId, String deskKey, String deskName) {}

    public record Queue(String id, String name, long count) {}

    public record DeskView(DeskSummary desk, List<Queue> queues, List<TypeView> types, List<SlaPolicy> policies, List<UserRef> agents) {}

    public record Stats(long open, long newToday, Long avgFirstResponseMinutes, long breachingSoon, Double csat, Double csatDelta) {}

    public record RequestRow(Long id, String key, String summary, UserRef reporter, UserRef assignee, String type, String category, String status,
                             String priority, Sla firstResponse, Sla resolution, Instant createdAt, Instant updatedAt) {}

    public record CommentView(Long id, UserRef author, String body, boolean internal, Instant createdAt) {}

    public record Similar(String key, String summary, String status, Instant resolvedAt, String resolution) {}

    public record CustomerView(UserRef user, String jobTitle, String organization, long open, long total, Double csat) {}

    public record RequestView(Long id, String key, String summary, String description, String deskKey, String deskName, TypeView type,
                              UserRef reporter, UserRef assignee, String status, String priority, String organization, String component,
                              String linked, Sla firstResponse, Sla resolution, Integer csat, Instant createdAt, Instant updatedAt,
                              Instant resolvedAt, List<CommentView> comments, List<Similar> similar, CustomerView customer) {}

    public record CreateRequest(Long typeId, String summary, String description, String priority, Long reporterId, String organization,
                                String component) {}

    public record EditRequest(String status, String priority, Long assigneeId, Boolean unassign, String organization, String component,
                              String linked, String summary, String description) {}

    public record CommentRequest(String body, Boolean internal) {}

    public record CsatRequest(Integer rating) {}

    public record HelpHome(List<DeskSummary> desks, List<TypeView> popular, List<RequestRow> mine) {}

    private final ServiceDeskRepository desks;
    private final RequestTypeRepository types;
    private final ServiceRequestRepository requests;
    private final RequestCommentRepository comments;
    private final UserRepository users;
    private final CurrentUser current;
    private final EventBus events;

    public DeskService(ServiceDeskRepository desks, RequestTypeRepository types, ServiceRequestRepository requests,
                       RequestCommentRepository comments, UserRepository users, CurrentUser current, EventBus events) {
        this.desks = desks;
        this.types = types;
        this.requests = requests;
        this.comments = comments;
        this.users = users;
        this.current = current;
        this.events = events;
    }

    // ---- agent view ---------------------------------------------------------------

    @GetMapping("/desks")
    @Transactional(readOnly = true)
    public List<DeskSummary> list() {
        return desks.findAllByOrderByNameAsc().stream().map(this::summary).toList();
    }

    @GetMapping("/desks/{key}")
    @Transactional(readOnly = true)
    public DeskView desk(@PathVariable String key) {
        User me = current.require();
        ServiceDesk d = desk0(key);
        List<ServiceRequest> all = requests.findByDeskOrderByCreatedAtDesc(d);
        List<ServiceRequest> open = all.stream().filter(r -> !DONE.contains(r.getStatus())).toList();
        Instant now = Instant.now();
        List<Queue> queues = new ArrayList<>();
        queues.add(new Queue("all", "All open", open.size()));
        queues.add(new Queue("mine", "Assigned to me", open.stream().filter(r -> isUser(r.getAssignee(), me)).count()));
        queues.add(new Queue("unassigned", "Unassigned", open.stream().filter(r -> r.getAssignee() == null).count()));
        queues.add(new Queue("breaching", "SLA breaching", open.stream().filter(r -> breaching(r, now)).count()));
        types.findByDeskOrderByIdAsc(d).stream().map(RequestType::getCategory).filter(Objects::nonNull).distinct()
                .forEach(c -> queues.add(new Queue("category:" + c, c, open.stream().filter(r -> r.getType() != null && c.equals(r.getType().getCategory())).count())));
        queues.add(new Queue("resolved", "Resolved", all.size() - open.size()));
        Set<Long> seen = new HashSet<>();
        List<UserRef> agents = all.stream().map(ServiceRequest::getAssignee).filter(Objects::nonNull).filter(u -> seen.add(u.getId()))
                .map(UserRef::of).toList();
        return new DeskView(summary(d), queues, types.findByDeskOrderByIdAsc(d).stream().map(DeskService::type).toList(),
                List.copyOf(POLICIES.values()), agents);
    }

    @GetMapping("/desks/{key}/requests")
    @Transactional(readOnly = true)
    public List<RequestRow> queue(@PathVariable String key, @RequestParam(defaultValue = "all") String queue,
                                  @RequestParam(required = false) String q, @RequestParam(required = false) Long type,
                                  @RequestParam(required = false) String status, @RequestParam(required = false) String priority) {
        User me = current.require();
        Instant now = Instant.now();
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        return requests.findByDeskOrderByCreatedAtDesc(desk0(key)).stream()
                .filter(r -> inQueue(r, queue, me, now))
                .filter(r -> query.isEmpty() || r.getSummary().toLowerCase(Locale.ROOT).contains(query) || r.getRequestKey().toLowerCase(Locale.ROOT).equals(query))
                .filter(r -> type == null || (r.getType() != null && r.getType().getId().equals(type)))
                .filter(r -> blank(status) || r.getStatus().equals(status))
                .filter(r -> blank(priority) || r.getPriority().equals(priority))
                .sorted(Comparator.comparing((ServiceRequest r) -> DONE.contains(r.getStatus()))
                        .thenComparing(r -> sortDue(r, now)))
                .map(r -> row(r, now)).toList();
    }

    @GetMapping("/desks/{key}/stats")
    @Transactional(readOnly = true)
    public Stats stats(@PathVariable String key) {
        List<ServiceRequest> all = requests.findByDeskOrderByCreatedAtDesc(desk0(key));
        Instant now = Instant.now();
        Instant today = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant d30 = now.minus(Duration.ofDays(30));
        Instant d60 = now.minus(Duration.ofDays(60));
        long open = all.stream().filter(r -> !DONE.contains(r.getStatus())).count();
        long newToday = all.stream().filter(r -> !r.getCreatedAt().isBefore(today)).count();
        Double avgFirst = all.stream().filter(r -> r.getFirstRespondedAt() != null && r.getCreatedAt().isAfter(d30))
                .mapToLong(r -> Duration.between(r.getCreatedAt(), r.getFirstRespondedAt()).toMinutes()).average().stream().boxed().findFirst().orElse(null);
        long soon = all.stream().filter(r -> !DONE.contains(r.getStatus())).filter(r -> {
            Sla s = resolutionSla(r, now);
            return "RUNNING".equals(s.state()) && s.minutes() <= SOON.toMinutes();
        }).count();
        Double csat = csat(all, d30, now);
        Double before = csat(all, d60, d30);
        Double delta = csat == null || before == null ? null : Math.round((csat - before) * 10) / 10.0;
        return new Stats(open, newToday, avgFirst == null ? null : Math.round(avgFirst), soon, csat, delta);
    }

    @GetMapping("/desks/{key}/customers")
    @Transactional(readOnly = true)
    public List<CustomerView> customers(@PathVariable String key) {
        ServiceDesk d = desk0(key);
        Map<Long, List<ServiceRequest>> byReporter = requests.findByDeskOrderByCreatedAtDesc(d).stream().filter(r -> r.getReporter() != null)
                .collect(Collectors.groupingBy(r -> r.getReporter().getId(), LinkedHashMap::new, Collectors.toList()));
        return byReporter.values().stream().map(rs -> customer(rs.getFirst().getReporter(), rs))
                .sorted(Comparator.comparing((CustomerView c) -> -c.open()).thenComparing(c -> c.user().name())).toList();
    }

    @GetMapping("/sla-policies")
    public List<SlaPolicy> policies() {
        return List.copyOf(POLICIES.values());
    }

    @PostMapping("/desks/{key}/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public RequestView raise(@PathVariable String key, @RequestBody CreateRequest r) {
        User me = current.require();
        User reporter = r.reporterId() == null ? me : users.findById(r.reporterId()).orElseThrow(() -> ApiException.notFound("User"));
        ServiceRequest s = create(desk0(key), r, reporter, me);
        return view(s, true);
    }

    @GetMapping("/requests/{key}")
    @Transactional(readOnly = true)
    public RequestView get(@PathVariable String key) {
        current.require();
        return view(request(key), true);
    }

    @PutMapping("/requests/{key}")
    public RequestView edit(@PathVariable String key, @RequestBody EditRequest e) {
        User me = current.require();
        ServiceRequest r = request(key);
        Instant now = Instant.now();
        if (e.summary() != null && !e.summary().isBlank()) r.setSummary(e.summary().trim());
        if (e.description() != null) r.setDescription(e.description());
        if (e.organization() != null) r.setOrganization(blank(e.organization()) ? null : e.organization().trim());
        if (e.component() != null) r.setComponent(blank(e.component()) ? null : e.component().trim());
        if (e.linked() != null) r.setLinked(blank(e.linked()) ? null : e.linked().trim());
        if (e.priority() != null && !e.priority().equals(r.getPriority())) {
            if (!PRIORITIES.contains(e.priority())) throw ApiException.badRequest("Priority must be P1, P2, P3 or P4.");
            String old = r.getPriority();
            r.setPriority(e.priority());
            applyPolicy(r, old);
        }
        if (Boolean.TRUE.equals(e.unassign())) {
            r.setAssignee(null);
        } else if (e.assigneeId() != null && !isUser(r.getAssignee(), e.assigneeId())) {
            User a = users.findById(e.assigneeId()).orElseThrow(() -> ApiException.notFound("User"));
            r.setAssignee(a);
            if (!a.getId().equals(me.getId())) {
                publish("request.assigned", r, me, "assigned you " + r.getRequestKey() + " " + r.getSummary(), List.of(a.getId()), Map.of());
            }
        }
        if (e.status() != null && !e.status().equals(r.getStatus())) {
            if (!STATUSES.contains(e.status())) throw ApiException.badRequest("Unknown status " + e.status());
            transition(r, e.status(), now);
            publish("request.status", r, me, "moved " + r.getRequestKey() + " to " + e.status().replace('_', ' ').toLowerCase(Locale.ROOT),
                    reporterId(r), Map.of("status", e.status()));
        }
        r.setUpdatedAt(now);
        return view(r, true);
    }

    @PostMapping("/requests/{key}/comments")
    public RequestView comment(@PathVariable String key, @RequestBody CommentRequest c) {
        User me = current.require();
        ServiceRequest r = request(key);
        boolean internal = Boolean.TRUE.equals(c.internal());
        addComment(r, me, c.body(), internal);
        Instant now = Instant.now();
        if (!internal && !isUser(r.getReporter(), me)) {
            if (r.getFirstRespondedAt() == null) r.setFirstRespondedAt(now);
            if (r.getAssignee() == null) r.setAssignee(me);
            if ("WAITING_FOR_SUPPORT".equals(r.getStatus())) transition(r, "WAITING_FOR_CUSTOMER", now);
            publish("request.replied", r, me, "replied to " + r.getRequestKey() + " " + r.getSummary(), reporterId(r), Map.of("notifyKind", "reporter"));
        }
        r.setUpdatedAt(now);
        return view(r, true);
    }

    // ---- help center (customers) --------------------------------------------------

    @GetMapping("/help")
    @Transactional(readOnly = true)
    public HelpHome help() {
        User me = current.require();
        List<RequestType> all = types.findAll();
        List<TypeView> popular = all.stream().sorted(Comparator.comparing((RequestType t) -> -requests.countByType(t)).thenComparing(RequestType::getId))
                .limit(6).map(DeskService::type).toList();
        Instant now = Instant.now();
        List<RequestRow> mine = requests.findByReporterOrderByCreatedAtDesc(me, PageRequest.of(0, 5)).stream().map(r -> row(r, now)).toList();
        return new HelpHome(list(), popular, mine);
    }

    @GetMapping("/help/desks/{key}")
    @Transactional(readOnly = true)
    public List<TypeView> helpTypes(@PathVariable String key) {
        return types.findByDeskOrderByIdAsc(desk0(key)).stream().map(DeskService::type).toList();
    }

    @GetMapping("/help/requests")
    @Transactional(readOnly = true)
    public List<RequestRow> myRequests() {
        User me = current.require();
        Instant now = Instant.now();
        return requests.findByReporterOrderByCreatedAtDesc(me).stream().map(r -> row(r, now)).toList();
    }

    @PostMapping("/help/desks/{key}/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public RequestView helpRaise(@PathVariable String key, @RequestBody CreateRequest r) {
        User me = current.require();
        CreateRequest own = new CreateRequest(r.typeId(), r.summary(), r.description(), "P3", null, r.organization(), null);
        return view(create(desk0(key), own, me, me), false);
    }

    @GetMapping("/help/requests/{key}")
    @Transactional(readOnly = true)
    public RequestView helpGet(@PathVariable String key) {
        return view(own(key), false);
    }

    @PostMapping("/help/requests/{key}/comments")
    public RequestView helpComment(@PathVariable String key, @RequestBody CommentRequest c) {
        User me = current.require();
        ServiceRequest r = own(key);
        addComment(r, me, c.body(), false);
        Instant now = Instant.now();
        if ("WAITING_FOR_CUSTOMER".equals(r.getStatus())) transition(r, "WAITING_FOR_SUPPORT", now);
        if ("RESOLVED".equals(r.getStatus())) transition(r, "WAITING_FOR_SUPPORT", now);
        r.setUpdatedAt(now);
        publish("request.customer_replied", r, me, "replied on " + r.getRequestKey() + " " + r.getSummary(),
                r.getAssignee() == null ? List.of() : List.of(r.getAssignee().getId()), Map.of("notifyKind", "assigned"));
        return view(r, false);
    }

    @PostMapping("/help/requests/{key}/resolve")
    public RequestView helpResolve(@PathVariable String key) {
        ServiceRequest r = own(key);
        if (!DONE.contains(r.getStatus())) transition(r, "RESOLVED", Instant.now());
        return view(r, false);
    }

    @PostMapping("/help/requests/{key}/csat")
    public RequestView rate(@PathVariable String key, @RequestBody CsatRequest c) {
        ServiceRequest r = own(key);
        if (!DONE.contains(r.getStatus())) throw ApiException.badRequest("You can rate a request once it is resolved.");
        if (c.rating() == null || c.rating() < 1 || c.rating() > 5) throw ApiException.badRequest("Rate from 1 to 5.");
        r.setCsat(c.rating());
        return view(r, false);
    }

    // ---- workflow -----------------------------------------------------------------

    private ServiceRequest create(ServiceDesk d, CreateRequest c, User reporter, User actor) {
        if (blank(c.summary())) throw ApiException.badRequest("Summarise the request.");
        RequestType t = null;
        if (c.typeId() != null) {
            t = types.findById(c.typeId()).orElseThrow(() -> ApiException.notFound("Request type"));
            if (!t.getDesk().getId().equals(d.getId())) throw ApiException.badRequest("That request type belongs to another service project.");
        }
        ServiceDesk locked = desks.lockById(d.getId()).orElseThrow(() -> ApiException.notFound("Service project"));
        int seq = locked.getRequestSeq() + 1;
        locked.setRequestSeq(seq);
        ServiceRequest r = new ServiceRequest();
        r.setDesk(locked);
        r.setType(t);
        r.setRequestKey(locked.getDeskKey() + "-" + seq);
        r.setSummary(c.summary().trim());
        r.setDescription(c.description());
        r.setReporter(reporter);
        r.setPriority(c.priority() != null && PRIORITIES.contains(c.priority()) ? c.priority() : "P3");
        r.setOrganization(blank(c.organization()) ? null : c.organization().trim());
        r.setComponent(blank(c.component()) ? null : c.component().trim());
        r.setCreatedAt(Instant.now());
        applyPolicy(r, null);
        r = requests.save(r);
        publish("request.created", r, UserRef.of(actor), "raised " + r.getRequestKey() + " " + r.getSummary(), List.of(), Map.of("priority", r.getPriority()));
        return r;
    }

    /**
     * Due dates from the request's creation time and priority. On a priority change the time already spent
     * waiting for the customer (the gap between the old due date and the old target) is carried over.
     */
    static void applyPolicy(ServiceRequest r, String oldPriority) {
        SlaPolicy p = POLICIES.getOrDefault(r.getPriority(), POLICIES.get("P3"));
        Instant created = r.getCreatedAt() == null ? Instant.now() : r.getCreatedAt();
        Duration paused = Duration.ZERO;
        if (oldPriority != null && r.getResolutionDue() != null) {
            SlaPolicy old = POLICIES.getOrDefault(oldPriority, POLICIES.get("P3"));
            paused = Duration.between(created.plus(Duration.ofMinutes(old.resolutionMinutes())), r.getResolutionDue());
            if (paused.isNegative()) paused = Duration.ZERO;
        }
        r.setFirstResponseDue(created.plus(Duration.ofMinutes(p.firstResponseMinutes())));
        r.setResolutionDue(created.plus(Duration.ofMinutes(p.resolutionMinutes())).plus(paused));
    }

    /** Moves a request to a new status, stopping and restarting the resolution clock around customer waits. */
    static void transition(ServiceRequest r, String status, Instant now) {
        String from = r.getStatus();
        if ("WAITING_FOR_CUSTOMER".equals(from) && r.getSlaPausedAt() != null) {
            if (r.getResolutionDue() != null) r.setResolutionDue(r.getResolutionDue().plus(Duration.between(r.getSlaPausedAt(), now)));
            r.setSlaPausedAt(null);
        }
        if ("WAITING_FOR_CUSTOMER".equals(status)) r.setSlaPausedAt(now);
        if (DONE.contains(status)) {
            if (r.getResolvedAt() == null) r.setResolvedAt(now);
        } else {
            r.setResolvedAt(null);
        }
        if (!"WAITING_FOR_SUPPORT".equals(status) && r.getFirstRespondedAt() == null && !DONE.contains(status)) {
            r.setFirstRespondedAt(now);
        }
        r.setStatus(status);
    }

    private void addComment(ServiceRequest r, User me, String body, boolean internal) {
        if (blank(body)) throw ApiException.badRequest("Write a reply first.");
        RequestComment c = new RequestComment();
        c.setRequest(r);
        c.setAuthor(me);
        c.setBody(body.trim());
        c.setInternal(internal);
        comments.save(c);
    }

    // ---- SLA maths ----------------------------------------------------------------

    static Sla firstResponseSla(ServiceRequest r, Instant now) {
        return clock("Time to first response", r.getCreatedAt(), r.getFirstResponseDue(), r.getFirstRespondedAt(), null, now);
    }

    static Sla resolutionSla(ServiceRequest r, Instant now) {
        return clock("Time to resolution", r.getCreatedAt(), r.getResolutionDue(), r.getResolvedAt(), r.getSlaPausedAt(), now);
    }

    private static Sla clock(String name, Instant start, Instant due, Instant stopped, Instant pausedAt, Instant now) {
        if (due == null) return new Sla(name, null, "NONE", 0);
        if (stopped != null) {
            return new Sla(name, due, stopped.isAfter(due) ? "MISSED" : "MET", Duration.between(start, stopped).toMinutes());
        }
        if (pausedAt != null) return new Sla(name, due, "PAUSED", Duration.between(pausedAt, due).toMinutes());
        long left = Duration.between(now, due).toMinutes();
        return new Sla(name, due, now.isAfter(due) ? "BREACHED" : "RUNNING", left);
    }

    private static boolean breaching(ServiceRequest r, Instant now) {
        if (DONE.contains(r.getStatus())) return false;
        Sla res = resolutionSla(r, now);
        Sla first = firstResponseSla(r, now);
        return "BREACHED".equals(res.state()) || "BREACHED".equals(first.state())
                || ("RUNNING".equals(res.state()) && res.minutes() <= SOON.toMinutes());
    }

    private static long sortDue(ServiceRequest r, Instant now) {
        Sla s = resolutionSla(r, now);
        return switch (s.state()) {
            case "RUNNING", "BREACHED" -> s.minutes();
            case "PAUSED" -> Long.MAX_VALUE / 2 + s.minutes();
            default -> Long.MAX_VALUE;
        };
    }

    private static Double csat(List<ServiceRequest> rs, Instant from, Instant to) {
        return rs.stream().filter(r -> r.getCsat() != null && r.getResolvedAt() != null && r.getResolvedAt().isAfter(from) && !r.getResolvedAt().isAfter(to))
                .mapToInt(ServiceRequest::getCsat).average().stream().boxed().findFirst().map(v -> Math.round(v * 10) / 10.0).orElse(null);
    }

    // ---- views --------------------------------------------------------------------

    private boolean inQueue(ServiceRequest r, String queue, User me, Instant now) {
        boolean open = !DONE.contains(r.getStatus());
        if (queue.startsWith("category:")) {
            return open && r.getType() != null && queue.substring(9).equals(r.getType().getCategory());
        }
        return switch (queue) {
            case "mine" -> open && isUser(r.getAssignee(), me);
            case "unassigned" -> open && r.getAssignee() == null;
            case "breaching" -> breaching(r, now);
            case "resolved" -> !open;
            case "everything" -> true;
            default -> open;
        };
    }

    private RequestRow row(ServiceRequest r, Instant now) {
        RequestType t = r.getType();
        return new RequestRow(r.getId(), r.getRequestKey(), r.getSummary(), UserRef.of(r.getReporter()), UserRef.of(r.getAssignee()),
                t == null ? null : t.getName(), t == null ? null : t.getCategory(), r.getStatus(), r.getPriority(),
                firstResponseSla(r, now), resolutionSla(r, now), r.getCreatedAt(), r.getUpdatedAt());
    }

    private RequestView view(ServiceRequest r, boolean agent) {
        Instant now = Instant.now();
        List<CommentView> cs = comments.findByRequestOrderByCreatedAtAsc(r).stream().filter(c -> agent || !c.isInternal())
                .map(c -> new CommentView(c.getId(), UserRef.of(c.getAuthor()), c.getBody(), c.isInternal(), c.getCreatedAt())).toList();
        List<Similar> similar = agent ? similar(r) : List.of();
        CustomerView customer = agent && r.getReporter() != null ? customer(r.getReporter(), requests.findByDeskAndReporter(r.getDesk(), r.getReporter())) : null;
        return new RequestView(r.getId(), r.getRequestKey(), r.getSummary(), r.getDescription(), r.getDesk().getDeskKey(), r.getDesk().getName(),
                r.getType() == null ? null : type(r.getType()), UserRef.of(r.getReporter()), UserRef.of(r.getAssignee()), r.getStatus(),
                r.getPriority(), r.getOrganization(), agent ? r.getComponent() : null, agent ? r.getLinked() : null,
                firstResponseSla(r, now), resolutionSla(r, now), r.getCsat(), r.getCreatedAt(), r.getUpdatedAt(), r.getResolvedAt(), cs,
                similar, customer);
    }

    /** Resolved requests of the same type sharing at least one word in the summary, ranked by shared words in the summary, with the last public reply as the fix. */
    private List<Similar> similar(ServiceRequest r) {
        if (r.getType() == null) return List.of();
        Set<String> words = words(r.getSummary());
        return requests.findByTypeAndStatusInAndIdNotOrderByResolvedAtDesc(r.getType(), DONE, r.getId(), PageRequest.of(0, 30)).stream()
                .map(o -> Map.entry(o, words(o.getSummary()).stream().filter(words::contains).count()))
                .filter(e -> e.getValue() > 0)
                .sorted(Comparator.comparing((Map.Entry<ServiceRequest, Long> e) -> -e.getValue()))
                .limit(3)
                .map(e -> {
                    ServiceRequest o = e.getKey();
                    String fix = comments.findByRequestOrderByCreatedAtAsc(o).stream().filter(c -> !c.isInternal())
                            .reduce((a, b) -> b).map(RequestComment::getBody).orElse(null);
                    return new Similar(o.getRequestKey(), o.getSummary(), o.getStatus(), o.getResolvedAt(), fix);
                }).toList();
    }

    private static final Set<String> STOP = Set.of("the", "a", "an", "to", "for", "on", "in", "of", "and", "not", "is", "my", "with", "from", "new");

    private static Set<String> words(String s) {
        return Arrays.stream(s.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")).filter(w -> w.length() > 2 && !STOP.contains(w))
                .collect(Collectors.toSet());
    }

    private CustomerView customer(User u, List<ServiceRequest> rs) {
        long open = rs.stream().filter(r -> !DONE.contains(r.getStatus())).count();
        Double csat = rs.stream().filter(r -> r.getCsat() != null).mapToInt(ServiceRequest::getCsat).average().stream().boxed().findFirst()
                .map(v -> Math.round(v * 10) / 10.0).orElse(null);
        String org = rs.stream().map(ServiceRequest::getOrganization).filter(Objects::nonNull).findFirst().orElse(null);
        return new CustomerView(UserRef.of(u), u.getJobTitle(), org, open, rs.size(), csat);
    }

    private DeskSummary summary(ServiceDesk d) {
        return new DeskSummary(d.getId(), d.getDeskKey(), d.getName(), d.getDescription(), d.getColor(), d.getIcon(),
                requests.countByDeskAndStatusNotIn(d, DONE));
    }

    private static TypeView type(RequestType t) {
        return new TypeView(t.getId(), t.getName(), t.getDescription(), t.getIcon(), t.getCategory(), t.getDesk().getId(),
                t.getDesk().getDeskKey(), t.getDesk().getName());
    }

    private ServiceDesk desk0(String key) {
        return desks.findByDeskKeyIgnoreCase(key).orElseThrow(() -> ApiException.notFound("Service project"));
    }

    ServiceRequest request(String key) {
        return requests.findByRequestKeyIgnoreCase(key).orElseThrow(() -> ApiException.notFound("Request"));
    }

    /** A request the signed-in customer raised; anything else looks like it does not exist. */
    private ServiceRequest own(String key) {
        User me = current.require();
        ServiceRequest r = request(key);
        if (!isUser(r.getReporter(), me)) throw ApiException.notFound("Request");
        return r;
    }

    private void publish(String type, ServiceRequest r, User actor, String summary, List<Long> recipients, Map<String, Object> data) {
        publish(type, r, UserRef.of(actor), summary, recipients, data);
    }

    private void publish(String type, ServiceRequest r, UserRef actor, String summary, List<Long> recipients, Map<String, Object> data) {
        List<Long> to = new ArrayList<>(recipients);
        if (r.getAssignee() != null && "request.created".equals(type)) to.add(r.getAssignee().getId());
        events.publish(DomainEvent.of(type, "desk", "request", r.getRequestKey(), r.getSummary(), link(r), actor, summary, to, data));
    }

    private static List<Long> reporterId(ServiceRequest r) {
        return r.getReporter() == null ? List.of() : List.of(r.getReporter().getId());
    }

    static String link(ServiceRequest r) {
        return "/desk/" + r.getDesk().getDeskKey() + "/" + r.getRequestKey();
    }

    private static boolean isUser(User u, User other) {
        return u != null && other != null && u.getId().equals(other.getId());
    }

    private static boolean isUser(User u, Long id) {
        return u != null && u.getId().equals(id);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    // ---- hub ----------------------------------------------------------------------

    @Override
    public String app() {
        return "desk";
    }

    @Override
    public List<WorkItem> assignedTo(User user) {
        Instant now = Instant.now();
        return requests.findByAssigneeAndStatusNotInOrderByUpdatedAtDesc(user, DONE).stream()
                .map(r -> {
                    Sla s = resolutionSla(r, now);
                    WorkItem w = WorkItem.of("desk", "request", r.getRequestKey(), r.getSummary(), r.getDesk().getName(), r.getStatus(),
                            r.getPriority(), link(r), r.getUpdatedAt());
                    return s.due() == null ? w : w.withDue(s.due().toString());
                }).toList();
    }

    @Override
    public List<WorkItem> waitingOn(User user) {
        return requests.findByReporterOrderByCreatedAtDesc(user).stream().filter(r -> "WAITING_FOR_CUSTOMER".equals(r.getStatus()))
                .map(r -> WorkItem.of("desk", "request", r.getRequestKey(), "Reply to support: " + r.getSummary(), r.getDesk().getName() + " · waiting for you",
                        r.getStatus(), r.getPriority(), "/help/requests/" + r.getRequestKey(), r.getUpdatedAt()))
                .toList();
    }

    @Override
    public List<WorkItem> search(String q, User user, int limit) {
        return requests.search(q, q.trim().toUpperCase(Locale.ROOT), PageRequest.of(0, limit)).stream()
                .map(r -> WorkItem.of("desk", "request", r.getRequestKey(), r.getSummary(),
                        r.getDesk().getName() + (r.getReporter() == null ? "" : " · " + r.getReporter().getDisplayName()),
                        r.getStatus(), r.getPriority(), link(r), r.getUpdatedAt()))
                .toList();
    }
}
