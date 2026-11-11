package com.tanmaysinghx.newxr.code;

import com.tanmaysinghx.newxr.seed.SampleDataService.SampleDataContributor;
import com.tanmaysinghx.newxr.user.User;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** northwind / orbit-mobile from the design's Code frame: source, PR #284 for ORB-387 (the Track sample's offline-mode story), and CI runs #1868–#1872. */
@Component
public class CodeSampleData implements SampleDataContributor {

    private final CodeService code;
    private final CodeRepoRepository repos;
    private final BranchRepository branches;
    private final PullRequestRepository pulls;
    private final PullReviewerRepository reviewers;
    private final PullCommentRepository comments;
    private final PipelineRepository pipelines;
    private final PipelineStageRepository stages;
    private final DeploymentRepository deployments;
    private final Vcs vcs;

    public CodeSampleData(CodeService code, CodeRepoRepository repos, BranchRepository branches, PullRequestRepository pulls,
                          PullReviewerRepository reviewers, PullCommentRepository comments, PipelineRepository pipelines,
                          PipelineStageRepository stages, DeploymentRepository deployments, Vcs vcs) {
        this.code = code;
        this.repos = repos;
        this.branches = branches;
        this.pulls = pulls;
        this.reviewers = reviewers;
        this.comments = comments;
        this.pipelines = pipelines;
        this.stages = stages;
        this.deployments = deployments;
        this.vcs = vcs;
    }

    @Override
    public int order() {
        return 60;
    }

    @Override
    public void load(User admin, Map<String, User> p) {
        if (repos.findBySlugIgnoreCase("orbit-mobile").isPresent()) return;
        User grace = p.get("grace"), ravi = p.get("ravi"), marco = p.get("marco"), aisha = p.get("aisha");
        Instant now = Instant.now();

        CodeRepo repo = new CodeRepo();
        repo.setNamespace(code.namespace());
        repo.setName("orbit-mobile");
        repo.setSlug("orbit-mobile");
        repo.setDescription("Orbit travel app for Android");
        repo.setLanguage("Kotlin");
        repo.getWatchers().addAll(List.of(admin, grace, ravi));
        repo.setPrSeq(283);
        repo = repos.save(repo);
        Branch main = new Branch();
        main.setRepo(repo);
        main.setName("main");
        main.setCreatedBy(admin);
        main.setCreatedAt(now.minus(Duration.ofDays(90)));
        branches.save(main);

        Map<String, String> initial = new LinkedHashMap<>();
        initial.put("README.md", README);
        initial.put("settings.gradle.kts", "rootProject.name = \"orbit-mobile\"\ninclude(\":app\")\n");
        initial.put("build.gradle.kts", "plugins {\n    id(\"com.android.application\") version \"8.7.0\" apply false\n    kotlin(\"android\") version \"2.0.21\" apply false\n}\n");
        initial.put("app/build.gradle.kts", APP_GRADLE.replace("COMPILE", "34").replace("ROOM", "2.6.1"));
        initial.put("app/proguard-rules.pro", "-keep class travel.orbit.data.** { *; }\n");
        initial.put("app/src/main/AndroidManifest.xml", "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">\n    <application android:label=\"Orbit\" />\n</manifest>\n");
        initial.put("app/src/sync/OfflineQueue.kt", QUEUE_BEFORE);
        initial.put("app/src/sync/SyncWorker.kt", WORKER);
        initial.put("app/src/data/ItineraryDao.kt", DAO_BEFORE);
        initial.put("app/src/ui/MapScreen.kt", "package travel.orbit.ui\n\n@Composable\nfun MapScreen(state: MapState) {\n    MapView(state.camera, rotateGestures = true)\n}\n");
        initial.put("app/src/test/OfflineQueueTest.kt", "package travel.orbit.sync\n\nclass OfflineQueueTest {\n    @Test fun flushSendsPendingOps() { }\n}\n");
        vcs.commit(repo, "main", admin, "Initial import", initial, now.minus(Duration.ofDays(60)));
        vcs.commit(repo, "main", ravi, "fix(map): handle rotation on API 35 (ORB-388)",
                Map.of("app/src/ui/MapScreen.kt", "package travel.orbit.ui\n\n@Composable\nfun MapScreen(state: MapState) {\n    // API 35 reports rotation before layout; wait for the first frame.\n    val ready by rememberFirstFrame()\n    MapView(state.camera, rotateGestures = ready)\n}\n"),
                now.minus(Duration.ofHours(5)));
        vcs.commit(repo, "main", grace, "test(sync): conflict scenarios",
                Map.of("app/src/test/OfflineQueueTest.kt", "package travel.orbit.sync\n\nclass OfflineQueueTest {\n    @Test fun flushSendsPendingOps() { }\n    @Test fun conflictKeepsServerVersion() { }\n}\n"),
                now.minus(Duration.ofDays(1)));
        vcs.commit(repo, "main", aisha, "docs: offline mode setup",
                Map.of("README.md", README + "\n## Offline mode\n\nItineraries are cached with Room and synced by `SyncWorker` when the network returns.\n"),
                now.minus(Duration.ofDays(2)));
        vcs.commit(repo, "main", marco, "chore: bump compileSdk to 35",
                Map.of("app/build.gradle.kts", APP_GRADLE.replace("COMPILE", "35").replace("ROOM", "2.6.1")), now.minus(Duration.ofDays(3)));
        vcs.commit(repo, "main", ravi, "chore: Room 2.7", Map.of("app/build.gradle.kts", APP_GRADLE.replace("COMPILE", "35").replace("ROOM", "2.7.0")),
                now.minus(Duration.ofHours(2)));

        // PR #284: offline itinerary cache for ORB-387.
        String feature = "feature/ORB-387-offline-cache";
        vcs.createBranch(repo, feature, "main", grace, now.minus(Duration.ofDays(4)));
        vcs.commit(repo, feature, grace, "feat(sync): offline queue flushes with retry (ORB-387)", Map.of("app/src/sync/OfflineQueue.kt", QUEUE_MID),
                now.minus(Duration.ofDays(3)));
        vcs.commit(repo, feature, grace, "feat(sync): cache itineraries in Room (ORB-387)", Map.of("app/src/data/ItineraryDao.kt", DAO_AFTER),
                now.minus(Duration.ofDays(2)));
        vcs.commit(repo, feature, grace, "feat(sync): reschedule worker when offline",
                Map.of("app/src/sync/SyncWorker.kt", WORKER.replace("return Result.success()", "if (!network.isOnline()) return Result.retry()\n        return Result.success()")),
                now.minus(Duration.ofHours(6)));
        Commit head = vcs.commit(repo, feature, grace, "feat(sync): retry outbox with jitter (ORB-414)", Map.of("app/src/sync/OfflineQueue.kt", QUEUE_AFTER),
                now.minus(Duration.ofMinutes(10)));

        CodeRepo locked = code.lock(repo);
        locked.setPrSeq(284);
        PullRequest pr = new PullRequest();
        pr.setRepo(locked);
        pr.setNumber(284);
        pr.setTitle("feat: offline itinerary cache");
        pr.setDescription("Caches itineraries in Room and queues writes while offline (ORB-387).\n\n- `OfflineQueue` retries with exponential backoff and jitter\n- `SyncWorker` reschedules itself when there's no network\n- `ItineraryDao` gains `upsertAll` and `pending()`");
        pr.setSourceBranch(feature);
        pr.setTargetBranch("main");
        pr.setAuthor(grace);
        pr.setCreatedAt(now.minus(Duration.ofDays(2)));
        pr.setUpdatedAt(now.minus(Duration.ofMinutes(10)));
        pr = pulls.save(pr);
        reviewer(pr, ravi, "APPROVED");
        reviewer(pr, admin, "NEEDS_WORK");
        reviewer(pr, aisha, "PENDING");
        comment(pr, ravi, "Should we cap total retry time? With 5 tries and back-off this can hold the worker for ~90 s on a bad network.",
                "app/src/sync/OfflineQueue.kt", 7, now.minus(Duration.ofHours(1)));
        comment(pr, grace, "Good point — capped at 30 s in the next commit, the job reschedules itself after that.",
                "app/src/sync/OfflineQueue.kt", 7, now.minus(Duration.ofMinutes(40)));

        // Branch for the ORB-388 fix (its change is already on main).
        String fix = "fix/ORB-388-map-rotation";
        vcs.createBranch(repo, fix, "main", ravi, now.minus(Duration.ofDays(1)));

        // Pipelines #1868–#1872.
        repo.setPipelineSeq(1872);
        pipeline(repo, 1868, "main", "Merge PR #279 biometric sign-in", "PASSED", aisha, now.minus(Duration.ofHours(30)), 15 * 60, List.of());
        pipeline(repo, 1869, "feature/ORB-409-share-trip", "feat: deep link for shared trips", "FAILED", marco, now.minus(Duration.ofHours(26)), 6 * 60,
                List.of(new String[]{"Build", "PASSED", "150", ""}, new String[]{"Unit tests", "FAILED", "201",
                        "> Task :app:testDebugUnitTest\nShareLinkTest > parsesTripId FAILED\n    expected:<trip_42> but was:<null>\n"}));
        pipeline(repo, 1870, fix, "fix(map): handle rotation on API 35", "PASSED", ravi, now.minus(Duration.ofHours(5)), 13 * 60, List.of());
        pipeline(repo, 1871, "main", "chore: bump compileSdk to 35", "PASSED", ravi, now.minus(Duration.ofHours(2)), 14 * 60, List.of());
        pipeline(repo, 1872, feature, "feat(sync): retry outbox with jitter", "RUNNING", grace, now.minus(Duration.ofMinutes(10)), null,
                List.of(new String[]{"Build", "PASSED", "161", "BUILD SUCCESSFUL in 2m 41s\n"},
                        new String[]{"Unit tests", "PASSED", "248", "1,204 tests completed, 0 failed\n"},
                        new String[]{"Instrumented tests", "RUNNING", "192", "> Task :app:connectedDebugAndroidTest\nStarting 214 tests on Pixel_8_API_35(AVD)\n"
                                + "OfflineQueueTest > flush_retriesWithJitter PASSED\nOfflineQueueTest > flush_stopsAfterCap PASSED\n"
                                + "ConflictPromptTest > cancelledBooking_showsDialog …\n"},
                        new String[]{"Security scan", "QUEUED", null, null}, new String[]{"Deploy to beta", "QUEUED", null, null}));
        pipelines.flush();
        pipelines.findByRepoAndNumber(repo, 1872).ifPresent(pl -> pl.setCommitSha(head.getSha()));

        deployment(repo, "production", "4.11.2", "main", ravi.getDisplayName(), now.minus(Duration.ofDays(6)));
        deployment(repo, "beta", "4.12.0-rc2", "main", ravi.getDisplayName(), now.minus(Duration.ofHours(2)));
    }

    private void reviewer(PullRequest pr, User u, String state) {
        PullReviewer r = new PullReviewer();
        r.setPull(pr);
        r.setUser(u);
        r.setState(state);
        reviewers.save(r);
    }

    private void comment(PullRequest pr, User u, String body, String path, Integer line, Instant at) {
        PullComment c = new PullComment();
        c.setPull(pr);
        c.setAuthor(u);
        c.setBody(body);
        c.setPath(path);
        c.setLine(line);
        c.setCreatedAt(at);
        c.setUpdatedAt(at);
        comments.save(c);
    }

    private void pipeline(CodeRepo repo, int n, String branch, String message, String status, User by, Instant start, Integer seconds, List<String[]> st) {
        Pipeline p = new Pipeline();
        p.setRepo(repo);
        p.setNumber(n);
        p.setBranch(branch);
        p.setCommitMessage(message);
        p.setStatus(status);
        p.setTriggeredBy(by);
        p.setTriggeredByName(by.getDisplayName());
        p.setStartedAt(start);
        p.setCreatedAt(start);
        p.setUpdatedAt(start);
        if (seconds != null) p.setFinishedAt(start.plusSeconds(seconds));
        p = pipelines.save(p);
        List<String[]> rows = st.isEmpty()
                ? List.of(new String[]{"Build", "PASSED", "160", ""}, new String[]{"Unit tests", "PASSED", "250", ""},
                          new String[]{"Instrumented tests", "PASSED", "300", ""}, new String[]{"Security scan", "PASSED", "60", ""})
                : st;
        for (int i = 0; i < rows.size(); i++) {
            PipelineStage s = new PipelineStage();
            s.setPipeline(p);
            s.setPosition(i);
            s.setName(rows.get(i)[0]);
            s.setStatus(rows.get(i)[1]);
            s.setDurationSeconds(rows.get(i)[2] == null ? null : Integer.parseInt(rows.get(i)[2]));
            s.setLog(rows.get(i)[3]);
            stages.save(s);
        }
    }

    private void deployment(CodeRepo repo, String env, String version, String branch, String by, Instant at) {
        Deployment d = new Deployment();
        d.setRepo(repo);
        d.setEnvironment(env);
        d.setVersion(version);
        d.setBranch(branch);
        d.setDeployedBy(by);
        d.setCreatedAt(at);
        d.setUpdatedAt(at);
        deployments.save(d);
    }

    private static final String README = """
            # Orbit Mobile — Android

            The Orbit travel app for Android. Kotlin, Jetpack Compose, Room, WorkManager.

            ## Getting started

            ```
            ./gradlew :app:installDebug
            ./gradlew test connectedCheck
            ```
            """;

    private static final String APP_GRADLE = """
            android {
                namespace = "travel.orbit"
                compileSdk = COMPILE
            }

            dependencies {
                implementation("androidx.room:room-runtime:ROOM")
                implementation("androidx.work:work-runtime-ktx:2.9.1")
            }
            """;

    private static final String QUEUE_BEFORE = """
            package travel.orbit.sync

            class OfflineQueue(private val dao: OutboxDao) {

                suspend fun flush() = dao.all().forEach { send(it) }

                private suspend fun send(op: SyncOp): Boolean = api.post(op)
            }
            """;

    private static final String QUEUE_MID = """
            package travel.orbit.sync

            class OfflineQueue(private val dao: OutboxDao) {

                suspend fun flush() {
                    for (op in dao.pending()) {
                        if (send(op)) dao.markSent(op.id)
                    }
                }

                private suspend fun send(op: SyncOp): Boolean = api.post(op)
            }
            """;

    private static final String QUEUE_AFTER = """
            package travel.orbit.sync

            class OfflineQueue(private val dao: OutboxDao) {

                suspend fun flush() {
                    for (op in dao.pending()) {
                        val ok = retry(times = 5, jitter = 0.3, maxTotal = 30.seconds) { send(op) }
                        if (ok) dao.markSent(op.id) else break
                    }
                }

                private suspend fun send(op: SyncOp): Boolean = api.post(op)
            }
            """;

    private static final String WORKER = """
            package travel.orbit.sync

            class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

                override suspend fun doWork(): Result {
                    queue.flush()
                    return Result.success()
                }
            }
            """;

    private static final String DAO_BEFORE = """
            package travel.orbit.data

            @Dao
            interface ItineraryDao {
                @Query("SELECT * FROM itinerary WHERE tripId = :tripId")
                suspend fun forTrip(tripId: String): List<Itinerary>
            }
            """;

    private static final String DAO_AFTER = """
            package travel.orbit.data

            @Dao
            interface ItineraryDao {
                @Query("SELECT * FROM itinerary WHERE tripId = :tripId")
                suspend fun forTrip(tripId: String): List<Itinerary>

                @Upsert
                suspend fun upsertAll(items: List<Itinerary>)

                @Query("SELECT * FROM itinerary WHERE synced = 0")
                suspend fun pending(): List<Itinerary>
            }
            """;
}
