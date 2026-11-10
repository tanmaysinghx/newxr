package com.tanmaysinghx.newxr.hub;

import com.tanmaysinghx.newxr.admin.ProductService;
import com.tanmaysinghx.newxr.security.CurrentUser;
import com.tanmaysinghx.newxr.user.User;
import com.tanmaysinghx.newxr.user.UserRef;
import com.tanmaysinghx.newxr.user.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Home ("For you"), recent, starred and global search across every app. */
@RestController
@RequestMapping("/api/hub")
public class HubController {

    private static final Logger log = LoggerFactory.getLogger(HubController.class);

    public record ViewRequest(String app, String kind, String key, String title, String subtitle, String link) {}

    public record StarRequest(String app, String kind, String title, String link) {}

    public record Home(List<WorkItem> assigned, List<WorkItem> waiting, List<RecentItem> recent, List<StarItem> starred) {}

    public record RecentItem(String app, String kind, String key, String title, String subtitle, String link, Instant viewedAt) {}

    public record StarItem(Long id, String app, String kind, String title, String link) {}

    public record SearchResult(String query, int total, Map<String, Long> counts, List<WorkItem> items) {}

    private final List<HubContributor> contributors;
    private final RecentViewRepository recents;
    private final StarRepository stars;
    private final CurrentUser current;
    private final ProductService products;
    private final UserRepository users;

    public HubController(List<HubContributor> contributors, RecentViewRepository recents, StarRepository stars,
                         CurrentUser current, ProductService products, UserRepository users) {
        this.contributors = contributors;
        this.recents = recents;
        this.stars = stars;
        this.current = current;
        this.products = products;
        this.users = users;
    }

    @GetMapping("/home")
    @Transactional(readOnly = true)
    public Home home() {
        User me = current.require();
        Set<String> apps = products.appsFor(me);
        List<WorkItem> assigned = new ArrayList<>();
        List<WorkItem> waiting = new ArrayList<>();
        for (HubContributor c : contributors) {
            if (!apps.contains(c.app())) continue;
            try {
                assigned.addAll(c.assignedTo(me));
                waiting.addAll(c.waitingOn(me));
            } catch (RuntimeException e) {
                log.warn("Home contributor {} failed: {}", c.app(), e.getMessage());
            }
        }
        assigned.sort(Comparator.comparing(WorkItem::updatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        waiting.sort(Comparator.comparing(WorkItem::updatedAt, Comparator.nullsLast(Comparator.reverseOrder())));
        return new Home(assigned, waiting, recent(12), starred());
    }

    @GetMapping("/recent")
    @Transactional(readOnly = true)
    public List<RecentItem> recent(@RequestParam(defaultValue = "30") int limit) {
        return recents.findByUserIdOrderByViewedAtDesc(current.id(), PageRequest.of(0, Math.min(limit, 100))).stream()
                .map(r -> new RecentItem(r.getApp(), r.getKind(), r.getItemKey(), r.getTitle(), r.getSubtitle(), r.getLink(), r.getViewedAt()))
                .toList();
    }

    @PostMapping("/recent")
    @Transactional
    public void viewed(@RequestBody ViewRequest v) {
        if (v.link() == null || !v.link().startsWith("/")) return;
        Long me = current.id();
        RecentView r = recents.findByUserIdAndLink(me, v.link()).orElseGet(RecentView::new);
        r.setUserId(me);
        r.setApp(v.app());
        r.setKind(v.kind());
        r.setItemKey(cut(v.key(), 64));
        r.setTitle(cut(v.title(), 300));
        r.setSubtitle(cut(v.subtitle(), 200));
        r.setLink(cut(v.link(), 300));
        r.setViewedAt(Instant.now());
        recents.save(r);
    }

    @GetMapping("/stars")
    @Transactional(readOnly = true)
    public List<StarItem> starred() {
        return stars.findByUserIdOrderByCreatedAtDesc(current.id()).stream()
                .map(s -> new StarItem(s.getId(), s.getApp(), s.getKind(), s.getTitle(), s.getLink())).toList();
    }

    @GetMapping("/stars/check")
    public Map<String, Boolean> isStarred(@RequestParam String link) {
        return Map.of("starred", stars.existsByUserIdAndLink(current.id(), link));
    }

    /** Toggles; returns the new state. */
    @PostMapping("/stars")
    @Transactional
    public Map<String, Boolean> toggleStar(@RequestBody StarRequest s) {
        Long me = current.id();
        var existing = stars.findByUserIdAndLink(me, s.link());
        if (existing.isPresent()) {
            stars.delete(existing.get());
            return Map.of("starred", false);
        }
        if (s.link() == null || !s.link().startsWith("/")) throw new IllegalArgumentException("Invalid link");
        Star star = new Star();
        star.setUserId(me);
        star.setApp(s.app());
        star.setKind(s.kind());
        star.setTitle(cut(s.title(), 300));
        star.setLink(cut(s.link(), 300));
        stars.save(star);
        return Map.of("starred", true);
    }

    @GetMapping("/search")
    @Transactional(readOnly = true)
    public SearchResult search(@RequestParam String q, @RequestParam(required = false) String app) {
        User me = current.require();
        String query = q.trim();
        if (query.length() < 2) return new SearchResult(query, 0, Map.of(), List.of());
        Set<String> apps = products.appsFor(me);
        List<WorkItem> all = new ArrayList<>();
        for (HubContributor c : contributors) {
            if (!apps.contains(c.app())) continue;
            try {
                all.addAll(c.search(query, me, 40));
            } catch (RuntimeException e) {
                log.warn("Search contributor {} failed: {}", c.app(), e.getMessage());
            }
        }
        users.search(query).stream().filter(User::isActive).limit(10).forEach(u -> {
            UserRef r = UserRef.of(u);
            all.add(WorkItem.of("people", "person", r.initials(), u.getDisplayName(), u.getEmail(),
                    null, null, "/people/" + u.getId(), u.getLastActiveAt()));
        });
        Map<String, Long> counts = new LinkedHashMap<>();
        all.forEach(w -> counts.merge(w.app(), 1L, Long::sum));
        List<WorkItem> filtered = all.stream().filter(w -> app == null || app.isBlank() || app.equals(w.app()))
                .sorted(Comparator.comparing(WorkItem::updatedAt, Comparator.nullsLast(Comparator.reverseOrder()))).toList();
        return new SearchResult(query, all.size(), counts, filtered);
    }

    private static String cut(String s, int max) {
        return s == null ? null : (s.length() <= max ? s : s.substring(0, max));
    }
}
