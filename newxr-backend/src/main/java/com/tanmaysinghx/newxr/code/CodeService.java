package com.tanmaysinghx.newxr.code;

import com.tanmaysinghx.newxr.common.ApiException;
import com.tanmaysinghx.newxr.hub.HubContributor;
import com.tanmaysinghx.newxr.hub.WorkItem;
import com.tanmaysinghx.newxr.security.CurrentUser;
import com.tanmaysinghx.newxr.settings.SettingsService;
import com.tanmaysinghx.newxr.user.User;
import com.tanmaysinghx.newxr.user.UserRef;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Repositories, source browsing and editing, branches, commits, and links back to Track issues. */
@RestController
@RequestMapping("/api/code")
@Transactional
public class CodeService implements HubContributor {

    private static final Pattern NAME = Pattern.compile("[a-z0-9][a-z0-9._-]{0,79}");

    public record RepoSummary(Long id, String namespace, String name, String slug, String description, String defaultBranch, String language,
                              long openPulls, int branches, String lastCommitMessage, Instant updatedAt, String lastPipelineStatus) {}

    public record RepoView(RepoSummary repo, List<UserRef> watchers, boolean watching, List<String> branchNames, boolean ciConfigured, String ciTokenHint) {}

    public record CommitView(String sha, String message, UserRef author, String branch, int additions, int deletions, String paths, Instant at,
                             String checks) {}

    public record Entry(String name, String path, boolean dir, String lastCommitMessage, Instant lastCommitAt, String lastCommitSha) {}

    public record Tree(String branch, String path, List<Entry> entries, String readmePath, String readme, CommitView lastCommit) {}

    public record FileView(String branch, String path, String content, int lines, String lastCommitSha, String lastCommitMessage, Instant lastCommitAt) {}

    public record BranchView(String name, String baseBranch, boolean isDefault, CommitView head, UserRef createdBy, Instant updatedAt,
                             Integer openPull, String pipelineStatus) {}

    public record CreateRepo(String name, String description, String defaultBranch, String language) {}

    public record SaveFile(String branch, String path, String content, String message, String newBranch) {}

    public record CreateBranch(String name, String from) {}

    public record Linked(String kind, String title, String status, String link) {}

    private final CodeRepoRepository repos;
    private final BranchRepository branches;
    private final RepoFileRepository files;
    private final CommitRepository commits;
    private final PullRequestRepository pulls;
    private final PullReviewerRepository reviewers;
    private final PipelineRepository pipelines;
    private final Vcs vcs;
    private final CurrentUser current;
    private final SettingsService settings;

    public CodeService(CodeRepoRepository repos, BranchRepository branches, RepoFileRepository files, CommitRepository commits,
                       PullRequestRepository pulls, PullReviewerRepository reviewers, PipelineRepository pipelines, Vcs vcs,
                       CurrentUser current, SettingsService settings) {
        this.repos = repos;
        this.branches = branches;
        this.files = files;
        this.commits = commits;
        this.pulls = pulls;
        this.reviewers = reviewers;
        this.pipelines = pipelines;
        this.vcs = vcs;
        this.current = current;
        this.settings = settings;
    }

    // ---- repositories ---------------------------------------------------------------

    @GetMapping("/repos")
    @Transactional(readOnly = true)
    public List<RepoSummary> repos() {
        return repos.findAllByOrderByNameAsc().stream().map(this::summary).toList();
    }

    @PostMapping("/repos")
    @ResponseStatus(HttpStatus.CREATED)
    public RepoView create(@RequestBody CreateRepo r) {
        User me = current.require();
        String name = r.name() == null ? "" : r.name().trim().toLowerCase(Locale.ROOT).replace(' ', '-');
        if (!NAME.matcher(name).matches()) throw ApiException.badRequest("Use lower-case letters, digits, dots, dashes and underscores for the name.");
        if (repos.findBySlugIgnoreCase(name).isPresent()) throw ApiException.conflict("A repository called " + name + " already exists.");
        String branch = r.defaultBranch() == null || r.defaultBranch().isBlank() ? "main" : r.defaultBranch().trim();
        if (!Vcs.BRANCH.matcher(branch).matches()) throw ApiException.badRequest("That isn't a valid branch name.");
        CodeRepo repo = new CodeRepo();
        repo.setNamespace(namespace());
        repo.setName(name);
        repo.setSlug(name);
        repo.setDescription(r.description());
        repo.setDefaultBranch(branch);
        repo.setLanguage(r.language());
        repo.getWatchers().add(me);
        repo = repos.save(repo);
        Branch b = new Branch();
        b.setRepo(repo);
        b.setName(branch);
        b.setCreatedBy(me);
        branches.save(b);
        vcs.commit(repo, branch, me, "Initial commit", Map.of("README.md", "# " + name + "\n\n" + (r.description() == null ? "" : r.description() + "\n")));
        return view(repo, me);
    }

    /** Workspace shown before repository names, from the organization name ("Northwind" → "northwind"). */
    String namespace() {
        String org = settings.org().name();
        String ns = org == null ? "" : org.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return ns.isEmpty() ? "newxr" : Vcs.trim(ns, 40);
    }

    @GetMapping("/repos/{slug}")
    @Transactional(readOnly = true)
    public RepoView repo(@PathVariable String slug) {
        return view(repo0(slug), current.require());
    }

    @PutMapping("/repos/{slug}")
    public RepoView edit(@PathVariable String slug, @RequestBody CreateRepo r) {
        User me = current.require();
        CodeRepo repo = repo0(slug);
        if (r.description() != null) repo.setDescription(r.description());
        if (r.language() != null) repo.setLanguage(r.language().isBlank() ? null : r.language().trim());
        if (r.defaultBranch() != null && !r.defaultBranch().equals(repo.getDefaultBranch())) {
            vcs.branch(repo, r.defaultBranch());
            repo.setDefaultBranch(r.defaultBranch());
        }
        return view(repo, me);
    }

    @PostMapping("/repos/{slug}/watch")
    public RepoView watch(@PathVariable String slug) {
        User me = current.require();
        CodeRepo repo = repo0(slug);
        if (!repo.getWatchers().removeIf(u -> u.getId().equals(me.getId()))) repo.getWatchers().add(me);
        return view(repo, me);
    }

    // ---- source -------------------------------------------------------------------

    @GetMapping("/repos/{slug}/tree")
    @Transactional(readOnly = true)
    public Tree tree(@PathVariable String slug, @RequestParam(required = false) String branch, @RequestParam(defaultValue = "") String path) {
        CodeRepo repo = repo0(slug);
        String b = branch == null || branch.isBlank() ? repo.getDefaultBranch() : branch;
        vcs.branch(repo, b);
        String dir = path.isBlank() ? "" : Vcs.cleanPath(path) + "/";
        Map<String, Entry> dirs = new TreeMap<>(), plain = new TreeMap<>();
        for (RepoFile f : files.findByRepoAndBranch(repo, b)) {
            if (!f.getPath().startsWith(dir)) continue;
            String rest = f.getPath().substring(dir.length());
            int slash = rest.indexOf('/');
            if (slash > 0) {
                String name = rest.substring(0, slash);
                Entry prev = dirs.get(name);
                if (prev == null || (f.getLastCommitAt() != null && (prev.lastCommitAt() == null || f.getLastCommitAt().isAfter(prev.lastCommitAt())))) {
                    dirs.put(name, new Entry(name, dir + name, true, f.getLastCommitMessage(), f.getLastCommitAt(), f.getLastCommitSha()));
                }
            } else {
                plain.put(rest, new Entry(rest, f.getPath(), false, f.getLastCommitMessage(), f.getLastCommitAt(), f.getLastCommitSha()));
            }
        }
        if (dirs.isEmpty() && plain.isEmpty() && !dir.isEmpty()) throw ApiException.notFound("Folder " + path);
        List<Entry> entries = new ArrayList<>(dirs.values());
        entries.addAll(plain.values());
        String readmeName = plain.keySet().stream().filter(n -> n.equalsIgnoreCase("README.md")).findFirst().orElse(null);
        String readme = readmeName == null ? null : files.findByRepoAndBranchAndPath(repo, b, dir + readmeName).map(RepoFile::getContent).orElse(null);
        CommitView last = commits.findFirstByRepoAndBranchOrderByCreatedAtDesc(repo, b).map(c -> view(repo, c)).orElse(null);
        return new Tree(b, dir.isEmpty() ? "" : dir.substring(0, dir.length() - 1), entries, readmeName == null ? null : dir + readmeName, readme, last);
    }

    @GetMapping("/repos/{slug}/file")
    @Transactional(readOnly = true)
    public FileView file(@PathVariable String slug, @RequestParam(required = false) String branch, @RequestParam String path) {
        CodeRepo repo = repo0(slug);
        String b = branch == null || branch.isBlank() ? repo.getDefaultBranch() : branch;
        RepoFile f = files.findByRepoAndBranchAndPath(repo, b, Vcs.cleanPath(path)).orElseThrow(() -> ApiException.notFound("File " + path));
        return new FileView(b, f.getPath(), f.getContent(), LineDiff.lines(f.getContent()).length, f.getLastCommitSha(), f.getLastCommitMessage(), f.getLastCommitAt());
    }

    /** Creates or edits a file as a commit, optionally on a new branch forked from {@code branch}. */
    @PutMapping("/repos/{slug}/file")
    public CommitView save(@PathVariable String slug, @RequestBody SaveFile r) {
        User me = current.require();
        CodeRepo repo = repo0(slug);
        String b = r.branch() == null || r.branch().isBlank() ? repo.getDefaultBranch() : r.branch();
        if (r.newBranch() != null && !r.newBranch().isBlank()) {
            vcs.createBranch(repo, r.newBranch(), b, me);
            b = r.newBranch().trim();
        }
        return view(repo, vcs.commit(repo, b, me, r.message(), Map.of(r.path() == null ? "" : r.path(), r.content() == null ? "" : r.content())));
    }

    // ---- branches & commits -----------------------------------------------------------

    @GetMapping("/repos/{slug}/branches")
    @Transactional(readOnly = true)
    public List<BranchView> branches(@PathVariable String slug) {
        CodeRepo repo = repo0(slug);
        return branches.findByRepoOrderByUpdatedAtDesc(repo).stream()
                .sorted(Comparator.comparing((Branch b) -> !b.getName().equals(repo.getDefaultBranch())))
                .map(b -> new BranchView(b.getName(), b.getBaseBranch(), b.getName().equals(repo.getDefaultBranch()),
                        commits.findFirstByRepoAndBranchOrderByCreatedAtDesc(repo, b.getName()).map(c -> view(repo, c)).orElse(null),
                        UserRef.of(b.getCreatedBy()), b.getUpdatedAt(),
                        pulls.findByRepoAndSourceBranchAndStatus(repo, b.getName(), "OPEN").stream().map(PullRequest::getNumber).findFirst().orElse(null),
                        pipelines.findFirstByRepoAndBranchOrderByNumberDesc(repo, b.getName()).map(Pipeline::getStatus).orElse(null)))
                .toList();
    }

    @PostMapping("/repos/{slug}/branches")
    @ResponseStatus(HttpStatus.CREATED)
    public BranchView createBranch(@PathVariable String slug, @RequestBody CreateBranch r) {
        User me = current.require();
        CodeRepo repo = repo0(slug);
        Branch b = vcs.createBranch(repo, r.name(), r.from() == null || r.from().isBlank() ? repo.getDefaultBranch() : r.from(), me);
        return new BranchView(b.getName(), b.getBaseBranch(), false, null, UserRef.of(me), b.getUpdatedAt(), null, null);
    }

    @DeleteMapping("/repos/{slug}/branches")
    public void deleteBranch(@PathVariable String slug, @RequestParam String name) {
        current.require();
        CodeRepo repo = repo0(slug);
        if (!pulls.findByRepoAndSourceBranchAndStatus(repo, name, "OPEN").isEmpty()) {
            throw ApiException.conflict("Close or merge the open pull request from " + name + " first.");
        }
        vcs.deleteBranch(repo, name);
    }

    @GetMapping("/repos/{slug}/commits")
    @Transactional(readOnly = true)
    public List<CommitView> commits(@PathVariable String slug, @RequestParam(required = false) String branch) {
        CodeRepo repo = repo0(slug);
        String b = branch == null || branch.isBlank() ? repo.getDefaultBranch() : branch;
        return commits.findByRepoAndBranchOrderByCreatedAtDesc(repo, b, PageRequest.of(0, 200)).stream().map(c -> view(repo, c)).toList();
    }

    // ---- Track integration ----------------------------------------------------------

    /** Branches, pull requests, commits and pipelines that mention an issue key (ORB-412) — the Track Development panel. */
    @GetMapping("/linked/{issueKey}")
    @Transactional(readOnly = true)
    public List<Linked> linked(@PathVariable String issueKey) {
        String key = issueKey.trim().toUpperCase(Locale.ROOT);
        if (!key.matches("[A-Z][A-Z0-9]{0,9}-\\d{1,7}")) return List.of();
        Pattern word = Pattern.compile("(?<![A-Z0-9])" + Pattern.quote(key) + "(?![0-9])");
        List<Linked> out = new ArrayList<>();
        for (PullRequest p : pulls.mentioning(key)) {
            if (!word.matcher(p.getTitle().toUpperCase(Locale.ROOT)).find() && !word.matcher(p.getSourceBranch().toUpperCase(Locale.ROOT)).find()) continue;
            out.add(new Linked("pr", p.getRepo().getName() + " #" + p.getNumber() + " " + p.getTitle(), p.getStatus(), pullLink(p)));
        }
        for (Branch b : branches.findByNameContainingIgnoreCase(key)) {
            if (!word.matcher(b.getName().toUpperCase(Locale.ROOT)).find()) continue;
            out.add(new Linked("branch", b.getRepo().getName() + " · " + b.getName(), "OPEN",
                    "/code/" + b.getRepo().getSlug() + "/source?branch=" + b.getName()));
        }
        pipelines.mentioning(key, PageRequest.of(0, 10)).stream().filter(p -> word.matcher(p.getBranch().toUpperCase(Locale.ROOT)).find()).limit(3)
                .forEach(p -> out.add(new Linked("pipeline",
                p.getRepo().getName() + " pipeline #" + p.getNumber(), p.getStatus(), "/code/" + p.getRepo().getSlug() + "/pipelines/" + p.getNumber())));
        commits.mentioning(key, PageRequest.of(0, 20)).stream().filter(c -> word.matcher(c.getMessage().toUpperCase(Locale.ROOT)).find()).limit(5)
                .forEach(c -> out.add(new Linked("commit", c.getSha().substring(0, 7) + " " + Vcs.firstLine(c.getMessage()), "COMMITTED",
                        "/code/" + c.getRepo().getSlug() + "/commits?branch=" + c.getBranch())));
        Map<String, Linked> unique = new LinkedHashMap<>();
        out.forEach(l -> unique.putIfAbsent(l.link(), l));
        return List.copyOf(unique.values());
    }

    // ---- views ----------------------------------------------------------------------

    RepoSummary summary(CodeRepo r) {
        var last = commits.findFirstByRepoAndBranchOrderByCreatedAtDesc(r, r.getDefaultBranch());
        return new RepoSummary(r.getId(), r.getNamespace(), r.getName(), r.getSlug(), r.getDescription(), r.getDefaultBranch(), r.getLanguage(),
                pulls.countByRepoAndStatus(r, "OPEN"), branches.findByRepoOrderByUpdatedAtDesc(r).size(),
                last.map(c -> Vcs.firstLine(c.getMessage())).orElse(null), last.map(Commit::getCreatedAt).orElse(r.getUpdatedAt()),
                pipelines.findFirstByRepoAndBranchOrderByNumberDesc(r, r.getDefaultBranch()).map(Pipeline::getStatus).orElse(null));
    }

    private RepoView view(CodeRepo r, User me) {
        return new RepoView(summary(r), r.getWatchers().stream().map(UserRef::of).toList(),
                r.getWatchers().stream().anyMatch(u -> u.getId().equals(me.getId())),
                branches.findByRepoOrderByUpdatedAtDesc(r).stream().map(Branch::getName).sorted().toList(), r.getCiTokenHash() != null, r.getCiTokenHint());
    }

    CommitView view(CodeRepo repo, Commit c) {
        String checks = pipelines.findByRepoOrderByNumberDesc(repo, PageRequest.of(0, 50)).stream()
                .filter(p -> c.getSha().equals(p.getCommitSha())).map(Pipeline::getStatus).findFirst().orElse(null);
        return new CommitView(c.getSha(), c.getMessage(), UserRef.of(c.getAuthor()), c.getBranch(), c.getAdditions(), c.getDeletions(), c.getPaths(),
                c.getCreatedAt(), checks);
    }

    /** Row lock for numbering pull requests and pipelines. */
    CodeRepo lock(CodeRepo r) {
        return repos.lockById(r.getId()).orElseThrow(() -> ApiException.notFound("Repository"));
    }

    CodeRepo repo0(String slug) {
        return repos.findBySlugIgnoreCase(slug).orElseThrow(() -> ApiException.notFound("Repository"));
    }

    static String pullLink(PullRequest p) {
        return "/code/" + p.getRepo().getSlug() + "/pulls/" + p.getNumber();
    }

    // ---- hub ------------------------------------------------------------------------

    @Override
    public String app() {
        return "code";
    }

    @Override
    public List<WorkItem> assignedTo(User user) {
        return pulls.findByAuthorAndStatus(user, "OPEN").stream()
                .map(p -> WorkItem.of("code", "pr", p.getRepo().getName() + "#" + p.getNumber(), p.getTitle(),
                        p.getRepo().getName() + " · your pull request", p.getStatus(), null, pullLink(p), p.getUpdatedAt()))
                .toList();
    }

    @Override
    public List<WorkItem> waitingOn(User user) {
        return reviewers.openFor(user).stream().filter(r -> "PENDING".equals(r.getState()))
                .map(r -> r.getPull())
                .map(p -> WorkItem.of("code", "pr", p.getRepo().getName() + "#" + p.getNumber(), "Review: " + p.getTitle(),
                        p.getRepo().getName() + " · " + (p.getAuthor() == null ? "" : p.getAuthor().getDisplayName()), "NEEDS_REVIEW", null,
                        pullLink(p), p.getUpdatedAt()))
                .toList();
    }

    @Override
    public List<WorkItem> search(String q, User user, int limit) {
        List<WorkItem> out = new ArrayList<>();
        repos.search(q, PageRequest.of(0, limit)).forEach(r -> out.add(WorkItem.of("code", "repo", null, r.getNamespace() + " / " + r.getName(),
                "Repository" + (r.getDescription() == null ? "" : " · " + r.getDescription()), null, null, "/code/" + r.getSlug(), r.getUpdatedAt())));
        pulls.search(q, PageRequest.of(0, limit)).forEach(p -> out.add(WorkItem.of("code", "pr", p.getRepo().getName() + "#" + p.getNumber(),
                p.getTitle(), "Pull request · " + p.getRepo().getName(), p.getStatus(), null, pullLink(p), p.getUpdatedAt())));
        return out.stream().limit(limit).toList();
    }
}
