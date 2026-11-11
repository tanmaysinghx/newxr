package com.tanmaysinghx.newxr.code;

import com.tanmaysinghx.newxr.common.ApiException;
import com.tanmaysinghx.newxr.events.DomainEvent;
import com.tanmaysinghx.newxr.events.EventBus;
import com.tanmaysinghx.newxr.security.CurrentUser;
import com.tanmaysinghx.newxr.track.IssueRepository;
import com.tanmaysinghx.newxr.user.User;
import com.tanmaysinghx.newxr.user.UserRef;
import com.tanmaysinghx.newxr.user.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Pull requests: diff against the target, reviews, line comments, checks from CI, merge with conflict detection. */
@RestController
@RequestMapping("/api/code/repos/{slug}/pulls")
@Transactional
public class PullService {

    private static final Pattern ISSUE_KEY = Pattern.compile("\\b([A-Z][A-Z0-9]{0,9}-\\d{1,7})\\b");

    public record PullRow(int number, String title, String sourceBranch, String targetBranch, UserRef author, String status,
                          List<ReviewerView> reviewers, int comments, Instant createdAt, Instant updatedAt, String checks) {}

    public record ReviewerView(UserRef user, String state) {}

    public record CommentView(Long id, UserRef author, String body, String path, Integer line, Instant at) {}

    public record FileDiff(String path, int additions, int deletions, boolean added, boolean conflict, boolean tooLarge, List<LineDiff.Hunk> hunks) {}

    public record StageView(String name, String status, Integer durationSeconds) {}

    public record Checks(int pipeline, String status, List<StageView> stages) {}

    public record IssueLink(String key, String summary, String status) {}

    public record PullView(int number, String repoSlug, String repoName, String title, String description, String sourceBranch, String targetBranch,
                           UserRef author, String status, Instant createdAt, Instant updatedAt, UserRef closedBy, Instant closedAt, String mergeSha,
                           List<ReviewerView> reviewers, List<CommentView> comments, List<CodeService.CommitView> commits, List<FileDiff> files,
                           Checks checks, List<IssueLink> issues, boolean mergeable, List<String> conflicts, int additions, int deletions) {}

    public record CreatePull(String title, String description, String sourceBranch, String targetBranch, List<Long> reviewerIds) {}

    public record EditPull(String title, String description, List<Long> reviewerIds) {}

    public record ReviewRequest(String state) {}

    public record CommentRequest(String body, String path, Integer line) {}

    public record PathRequest(String path) {}

    private final CodeService code;
    private final PullRequestRepository pulls;
    private final PullReviewerRepository reviewers;
    private final PullCommentRepository comments;
    private final PullFileRepository pullFiles;
    private final CommitRepository commits;
    private final PipelineRepository pipelines;
    private final PipelineStageRepository stages;
    private final IssueRepository issues;
    private final UserRepository users;
    private final Vcs vcs;
    private final CurrentUser current;
    private final EventBus events;

    public PullService(CodeService code, PullRequestRepository pulls, PullReviewerRepository reviewers, PullCommentRepository comments,
                       PullFileRepository pullFiles, CommitRepository commits, PipelineRepository pipelines, PipelineStageRepository stages,
                       IssueRepository issues, UserRepository users, Vcs vcs, CurrentUser current, EventBus events) {
        this.code = code;
        this.pulls = pulls;
        this.reviewers = reviewers;
        this.comments = comments;
        this.pullFiles = pullFiles;
        this.commits = commits;
        this.pipelines = pipelines;
        this.stages = stages;
        this.issues = issues;
        this.users = users;
        this.vcs = vcs;
        this.current = current;
        this.events = events;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<PullRow> list(@PathVariable String slug, @RequestParam(defaultValue = "OPEN") String state) {
        CodeRepo repo = code.repo0(slug);
        return pulls.findByRepoOrderByNumberDesc(repo).stream().filter(p -> "ALL".equals(state) || p.getStatus().equals(state))
                .map(p -> new PullRow(p.getNumber(), p.getTitle(), p.getSourceBranch(), p.getTargetBranch(), UserRef.of(p.getAuthor()), p.getStatus(),
                        reviewerViews(p), comments.findByPullOrderByCreatedAtAsc(p).size(), p.getCreatedAt(), p.getUpdatedAt(),
                        pipelines.findFirstByRepoAndBranchOrderByNumberDesc(repo, p.getSourceBranch()).map(Pipeline::getStatus).orElse(null)))
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PullView create(@PathVariable String slug, @RequestBody CreatePull r) {
        User me = current.require();
        CodeRepo repo = code.repo0(slug);
        String target = r.targetBranch() == null || r.targetBranch().isBlank() ? repo.getDefaultBranch() : r.targetBranch();
        if (r.sourceBranch() == null || r.sourceBranch().equals(target)) throw ApiException.badRequest("Pick a branch to merge from that isn't the target.");
        vcs.branch(repo, r.sourceBranch());
        vcs.branch(repo, target);
        if (!pulls.findByRepoAndSourceBranchAndStatus(repo, r.sourceBranch(), "OPEN").isEmpty()) {
            throw ApiException.conflict(r.sourceBranch() + " already has an open pull request.");
        }
        if (vcs.changes(repo, r.sourceBranch(), target).isEmpty()) throw ApiException.badRequest(r.sourceBranch() + " has no changes compared with " + target + ".");
        CodeRepo locked = code.lock(repo);
        locked.setPrSeq(locked.getPrSeq() + 1);
        PullRequest p = new PullRequest();
        p.setRepo(locked);
        p.setNumber(locked.getPrSeq());
        p.setTitle(r.title() == null || r.title().isBlank() ? Vcs.firstLine(r.sourceBranch()) : r.title().trim());
        p.setDescription(r.description());
        p.setSourceBranch(r.sourceBranch());
        p.setTargetBranch(target);
        p.setAuthor(me);
        p = pulls.save(p);
        setReviewers(p, r.reviewerIds(), me);
        return view(p);
    }

    @GetMapping("/{n}")
    @Transactional(readOnly = true)
    public PullView get(@PathVariable String slug, @PathVariable int n) {
        current.require();
        return view(pull(slug, n));
    }

    @PutMapping("/{n}")
    public PullView edit(@PathVariable String slug, @PathVariable int n, @RequestBody EditPull r) {
        User me = current.require();
        PullRequest p = pull(slug, n);
        if (r.title() != null && !r.title().isBlank()) p.setTitle(r.title().trim());
        if (r.description() != null) p.setDescription(r.description());
        if (r.reviewerIds() != null) setReviewers(p, r.reviewerIds(), me);
        p.setUpdatedAt(Instant.now());
        return view(p);
    }

    @PostMapping("/{n}/review")
    public PullView review(@PathVariable String slug, @PathVariable int n, @RequestBody ReviewRequest r) {
        User me = current.require();
        PullRequest p = open(pull(slug, n));
        if (!List.of("APPROVED", "NEEDS_WORK", "PENDING").contains(r.state())) throw ApiException.badRequest("Review is APPROVED, NEEDS_WORK or PENDING.");
        if (p.getAuthor() != null && p.getAuthor().getId().equals(me.getId())) throw ApiException.badRequest("You can't review your own pull request.");
        PullReviewer rv = reviewers.findByPullOrderByIdAsc(p).stream().filter(x -> x.getUser().getId().equals(me.getId())).findFirst().orElseGet(() -> {
            PullReviewer x = new PullReviewer();
            x.setPull(p);
            x.setUser(me);
            return x;
        });
        rv.setState(r.state());
        reviewers.save(rv);
        p.setUpdatedAt(Instant.now());
        if (!"PENDING".equals(r.state())) {
            notify(p, me, "pr.reviewed", ("APPROVED".equals(r.state()) ? "approved " : "asked for changes on ") + ref(p), authorOnly(p));
        }
        return view(p);
    }

    @PostMapping("/{n}/comments")
    public PullView comment(@PathVariable String slug, @PathVariable int n, @RequestBody CommentRequest r) {
        User me = current.require();
        PullRequest p = pull(slug, n);
        if (r.body() == null || r.body().isBlank()) throw ApiException.badRequest("Write a comment first.");
        PullComment c = new PullComment();
        c.setPull(p);
        c.setAuthor(me);
        c.setBody(r.body().trim());
        c.setPath(r.path() == null || r.path().isBlank() ? null : Vcs.cleanPath(r.path()));
        c.setLine(c.getPath() == null ? null : r.line());
        comments.save(c);
        p.setUpdatedAt(Instant.now());
        Set<Long> to = new LinkedHashSet<>(authorOnly(p));
        reviewers.findByPullOrderByIdAsc(p).forEach(x -> to.add(x.getUser().getId()));
        comments.findByPullOrderByCreatedAtAsc(p).forEach(x -> { if (x.getAuthor() != null) to.add(x.getAuthor().getId()); });
        notify(p, me, "pr.commented", "commented on " + ref(p), List.copyOf(to));
        return view(p);
    }

    @PostMapping("/{n}/merge")
    public PullView merge(@PathVariable String slug, @PathVariable int n) {
        User me = current.require();
        PullRequest p = open(pull(slug, n));
        CodeRepo repo = p.getRepo();
        List<Vcs.Change> cs = vcs.changes(repo, p.getSourceBranch(), p.getTargetBranch());
        Commit c = vcs.merge(repo, p.getSourceBranch(), p.getTargetBranch(), me,
                "Merge pull request #" + p.getNumber() + " from " + p.getSourceBranch() + "\n\n" + p.getTitle());
        for (Vcs.Change ch : cs) {
            PullFile f = new PullFile();
            f.setPull(p);
            f.setPath(ch.path());
            f.setBeforeContent(ch.before());
            f.setAfterContent(ch.after());
            f.setAdded(ch.added());
            pullFiles.save(f);
        }
        p.setStatus("MERGED");
        p.setMergeSha(c.getSha());
        p.setClosedBy(me);
        p.setClosedAt(Instant.now());
        notify(p, me, "pr.merged", "merged " + ref(p), authorOnly(p));
        return view(p);
    }

    @PostMapping("/{n}/decline")
    public PullView decline(@PathVariable String slug, @PathVariable int n) {
        User me = current.require();
        PullRequest p = open(pull(slug, n));
        p.setStatus("DECLINED");
        p.setClosedBy(me);
        p.setClosedAt(Instant.now());
        notify(p, me, "pr.declined", "declined " + ref(p), authorOnly(p));
        return view(p);
    }

    @PostMapping("/{n}/reopen")
    public PullView reopen(@PathVariable String slug, @PathVariable int n) {
        current.require();
        PullRequest p = pull(slug, n);
        if (!"DECLINED".equals(p.getStatus())) throw ApiException.badRequest("Only declined pull requests can be reopened.");
        if (!pulls.findByRepoAndSourceBranchAndStatus(p.getRepo(), p.getSourceBranch(), "OPEN").isEmpty()) {
            throw ApiException.conflict(p.getSourceBranch() + " already has an open pull request.");
        }
        vcs.branch(p.getRepo(), p.getSourceBranch());
        p.setStatus("OPEN");
        p.setClosedBy(null);
        p.setClosedAt(null);
        return view(p);
    }

    /** Brings target changes into the branch where the branch hasn't changed the same files. */
    @PostMapping("/{n}/update-branch")
    public Map<String, Object> updateBranch(@PathVariable String slug, @PathVariable int n) {
        current.require();
        PullRequest p = open(pull(slug, n));
        int updated = vcs.updateFromTarget(p.getRepo(), p.getSourceBranch(), p.getTargetBranch());
        p.setUpdatedAt(Instant.now());
        return Map.of("updated", updated, "pull", view(p));
    }

    /** Keeps the branch's version of a conflicting file. */
    @PostMapping("/{n}/resolve")
    public PullView resolve(@PathVariable String slug, @PathVariable int n, @RequestBody PathRequest r) {
        current.require();
        PullRequest p = open(pull(slug, n));
        vcs.resolve(p.getRepo(), p.getSourceBranch(), p.getTargetBranch(), Vcs.cleanPath(r.path()));
        return view(p);
    }

    // ---- views ----------------------------------------------------------------------

    private PullView view(PullRequest p) {
        CodeRepo repo = p.getRepo();
        boolean open = "OPEN".equals(p.getStatus());
        List<FileDiff> files = new ArrayList<>();
        List<String> conflicts = new ArrayList<>();
        if (open) {
            for (Vcs.Change ch : vcs.changes(repo, p.getSourceBranch(), p.getTargetBranch())) {
                LineDiff.Result d = LineDiff.diff(ch.before(), ch.after());
                files.add(new FileDiff(ch.path(), d.additions(), d.deletions(), ch.added(), ch.conflict(), d.tooLarge(), d.hunks()));
                if (ch.conflict()) conflicts.add(ch.path());
            }
        } else {
            for (PullFile f : pullFiles.findByPullOrderByPathAsc(p)) {
                LineDiff.Result d = LineDiff.diff(f.getBeforeContent(), f.getAfterContent());
                files.add(new FileDiff(f.getPath(), d.additions(), d.deletions(), f.isAdded(), false, d.tooLarge(), d.hunks()));
            }
        }
        // Commits recorded on the source branch since it was created (branches don't inherit the base's history).
        Instant since = branchCreated(p);
        List<CodeService.CommitView> cs = commits.findByRepoAndBranchOrderByCreatedAtDesc(repo, p.getSourceBranch(), PageRequest.of(0, 100)).stream()
                .filter(c -> since == null || !c.getCreatedAt().isBefore(since))
                .map(c -> code.view(repo, c)).toList();
        Checks checks = pipelines.findFirstByRepoAndBranchOrderByNumberDesc(repo, p.getSourceBranch())
                .map(pl -> new Checks(pl.getNumber(), pl.getStatus(), stages.findByPipelineOrderByPositionAsc(pl).stream()
                        .map(s -> new StageView(s.getName(), s.getStatus(), s.getDurationSeconds())).toList()))
                .orElse(null);
        Set<String> keys = new LinkedHashSet<>();
        for (String text : new String[]{p.getTitle(), p.getSourceBranch(), p.getDescription()}) {
            if (text == null) continue;
            Matcher m = ISSUE_KEY.matcher(text.toUpperCase(Locale.ROOT));
            while (m.find()) keys.add(m.group(1));
        }
        List<IssueLink> linked = keys.stream().map(k -> issues.findByIssueKeyIgnoreCase(k).orElse(null)).filter(i -> i != null)
                .map(i -> new IssueLink(i.getIssueKey(), i.getSummary(), i.getStatus().name())).toList();
        return new PullView(p.getNumber(), repo.getSlug(), repo.getNamespace() + " / " + repo.getName(), p.getTitle(), p.getDescription(),
                p.getSourceBranch(), p.getTargetBranch(), UserRef.of(p.getAuthor()), p.getStatus(), p.getCreatedAt(), p.getUpdatedAt(),
                UserRef.of(p.getClosedBy()), p.getClosedAt(), p.getMergeSha(), reviewerViews(p),
                comments.findByPullOrderByCreatedAtAsc(p).stream()
                        .map(c -> new CommentView(c.getId(), UserRef.of(c.getAuthor()), c.getBody(), c.getPath(), c.getLine(), c.getCreatedAt())).toList(),
                cs, files, checks, linked, open && conflicts.isEmpty() && !files.isEmpty(), conflicts,
                files.stream().mapToInt(FileDiff::additions).sum(), files.stream().mapToInt(FileDiff::deletions).sum());
    }

    private Instant branchCreated(PullRequest p) {
        return vcs.branchIfExists(p.getRepo(), p.getSourceBranch()).map(Branch::getCreatedAt).orElse(null);
    }

    private List<ReviewerView> reviewerViews(PullRequest p) {
        return reviewers.findByPullOrderByIdAsc(p).stream().map(r -> new ReviewerView(UserRef.of(r.getUser()), r.getState())).toList();
    }

    private void setReviewers(PullRequest p, List<Long> ids, User me) {
        if (ids == null) return;
        List<PullReviewer> existing = reviewers.findByPullOrderByIdAsc(p);
        Set<Long> want = new LinkedHashSet<>(ids);
        if (p.getAuthor() != null) want.remove(p.getAuthor().getId());
        existing.stream().filter(r -> !want.contains(r.getUser().getId())).forEach(reviewers::delete);
        List<Long> added = new ArrayList<>();
        for (Long id : want) {
            if (existing.stream().anyMatch(r -> r.getUser().getId().equals(id))) continue;
            PullReviewer r = new PullReviewer();
            r.setPull(p);
            r.setUser(users.findById(id).orElseThrow(() -> ApiException.notFound("User")));
            reviewers.save(r);
            added.add(id);
        }
        if (!added.isEmpty()) notify(p, me, "pr.review_requested", "asked you to review " + ref(p), added);
    }

    private void notify(PullRequest p, User actor, String type, String summary, List<Long> to) {
        events.publish(DomainEvent.of(type, "code", "pull", p.getRepo().getName() + "#" + p.getNumber(), p.getTitle(), CodeService.pullLink(p),
                UserRef.of(actor), summary, to, Map.of("notifyKind", "direct")));
    }

    private static List<Long> authorOnly(PullRequest p) {
        return p.getAuthor() == null ? List.of() : List.of(p.getAuthor().getId());
    }

    private static String ref(PullRequest p) {
        return p.getRepo().getName() + " #" + p.getNumber() + " " + p.getTitle();
    }

    private static PullRequest open(PullRequest p) {
        if (!"OPEN".equals(p.getStatus())) throw ApiException.badRequest("This pull request is " + p.getStatus().toLowerCase(Locale.ROOT) + ".");
        return p;
    }

    private PullRequest pull(String slug, int n) {
        return pulls.findByRepoAndNumber(code.repo0(slug), n).orElseThrow(() -> ApiException.notFound("Pull request"));
    }
}
