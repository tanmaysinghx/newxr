package com.tanmaysinghx.newxr.pages;

import com.tanmaysinghx.newxr.seed.SampleDataService.SampleDataContributor;
import com.tanmaysinghx.newxr.user.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PagesSampleData implements SampleDataContributor {

    private final SpaceRepository spaces;
    private final PageRepository pages;
    private final PageVersionRepository versions;
    private final PageCommentRepository comments;
    private final PageReactionRepository reactions;

    public PagesSampleData(SpaceRepository spaces, PageRepository pages, PageVersionRepository versions,
                           PageCommentRepository comments, PageReactionRepository reactions) {
        this.spaces = spaces;
        this.pages = pages;
        this.versions = versions;
        this.comments = comments;
        this.reactions = reactions;
    }

    @Override
    public int order() {
        return 10;
    }

    @Override
    public void load(User admin, Map<String, User> p) {
        Space eng = space("ENG", "Engineering", "How we build Orbit: architecture, runbooks, decisions and team rituals.", "#0e9f8e", admin, "ORB");
        space("PROD", "Product", "Specs, research and roadmaps", "#5a4bf5", p.get("aisha"), null);
        space("DES", "Design", "Design system and critiques", "#d9466f", p.get("lena"), null);
        space("IT", "IT Help", "Policies and how-to articles", "#2f6fde", p.get("omar"), null);
        space("MKT", "Marketing", "Campaigns, brand and launches", "#e8892b", p.get("marco"), null);
        space("PPL", "People", "Handbook, benefits and hiring", "#2f9a57", p.get("priya"), null);

        Page home = page(eng, null, "Engineering home", """
                # Engineering at Northwind

                We build **Orbit**, the travel app used by 1.2M monthly travellers.

                > [!info] New here? Start with [Onboarding](/pages/ENG) and the on-call handbook.

                ## Teams
                | Team | Lead | Focus |
                | --- | --- | --- |
                | Mobile | Grace Lee | iOS and Android apps |
                | Platform | Omar Haddad | APIs, sync, infrastructure |
                | Growth | Marco Silva | Sharing, alerts, analytics |
                """, admin, 30, false);
        Page arch = page(eng, null, "Architecture", "Service map, data flows and architecture decision records (ADRs).", p.get("grace"), 20, true);
        page(eng, arch, "Service map", "## Services\n- **itinerary-api** — trips, bookings, documents\n- **sync-gateway** — device sync, conflict resolution\n- **notify** — push and email\n- **pricing** — fares and alerts", p.get("omar"), 12, false);
        Page offline = page(eng, arch, "Offline sync — technical design", """
                > [!info] This design is approved for 4.12. Changes after 10 Nov need sign-off from the mobile platform guild.

                ## Context
                Travellers often open their itineraries in places with no connectivity — airports, trains, foreign networks. \
                Today the app needs a live request to render a trip, which drives 18% of our 1-star reviews. This page describes \
                a local-first sync engine for itineraries, tickets and maps.

                ## Design
                - Room database is the source of truth on device; the UI only reads from it.
                - Writes go to a durable outbox and are replayed by a WorkManager job with exponential back-off.
                - Server responses carry a version vector; conflicts surface a prompt instead of silently overwriting.

                ```kotlin
                @Entity(tableName = "itinerary")
                data class ItineraryEntity(
                    @PrimaryKey val id: String,
                    val payload: String,
                    val version: Long,
                    val syncedAt: Instant?
                )
                ```

                ## Rollout
                | Phase | Audience | Date | Status |
                | --- | --- | --- | --- |
                | Internal dogfood | Employees | 10 Nov | Done |
                | Beta | 5% Android, 5% iOS | 17 Nov | In progress |
                | GA | Everyone | 1 Dec | To do |

                Tracked in ORB-387.
                """, p.get("grace"), 0, false);
        page(eng, arch, "API guidelines", "## Principles\n1. Resources are nouns, plural.\n2. Every list is paginated.\n3. Errors are `{status, message}`.", p.get("omar"), 40, false);
        Page runbooks = page(eng, null, "Runbooks", "Step-by-step guides for operating Orbit in production.", p.get("omar"), 25, false);
        page(eng, runbooks, "On-call handbook", "## Escalation\n- P1: page the primary on-call in Pulse, open an incident.\n- P2: acknowledge within 15 minutes.\n\n## Handover\nEvery Monday 10:00.", p.get("omar"), 5, true);
        page(eng, runbooks, "Incident policy v3", "## Severity\n| Level | Definition |\n| --- | --- |\n| SEV1 | Customers cannot book |\n| SEV2 | Degraded booking or sync |", p.get("omar"), 4, false);
        page(eng, null, "Onboarding", "## Your first two weeks\n- [x] Laptop and accounts\n- [ ] Ship a small fix\n- [ ] Shadow an on-call shift", p.get("aisha"), 15, true);
        page(eng, null, "Meeting notes", "Weekly engineering sync notes.", admin, 3, false);
        Page retros = page(eng, null, "Retrospectives", "Sprint retrospectives for the Orbit team.", admin, 6, false);
        Page retro = page(eng, retros, "Sprint 23 retrospective", "## What went well\n- Biometric sign-in shipped on time\n\n## What could be better\n- Scope grew mid-sprint\n\n## Action items\n- [x] Add mid-sprint scope check to stand-up", admin, 14, false);

        Page blog = page(eng, null, "Q4 platform priorities", "Our three priorities this quarter: offline sync, Android 15 readiness and a faster checkout.", p.get("aisha"), 3, false);
        blog.setKind("BLOG");

        comment(offline, p.get("ravi"), "Should the outbox also cover seat-selection changes? Those are the most common offline edits in the analytics.");
        comment(offline, p.get("grace"), "Good call — adding a section on seat changes. They'll need the same conflict prompt.");
        react(offline, admin, "👍");
        react(offline, p.get("ravi"), "👍");
        react(offline, p.get("lena"), "🎉");
        react(retro, p.get("aisha"), "👀");
    }

    private Space space(String key, String name, String desc, String color, User owner, String project) {
        Space s = new Space();
        s.setSpaceKey(key);
        s.setName(name);
        s.setDescription(desc);
        s.setColor(color);
        s.setOwner(owner);
        s.setLinkedProject(project);
        return spaces.save(s);
    }

    private double pos = 1000;

    private Page page(Space s, Page parent, String title, String body, User author, int daysAgo, boolean pinned) {
        Page pg = new Page();
        pg.setSpace(s);
        pg.setParent(parent);
        pg.setTitle(title);
        pg.setBody(body);
        pg.setStatus("PUBLISHED");
        pg.setVersion(1);
        pg.setAuthor(author);
        pg.setLastEditor(author);
        pg.setPinned(pinned);
        pg.setPosition(pos += 1000);
        Instant when = Instant.now().minus(daysAgo, ChronoUnit.DAYS).minus((long) (Math.random() * 300), ChronoUnit.MINUTES);
        pg.setPublishedAt(when);
        pg.setCreatedAt(when);
        pg.setUpdatedAt(when);
        pg = pages.save(pg);
        PageVersion v = new PageVersion();
        v.setPage(pg);
        v.setVersion(1);
        v.setTitle(title);
        v.setBody(body);
        v.setAuthor(author);
        versions.save(v);
        return pg;
    }

    private void comment(Page page, User author, String body) {
        PageComment c = new PageComment();
        c.setPage(page);
        c.setAuthor(author);
        c.setBody(body);
        comments.save(c);
    }

    private void react(Page page, User u, String emoji) {
        PageReaction r = new PageReaction();
        r.setPage(page);
        r.setUserId(u.getId());
        r.setEmoji(emoji);
        reactions.save(r);
    }
}
