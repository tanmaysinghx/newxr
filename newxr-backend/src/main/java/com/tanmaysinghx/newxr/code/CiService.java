package com.tanmaysinghx.newxr.code;

import com.tanmaysinghx.newxr.common.ApiException;
import com.tanmaysinghx.newxr.events.DomainEvent;
import com.tanmaysinghx.newxr.events.EventBus;
import com.tanmaysinghx.newxr.security.CurrentUser;
import com.tanmaysinghx.newxr.user.User;
import com.tanmaysinghx.newxr.user.UserRef;
import com.tanmaysinghx.newxr.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * Pipelines and deployments. Newxr doesn't run builds: CI reports runs to the repository webhook
 * ({@code POST /api/public/code/hooks/{token}}), and Newxr shows them on branches, pull requests and Track issues.
 */
@RestController
@Transactional
public class CiService {

    private static final SecureRandom RANDOM = new SecureRandom();
    static final String HOOK_PATH = "/api/public/code/hooks/";

    public record StageView(String name, String status, Integer durationSeconds, String log) {}

    public record PipelineRow(int number, String branch, String commitSha, String commitMessage, String status, UserRef triggeredBy,
                              String triggeredByName, Instant startedAt, Instant finishedAt, Long durationSeconds, String url) {}

    public record PipelineView(PipelineRow pipeline, List<StageView> stages) {}

    public record DeploymentView(Long id, String environment, String status, String branch, String commitSha, String version, Integer pipelineNumber,
                                 String deployedBy, String url, Instant at) {}

    public record TokenView(String webhookPath, String tokenHint) {}

    private final CodeService code;
    private final CodeRepoRepository repos;
    private final PipelineRepository pipelines;
    private final PipelineStageRepository stages;
    private final DeploymentRepository deployments;
    private final CommitRepository commits;
    private final UserRepository users;
    private final CurrentUser current;
    private final EventBus events;

    public CiService(CodeService code, CodeRepoRepository repos, PipelineRepository pipelines, PipelineStageRepository stages,
                     DeploymentRepository deployments, CommitRepository commits, UserRepository users, CurrentUser current, EventBus events) {
        this.code = code;
        this.repos = repos;
        this.pipelines = pipelines;
        this.stages = stages;
        this.deployments = deployments;
        this.commits = commits;
        this.users = users;
        this.current = current;
        this.events = events;
    }

    @GetMapping("/api/code/repos/{slug}/pipelines")
    @Transactional(readOnly = true)
    public List<PipelineRow> pipelines(@PathVariable String slug, @RequestParam(required = false) String branch) {
        return pipelines.findByRepoOrderByNumberDesc(code.repo0(slug), PageRequest.of(0, 100)).stream()
                .filter(p -> branch == null || branch.isBlank() || p.getBranch().equals(branch)).map(CiService::row).toList();
    }

    @GetMapping("/api/code/repos/{slug}/pipelines/{n}")
    @Transactional(readOnly = true)
    public PipelineView pipeline(@PathVariable String slug, @PathVariable int n) {
        return view(pipeline0(slug, n));
    }

    /** Marks a run as stopped in Newxr. The CI system itself must be stopped there; this only records it. */
    @PostMapping("/api/code/repos/{slug}/pipelines/{n}/stop")
    public PipelineView stop(@PathVariable String slug, @PathVariable int n) {
        User me = current.require();
        Pipeline p = pipeline0(slug, n);
        if (!List.of("RUNNING", "QUEUED").contains(p.getStatus())) throw ApiException.badRequest("Only running pipelines can be stopped.");
        p.setStatus("STOPPED");
        p.setFinishedAt(Instant.now());
        stages.findByPipelineOrderByPositionAsc(p).stream().filter(s -> List.of("RUNNING", "QUEUED").contains(s.getStatus()))
                .forEach(s -> s.setStatus("STOPPED"));
        if (p.getTriggeredByName() == null) p.setTriggeredByName(me.getDisplayName());
        return view(p);
    }

    @GetMapping("/api/code/repos/{slug}/deployments")
    @Transactional(readOnly = true)
    public List<DeploymentView> deployments(@PathVariable String slug) {
        return deployments.findByRepoOrderByCreatedAtDesc(code.repo0(slug), PageRequest.of(0, 100)).stream().map(CiService::view).toList();
    }

    /** New CI webhook URL; the previous one stops working. Shown once. */
    @PostMapping("/api/code/repos/{slug}/ci-token")
    public TokenView rotate(@PathVariable String slug) {
        current.require();
        CodeRepo repo = code.repo0(slug);
        byte[] b = new byte[24];
        RANDOM.nextBytes(b);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(b);
        repo.setCiTokenHash(hash(token));
        repo.setCiTokenHint(token.substring(token.length() - 4));
        return new TokenView(HOOK_PATH + token, repo.getCiTokenHint());
    }

    // ---- webhook --------------------------------------------------------------------

    /**
     * {@code {"type":"pipeline","id":"run-1","branch":"main","commit":"a41c9e2…","message":"…","status":"running",
     * "triggeredBy":"grace@…","url":"…","stages":[{"name":"Build","status":"passed","durationSeconds":161,"log":"…"}]}}
     * or {@code {"type":"deployment","environment":"beta","status":"succeeded","branch":"main","commit":"…","version":"4.12.0"}}.
     */
    @PostMapping(HOOK_PATH + "{token}")
    public Map<String, Object> receive(@PathVariable String token, @RequestBody Map<String, Object> body) {
        CodeRepo repo = repos.findByCiTokenHash(hash(token)).orElseThrow(() -> ApiException.notFound("Repository"));
        String type = str(body.get("type"));
        if ("deployment".equalsIgnoreCase(type)) return Map.of("deployment", deployment(repo, body).getId());
        if (type != null && !"pipeline".equalsIgnoreCase(type)) throw ApiException.badRequest("type must be pipeline or deployment.");
        return Map.of("pipeline", pipelineEvent(repo, body).getNumber());
    }

    private Pipeline pipelineEvent(CodeRepo repo, Map<String, Object> body) {
        String ext = str(body.get("id"));
        Pipeline p = ext == null ? null : pipelines.findByRepoAndExternalId(repo, ext).orElse(null);
        Instant now = Instant.now();
        String before = p == null ? null : p.getStatus();
        if (p == null) {
            String branch = str(body.get("branch"));
            if (branch == null) throw ApiException.badRequest("Send the branch the pipeline runs on.");
            CodeRepo locked = code.lock(repo);
            locked.setPipelineSeq(locked.getPipelineSeq() + 1);
            p = new Pipeline();
            p.setRepo(locked);
            p.setNumber(locked.getPipelineSeq());
            p.setExternalId(ext);
            p.setBranch(Vcs.trim(branch, 100));
            p.setStartedAt(now);
        }
        if (str(body.get("commit")) != null) p.setCommitSha(Vcs.trim(str(body.get("commit")), 40));
        if (str(body.get("message")) != null) p.setCommitMessage(Vcs.firstLine(str(body.get("message"))));
        if (p.getCommitMessage() == null && p.getCommitSha() != null) {
            String sha = p.getCommitSha();
            Commit known = commits.findByRepoAndBranchOrderByCreatedAtDesc(repo, p.getBranch(), PageRequest.of(0, 50)).stream()
                    .filter(c -> c.getSha().startsWith(sha)).findFirst().orElse(null);
            if (known != null) p.setCommitMessage(Vcs.firstLine(known.getMessage()));
        }
        if (str(body.get("url")) != null) p.setUrl(Vcs.trim(str(body.get("url")), 400));
        String by = str(body.get("triggeredBy"));
        if (by != null) {
            User u = users.findByEmailIgnoreCase(by).orElse(null);
            p.setTriggeredBy(u);
            p.setTriggeredByName(u == null ? Vcs.trim(by, 120) : u.getDisplayName());
        }
        p.setStatus(status(str(body.get("status")), p.getStatus()));
        if (List.of("PASSED", "FAILED", "STOPPED").contains(p.getStatus())) {
            if (p.getFinishedAt() == null) p.setFinishedAt(now);
        } else {
            p.setFinishedAt(null);
        }
        Pipeline saved = pipelines.save(p);
        if (body.get("stages") instanceof List<?> list) {
            stages.deleteByPipeline(saved);
            stages.flush();
            int i = 0;
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> m) || str(m.get("name")) == null) continue;
                PipelineStage s = new PipelineStage();
                s.setPipeline(saved);
                s.setPosition(i++);
                s.setName(Vcs.trim(str(m.get("name")), 80));
                s.setStatus(status(str(m.get("status")), "QUEUED"));
                s.setDurationSeconds(m.get("durationSeconds") instanceof Number n ? n.intValue() : null);
                String log = str(m.get("log"));
                s.setLog(log == null ? null : log.length() > 64_000 ? log.substring(log.length() - 64_000) : log);
                stages.save(s);
            }
        }
        if ("FAILED".equals(saved.getStatus()) && !"FAILED".equals(before)) {
            List<Long> to = saved.getTriggeredBy() == null ? List.of() : List.of(saved.getTriggeredBy().getId());
            events.publish(DomainEvent.of("pipeline.failed", "code", "pipeline", repo.getName() + "#" + saved.getNumber(),
                    "Pipeline #" + saved.getNumber() + " failed", "/code/" + repo.getSlug() + "/pipelines/" + saved.getNumber(), null,
                    "reports pipeline #" + saved.getNumber() + " on " + saved.getBranch() + " failed", to, Map.of("notifyKind", "direct")));
        }
        return saved;
    }

    private Deployment deployment(CodeRepo repo, Map<String, Object> body) {
        String env = str(body.get("environment"));
        if (env == null) throw ApiException.badRequest("Send the environment you deployed to.");
        Deployment d = new Deployment();
        d.setRepo(repo);
        d.setEnvironment(Vcs.trim(env, 40));
        String s = str(body.get("status"));
        d.setStatus(s == null ? "SUCCEEDED" : switch (s.toLowerCase(Locale.ROOT)) {
            case "failed", "failure", "error" -> "FAILED";
            case "in_progress", "running", "pending" -> "IN_PROGRESS";
            default -> "SUCCEEDED";
        });
        d.setBranch(str(body.get("branch")) == null ? null : Vcs.trim(str(body.get("branch")), 100));
        d.setCommitSha(str(body.get("commit")) == null ? null : Vcs.trim(str(body.get("commit")), 40));
        d.setVersion(str(body.get("version")) == null ? null : Vcs.trim(str(body.get("version")), 60));
        d.setPipelineNumber(body.get("pipeline") instanceof Number n ? n.intValue() : null);
        d.setDeployedBy(str(body.get("deployedBy")) == null ? null : Vcs.trim(str(body.get("deployedBy")), 120));
        d.setUrl(str(body.get("url")) == null ? null : Vcs.trim(str(body.get("url")), 400));
        return deployments.save(d);
    }

    /** running/passed/failed and the usual CI synonyms (success, failure, canceled...). */
    static String status(String s, String fallback) {
        if (s == null) return fallback == null ? "RUNNING" : fallback;
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "queued", "pending", "waiting", "created" -> "QUEUED";
            case "passed", "success", "succeeded", "ok", "completed" -> "PASSED";
            case "failed", "failure", "error", "errored" -> "FAILED";
            case "stopped", "canceled", "cancelled", "aborted", "skipped" -> "STOPPED";
            default -> "RUNNING";
        };
    }

    // ---- views ----------------------------------------------------------------------

    static PipelineRow row(Pipeline p) {
        Long d = p.getStartedAt() == null ? null : Duration.between(p.getStartedAt(), p.getFinishedAt() == null ? Instant.now() : p.getFinishedAt()).toSeconds();
        return new PipelineRow(p.getNumber(), p.getBranch(), p.getCommitSha(), p.getCommitMessage(), p.getStatus(), UserRef.of(p.getTriggeredBy()),
                p.getTriggeredByName(), p.getStartedAt(), p.getFinishedAt(), d, p.getUrl());
    }

    private PipelineView view(Pipeline p) {
        return new PipelineView(row(p), stages.findByPipelineOrderByPositionAsc(p).stream()
                .map(s -> new StageView(s.getName(), s.getStatus(), s.getDurationSeconds(), s.getLog())).toList());
    }

    private static DeploymentView view(Deployment d) {
        return new DeploymentView(d.getId(), d.getEnvironment(), d.getStatus(), d.getBranch(), d.getCommitSha(), d.getVersion(), d.getPipelineNumber(),
                d.getDeployedBy(), d.getUrl(), d.getCreatedAt());
    }

    private Pipeline pipeline0(String slug, int n) {
        return pipelines.findByRepoAndNumber(code.repo0(slug), n).orElseThrow(() -> ApiException.notFound("Pipeline"));
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String str(Object o) {
        return o == null || o.toString().isBlank() ? null : o.toString();
    }
}
