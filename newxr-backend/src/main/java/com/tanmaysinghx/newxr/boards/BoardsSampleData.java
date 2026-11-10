package com.tanmaysinghx.newxr.boards;

import com.tanmaysinghx.newxr.seed.SampleDataService.SampleDataContributor;
import com.tanmaysinghx.newxr.user.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class BoardsSampleData implements SampleDataContributor {

    private final BoardRepository boards;
    private final BoardListRepository lists;
    private final CardRepository cards;
    private final CardCommentRepository comments;

    public BoardsSampleData(BoardRepository boards, BoardListRepository lists, CardRepository cards, CardCommentRepository comments) {
        this.boards = boards;
        this.lists = lists;
        this.cards = cards;
        this.comments = comments;
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public void load(User admin, Map<String, User> p) {
        Board b = new Board();
        b.setName("Q4 Launch");
        b.setDescription("Everything for the Q4 Orbit launch.");
        b.setBackground("#6d3ce0,#d9466f");
        b.setOwner(admin);
        b.getMembers().addAll(List.of(admin, p.get("grace"), p.get("ravi"), p.get("aisha"), p.get("marco"), p.get("lena")));
        b = boards.save(b);
        BoardList ideas = list(b, "Ideas", 1), doing = list(b, "In progress", 2), review = list(b, "Review", 3), done = list(b, "Done", 4);

        card(ideas, "Launch video storyboard", "Design", "#ffb86b,#ff6f91", null, false, List.of(p.get("lena")), 0, 0);
        card(ideas, "Partner co-marketing list", "Launch", null, null, false, List.of(), 0, 0);
        card(ideas, "Referral reward experiment", "Social", null, null, false, List.of(p.get("marco")), 0, 0);
        Card landing = card(doing, "Landing page v3", "Design,Copy", "#8b80ff,#5a4bf5", Instant.now().plus(1, ChronoUnit.DAYS), false,
                List.of(p.get("lena"), p.get("grace")), 5, 8);
        landing.setDescription("Refresh the hero for the Q4 launch: new product shots, a 15-second loop of the offline mode, and a simplified sign-up form. Copy owner: Marco.");
        landing.setLinkedIssue("ORB-387");
        String[] checks = {"Final hero illustration", "Offline mode video loop", "Headline and sub-copy", "Sign-up form with 2 fields", "Responsive QA",
                "Localise for DE, FR, JA", "Analytics events", "Legal review"};
        landing.getChecklist().clear();
        for (int i = 0; i < checks.length; i++) landing.getChecklist().add(new CardCheck(checks[i], i < 5));
        card(doing, "Press kit", "Launch", null, null, false, List.of(p.get("aisha")), 2, 6);
        card(doing, "App Store listing copy", "Copy", null, null, false, List.of(p.get("marco")), 0, 0);
        card(review, "Terms of service update", "Legal,Blocked", null, Instant.now().minus(2, ChronoUnit.DAYS), false, List.of(p.get("omar")), 0, 0);
        card(review, "Launch email sequence", "Copy", "#2bd4bd,#2f6fde", null, false, List.of(p.get("priya"), p.get("marco")), 4, 4);
        card(done, "Pricing page", "Design", null, Instant.now().minus(5, ChronoUnit.DAYS), true, List.of(p.get("lena")), 0, 0);
        card(done, "Beta waitlist export", "Launch", null, null, false, List.of(p.get("ravi")), 0, 0);
        card(done, "Social teaser posts", "Social", null, null, false, List.of(p.get("aisha")), 0, 0);

        CardComment c = new CardComment();
        c.setCard(landing);
        c.setAuthor(p.get("grace"));
        c.setBody("The video loop is in the shared drive. 6 MB — we can lazy-load it below the fold.");
        comments.save(c);
    }

    private BoardList list(Board b, String name, int pos) {
        BoardList l = new BoardList();
        l.setBoard(b);
        l.setName(name);
        l.setPosition(pos * 1000);
        return lists.save(l);
    }

    private double pos = 1000;

    private Card card(BoardList l, String title, String labels, String cover, Instant due, boolean dueDone, List<User> members, int done, int total) {
        Card c = new Card();
        c.setList(l);
        c.setTitle(title);
        c.setLabels(labels);
        c.setCover(cover);
        c.setDue(due);
        c.setDueDone(dueDone);
        c.setPosition(pos += 1000);
        c.getMembers().addAll(members);
        for (int i = 0; i < total; i++) c.getChecklist().add(new CardCheck("Step " + (i + 1), i < done));
        return cards.save(c);
    }
}
