package com.tanmaysinghx.newxr.goals;

import com.tanmaysinghx.newxr.seed.SampleDataService.SampleDataContributor;
import com.tanmaysinghx.newxr.user.User;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class GoalsSampleData implements SampleDataContributor {

    private final GoalRepository goals;
    private final GoalUpdateRepository updates;
    private final GoalLinkRepository links;

    public GoalsSampleData(GoalRepository goals, GoalUpdateRepository updates, GoalLinkRepository links) {
        this.goals = goals;
        this.updates = updates;
        this.links = links;
    }

    @Override
    public int order() {
        return 30;
    }

    @Override
    public void load(User admin, Map<String, User> p) {
        int year = LocalDate.now().getYear();
        Goal mau = goal(null, "Reach 1M monthly active travellers", "ON_TRACK", 68, p.get("aisha"), "Company", LocalDate.of(year, 12, 31));
        goal(mau, "Ship offline itineraries to everyone", "ON_TRACK", 72, p.get("grace"), "Mobile", LocalDate.of(year, 12, 1));
        goal(mau, "Launch in 3 new markets", "ON_TRACK", 75, p.get("marco"), "Growth", LocalDate.of(year, 12, 15));
        Goal crash = goal(mau, "Cut crash rate below 0.2%", "AT_RISK", 41, p.get("ravi"), "Mobile platform", LocalDate.of(year, 11, 30));
        crash.setMetricName("Crash rate (7-day)");
        crash.setMetricUnit("%");
        crash.setMetricStart(0.44);
        crash.setMetricTarget(0.2);
        crash.setMetricCurrent(0.31);
        crash.setStartDate(LocalDate.now().minusDays(56));
        crash.setDescription("Crashes per session over a rolling 7 days, across iOS and Android.");
        crash.getFollowers().addAll(List.of(admin, p.get("grace"), p.get("aisha")));
        Goal rev = goal(null, "Grow booking revenue 25% QoQ", "OFF_TRACK", 22, p.get("omar"), "Company", LocalDate.of(year, 12, 31));
        goal(rev, "Checkout conversion above 4.5%", "OFF_TRACK", 18, p.get("lena"), "Growth", LocalDate.of(year, 12, 20));
        goal(null, "Every team on Newxr by end of year", "COMPLETED", 100, admin, "IT", LocalDate.of(year, 11, 1));

        double[] values = {0.44, 0.42, 0.40, 0.38, 0.36, 0.33, 0.31};
        for (int i = 0; i < values.length; i++) {
            GoalUpdate u = new GoalUpdate();
            u.setGoal(crash);
            u.setAuthor(p.get("ravi"));
            u.setStatus(i == values.length - 1 ? "AT_RISK" : "ON_TRACK");
            u.setMetricValue(values[i]);
            u.setCheers(i == values.length - 1 ? 6 : 2);
            u.setBody(i == values.length - 1
                    ? "Down to 0.31% from 0.36%. Android 15 map rotation crash (ORB-388) is now 40% of all crashes; fix is in review and lands in 4.12. Expect ~0.24% after rollout — still short of target, so we're adding a second pass on memory warnings."
                    : i == values.length - 2 ? "Room migration removed the top two database crashes. Rate fell from 0.44% to 0.36%." : "Steady progress this week.");
            Instant when = Instant.now().minus((values.length - 1 - i) * 7L, ChronoUnit.DAYS);
            u.setCreatedAt(when);
            u.setUpdatedAt(when);
            updates.save(u);
        }
        link(crash, "track", "ORB-388 Crash when rotating map on Android 15", "/track/issue/ORB-388", "IN_REVIEW");
        link(crash, "track", "ORB-380 Offline experience", "/track/issue/ORB-380", "IN_PROGRESS");
        link(crash, "pages", "Crash triage playbook", "/pages/ENG", "PUBLISHED");
        link(crash, "pulse", "Crash rate alert", "/pulse", "OPEN");
    }

    private Goal goal(Goal parent, String name, String status, int progress, User owner, String team, LocalDate target) {
        Goal g = new Goal();
        g.setParent(parent);
        g.setName(name);
        g.setStatus(status);
        g.setProgress(progress);
        g.setOwner(owner);
        g.setTeam(team);
        g.setTargetDate(target);
        g.getFollowers().add(owner);
        return goals.save(g);
    }

    private void link(Goal g, String app, String title, String link, String status) {
        GoalLink l = new GoalLink();
        l.setGoal(g);
        l.setApp(app);
        l.setTitle(title);
        l.setLink(link);
        l.setStatus(status);
        links.save(l);
    }
}
