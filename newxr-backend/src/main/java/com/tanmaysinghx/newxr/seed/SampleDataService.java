package com.tanmaysinghx.newxr.seed;

import com.tanmaysinghx.newxr.track.ChecklistItem;
import com.tanmaysinghx.newxr.track.Issue;
import com.tanmaysinghx.newxr.track.IssuePriority;
import com.tanmaysinghx.newxr.track.IssueRepository;
import com.tanmaysinghx.newxr.track.IssueStatus;
import com.tanmaysinghx.newxr.track.IssueType;
import com.tanmaysinghx.newxr.track.Project;
import com.tanmaysinghx.newxr.track.ProjectRepository;
import com.tanmaysinghx.newxr.track.Sprint;
import com.tanmaysinghx.newxr.track.SprintRepository;
import com.tanmaysinghx.newxr.track.SprintState;
import com.tanmaysinghx.newxr.track.IssueComment;
import com.tanmaysinghx.newxr.track.IssueCommentRepository;
import com.tanmaysinghx.newxr.user.AuthSource;
import com.tanmaysinghx.newxr.user.Role;
import com.tanmaysinghx.newxr.user.User;
import com.tanmaysinghx.newxr.user.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demo content for evaluation installs, modelled on the design files (Northwind's "Orbit Mobile"
 * team). Demo teammates have no password and cannot sign in; delete them under Administration.
 * Other apps add their own content through {@link SampleDataContributor}.
 */
@Service
public class SampleDataService {

    private static final Logger log = LoggerFactory.getLogger(SampleDataService.class);
    public static final String DEMO_DOMAIN = "@demo.newxr.local";

    /** Implemented by each app that has sample content. */
    public interface SampleDataContributor {
        int order();
        void load(User admin, Map<String, User> people);
    }

    private final UserRepository users;
    private final ProjectRepository projects;
    private final SprintRepository sprints;
    private final IssueRepository issues;
    private final IssueCommentRepository comments;
    private final ObjectProvider<SampleDataContributor> contributors;

    public SampleDataService(UserRepository users, ProjectRepository projects, SprintRepository sprints, IssueRepository issues,
                             IssueCommentRepository comments, ObjectProvider<SampleDataContributor> contributors) {
        this.users = users;
        this.projects = projects;
        this.sprints = sprints;
        this.issues = issues;
        this.comments = comments;
        this.contributors = contributors;
    }

    @Transactional
    public void load(User admin) {
        if (projects.existsByProjectKeyIgnoreCase("ORB")) {
            return;
        }
        Map<String, User> people = people(admin);
        track(admin, people);
        contributors.orderedStream()
                .sorted((a, b) -> Integer.compare(a.order(), b.order()))
                .forEach(c -> {
                    try {
                        c.load(admin, people);
                    } catch (RuntimeException e) {
                        log.warn("Sample data for {} failed: {}", c.getClass().getSimpleName(), e.getMessage());
                    }
                });
        log.info("Loaded sample content");
    }

    private Map<String, User> people(User admin) {
        Map<String, User> m = new LinkedHashMap<>();
        m.put("me", admin);
        String[][] demo = {
                {"grace", "Grace Lee", "Staff engineer", "#0e9f8e"}, {"ravi", "Ravi Kumar", "Android engineer", "#2f6fde"},
                {"aisha", "Aisha Khan", "Engineering manager", "#2f9a57"}, {"lena", "Lena Fischer", "Product designer", "#d9466f"},
                {"marco", "Marco Silva", "Growth engineer", "#e8892b"}, {"omar", "Omar Haddad", "SRE", "#5a4bf5"},
                {"priya", "Priya Nair", "Ops manager, Lisbon", "#2f9a57"}};
        for (String[] d : demo) {
            String email = d[0] + DEMO_DOMAIN;
            User u = users.findByEmailIgnoreCase(email).orElseGet(User::new);
            u.setEmail(email);
            u.setDisplayName(d[1]);
            u.setJobTitle(d[2]);
            u.setAvatarColor(d[3]);
            u.setRole(Role.MEMBER);
            u.setSource(AuthSource.LOCAL);
            u.setInvited(true);
            u.setLastActiveAt(Instant.now().minus((long) (Math.random() * 600), ChronoUnit.MINUTES));
            m.put(d[0], users.save(u));
        }
        return m;
    }

    private void track(User admin, Map<String, User> p) {
        Project orb = new Project();
        orb.setProjectKey("ORB");
        orb.setName("Orbit Mobile");
        orb.setDescription("The Orbit travel app for iOS and Android.");
        orb.setLead(admin);
        orb.setColor("#5a4bf5");
        orb = projects.save(orb);

        LocalDate today = LocalDate.now();
        // Closed sprints 19–23 for velocity, active 24, future 25.
        int[][] vel = {{38, 34}, {42, 40}, {36, 30}, {44, 43}, {40, 38}};
        for (int n = 19; n <= 23; n++) {
            Sprint s = new Sprint();
            s.setProject(orb);
            s.setName("ORB Sprint " + n);
            s.setState(SprintState.CLOSED);
            s.setStartDate(today.minusDays(9 + 14L * (24 - n)));
            s.setEndDate(s.getStartDate().plusDays(14));
            s.setStartedAt(s.getStartDate().atStartOfDay().toInstant(java.time.ZoneOffset.UTC));
            s.setCompletedAt(s.getEndDate().atStartOfDay().toInstant(java.time.ZoneOffset.UTC));
            s.setCommittedPoints(vel[n - 19][0]);
            s.setCompletedPoints(vel[n - 19][1]);
            s.setCommittedIssues(vel[n - 19][0] / 4);
            s.setCompletedIssues(vel[n - 19][1] / 4);
            sprints.save(s);
        }
        Sprint active = new Sprint();
        active.setProject(orb);
        active.setName("ORB Sprint 24");
        active.setGoal("Ship offline itineraries to beta");
        active.setState(SprintState.ACTIVE);
        active.setStartDate(today.minusDays(9));
        active.setEndDate(today.plusDays(3));
        active.setStartedAt(active.getStartDate().atStartOfDay().toInstant(java.time.ZoneOffset.UTC));
        active = sprints.save(active);
        Sprint next = new Sprint();
        next.setProject(orb);
        next.setName("ORB Sprint 25");
        next.setStartDate(today.plusDays(4));
        next.setEndDate(today.plusDays(18));
        next = sprints.save(next);

        Map<String, Issue> epics = new LinkedHashMap<>();
        Object[][] epicDefs = {
                {"Offline experience", -40, 20, IssueStatus.IN_PROGRESS}, {"Biometric sign-in", -70, -20, IssueStatus.DONE},
                {"Social trip sharing", -10, 45, IssueStatus.IN_PROGRESS}, {"Price alerts", 15, 70, IssueStatus.TODO},
                {"Android 15 readiness", -50, -5, IssueStatus.IN_PROGRESS}, {"Checkout redesign", 40, 110, IssueStatus.TODO},
                {"Accessibility AA", 0, 60, IssueStatus.TODO}};
        List<Issue> all = new ArrayList<>();
        int seq = 380;
        double rank = 1000;
        for (Object[] e : epicDefs) {
            Issue epic = base(orb, seq++, IssueType.EPIC, (String) e[0], (IssueStatus) e[3], IssuePriority.HIGH, p.get("aisha"), admin, null, rank += 1000);
            epic.setStartDate(today.plusDays((int) e[1]));
            epic.setDueDate(today.plusDays((int) e[2]));
            epics.put((String) e[0], issues.save(epic));
        }
        orb.setIssueSeq(seq);

        // {summary, type, status, priority, assignee, points, labels, sprint(0 active / 1 next / -1 backlog), epic}
        Object[][] defs = {
                {"Offline mode for saved itineraries", IssueType.STORY, IssueStatus.IN_PROGRESS, IssuePriority.HIGH, "grace", 5, "mobile,sync", 0, "Offline experience"},
                {"Crash when rotating map on Android 15", IssueType.BUG, IssueStatus.TODO, IssuePriority.HIGHEST, "ravi", 3, "android", 0, "Android 15 readiness"},
                {"Migrate push service to v2 SDK", IssueType.TASK, IssueStatus.IN_REVIEW, IssuePriority.MEDIUM, "me", 3, "infra", 0, "Android 15 readiness"},
                {"Biometric sign-in on iOS", IssueType.STORY, IssueStatus.DONE, IssuePriority.HIGH, "aisha", 8, "ios,auth", 0, "Biometric sign-in"},
                {"Dark mode contrast on booking summary", IssueType.BUG, IssueStatus.TODO, IssuePriority.LOW, "lena", 1, "design", 0, "Accessibility AA"},
                {"Share trip with friends via link", IssueType.STORY, IssueStatus.IN_PROGRESS, IssuePriority.MEDIUM, "marco", 5, "growth", 0, "Social trip sharing"},
                {"Add analytics events to checkout", IssueType.TASK, IssueStatus.IN_REVIEW, IssuePriority.MEDIUM, "omar", 2, "analytics", 0, "Checkout redesign"},
                {"Duplicate notifications after reinstall", IssueType.BUG, IssueStatus.DONE, IssuePriority.HIGH, "priya", 3, "push", 0, null},
                {"Price alerts for saved routes", IssueType.STORY, IssueStatus.TODO, IssuePriority.MEDIUM, "me", 5, "growth", 0, "Price alerts"},
                {"Update App Store screenshots", IssueType.TASK, IssueStatus.DONE, IssuePriority.LOWEST, "lena", 1, "release", 0, null},
                {"Room schema for itinerary cache", IssueType.SUBTASK, IssueStatus.DONE, IssuePriority.MEDIUM, "grace", null, "", 0, null},
                {"Background sync worker", IssueType.SUBTASK, IssueStatus.DONE, IssuePriority.MEDIUM, "ravi", null, "", 0, null},
                {"Conflict resolution UI", IssueType.SUBTASK, IssueStatus.IN_PROGRESS, IssuePriority.MEDIUM, "me", null, "", 0, null},
                {"Storage eviction policy", IssueType.SUBTASK, IssueStatus.TODO, IssuePriority.LOW, null, null, "", 0, null},
                {"Trip timeline widget for home screen", IssueType.STORY, IssueStatus.TODO, IssuePriority.MEDIUM, null, 5, "ios", 1, "Social trip sharing"},
                {"Currency rounds incorrectly for JPY", IssueType.BUG, IssueStatus.TODO, IssuePriority.HIGH, "omar", 3, "payments", 1, "Checkout redesign"},
                {"Remove legacy GraphQL client", IssueType.TASK, IssueStatus.TODO, IssuePriority.LOW, null, 3, "infra", -1, null},
                {"Accessibility pass on onboarding", IssueType.STORY, IssueStatus.TODO, IssuePriority.MEDIUM, "lena", 5, "a11y", -1, "Accessibility AA"},
                {"Hotel check-in reminders", IssueType.STORY, IssueStatus.TODO, IssuePriority.LOW, null, 3, "growth", -1, null},
        };
        Issue offline = null;
        Instant sprintStart = active.getStartedAt();
        for (int k = 0; k < defs.length; k++) {
            Object[] d = defs[k];
            Issue i = base(orb, seq++, (IssueType) d[1], (String) d[0], (IssueStatus) d[2], (IssuePriority) d[3],
                    d[4] == null ? null : p.get((String) d[4]), k % 2 == 0 ? admin : p.get("aisha"), (Integer) d[5], rank += 1000);
            i.setLabels(((String) d[6]).isEmpty() ? null : (String) d[6]);
            int sp = (int) d[7];
            i.setSprint(sp == 0 ? active : sp == 1 ? next : null);
            if (sp == 0) {
                // A couple of issues joined after the sprint started (scope change).
                i.setAddedToSprintAt(k == 8 || k == 4 ? sprintStart.plus(3, ChronoUnit.DAYS) : sprintStart);
            }
            if (d[8] != null) i.setParent(epics.get((String) d[8]));
            if (i.getStatus() != IssueStatus.TODO) i.setStartedAt(sprintStart.plus(k % 4, ChronoUnit.DAYS));
            if (i.getStatus() == IssueStatus.DONE) i.setResolvedAt(sprintStart.plus(2 + k % 6, ChronoUnit.DAYS));
            if (k == 0) {
                i.setDescription("""
                        As a traveller with a poor connection, I want my saved itineraries, tickets and maps to open without the network, \
                        so I can get through airports and stations without stress.

                        Design: [Offline sync — technical design](/pages) · Rollout behind the `offline_v2` flag.""");
                i.getChecklist().add(new ChecklistItem("Itineraries saved in the last 30 days open in airplane mode", true));
                i.getChecklist().add(new ChecklistItem("Boarding passes render from cache within 500 ms", true));
                i.getChecklist().add(new ChecklistItem("Edits made offline sync when the device reconnects; conflicts prompt the user", false));
                i.getChecklist().add(new ChecklistItem("Storage is capped at 200 MB with LRU eviction", false));
                i.setFixVersion("4.12.0");
                i.setDueDate(today.plusDays(4));
            }
            i = issues.save(i);
            if (k == 0) offline = i;
            if (k >= 10 && k <= 13 && offline != null) {
                i.setParent(offline);
            }
            all.add(i);
        }
        orb.setIssueSeq(seq);
        projects.save(orb);
        if (offline != null) {
            comment(offline, p.get("ravi"), "Sync worker is merged behind the `offline_v2` flag. QA can test on build 4.12.0-rc2 — use the debug menu to simulate a dropped connection.");
            comment(offline, p.get("grace"), "@" + p.get("lena").getDisplayName() + " the conflict prompt needs copy for the case where a booking was cancelled server-side while offline. Can you add it to the flow?");
        }
        // Velocity bar for the active sprint: committed at start (scope added later is separate).
        final Long activeId = active.getId();
        int committed = all.stream().filter(i -> i.getSprint() != null && i.getSprint().getId().equals(activeId))
                .filter(i -> i.getAddedToSprintAt() != null && !i.getAddedToSprintAt().isAfter(sprintStart)).mapToInt(Issue::points).sum();
        active.setCommittedPoints(committed);
        active.setCommittedIssues((int) all.stream().filter(i -> i.getSprint() != null && i.getSprint().getState() == SprintState.ACTIVE).count());
        sprints.save(active);
    }

    private static Issue base(Project p, int n, IssueType type, String summary, IssueStatus status, IssuePriority prio,
                              User assignee, User reporter, Integer points, double rank) {
        Issue i = new Issue();
        i.setProject(p);
        i.setNumber(n);
        i.setIssueKey(p.getProjectKey() + "-" + n);
        i.setType(type);
        i.setSummary(summary);
        i.setStatus(status);
        i.setPriority(prio);
        i.setAssignee(assignee);
        i.setReporter(reporter);
        i.setStoryPoints(points);
        i.setRank(rank);
        return i;
    }

    private void comment(Issue i, User author, String body) {
        IssueComment c = new IssueComment();
        c.setIssue(i);
        c.setAuthor(author);
        c.setBody(body);
        comments.save(c);
    }
}
