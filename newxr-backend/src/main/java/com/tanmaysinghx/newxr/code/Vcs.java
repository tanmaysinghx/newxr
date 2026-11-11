package com.tanmaysinghx.newxr.code;

import com.tanmaysinghx.newxr.common.ApiException;
import com.tanmaysinghx.newxr.user.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Minimal version control over files stored in the database. Each branch has its own copy of every file; a file
 * copied from the base branch keeps the content hash it started from, which acts as the merge base:
 * changed on the branch = hash differs from base hash (or new); conflict = the target changed the same file too.
 */
@Service
@Transactional
public class Vcs {

    static final int MAX_FILE_BYTES = 512 * 1024;
    static final Pattern PATH = Pattern.compile("[A-Za-z0-9._\\-]+(/[A-Za-z0-9._\\-]+)*");
    static final Pattern BRANCH = Pattern.compile("[A-Za-z0-9._\\-]+(/[A-Za-z0-9._\\-]+)*");
    private static final SecureRandom RANDOM = new SecureRandom();

    /** A file that differs between a branch and its target. {@code before} is the target's content ("" when new). */
    public record Change(String path, String before, String after, boolean added, boolean conflict) {}

    private final BranchRepository branches;
    private final RepoFileRepository files;
    private final CommitRepository commits;

    public Vcs(BranchRepository branches, RepoFileRepository files, CommitRepository commits) {
        this.branches = branches;
        this.files = files;
        this.commits = commits;
    }

    public Branch branch(CodeRepo repo, String name) {
        return branches.findByRepoAndName(repo, name).orElseThrow(() -> ApiException.notFound("Branch " + name));
    }

    public java.util.Optional<Branch> branchIfExists(CodeRepo repo, String name) {
        return branches.findByRepoAndName(repo, name);
    }

    /** Writes the given files to a branch as one commit. Unchanged files are skipped; nothing changed is an error. */
    public Commit commit(CodeRepo repo, String branchName, User author, String message, Map<String, String> changes) {
        return commit(repo, branchName, author, message, changes, Instant.now());
    }

    /** As {@link #commit(CodeRepo, String, User, String, Map)} at a given time (imports and sample data). */
    Commit commit(CodeRepo repo, String branchName, User author, String message, Map<String, String> changes, Instant now) {
        if (message == null || message.isBlank()) throw ApiException.badRequest("Write a commit message.");
        Branch b = branch(repo, branchName);
        String sha = newSha();
        int adds = 0, dels = 0;
        List<String> touched = new ArrayList<>();
        for (Map.Entry<String, String> e : changes.entrySet()) {
            String path = cleanPath(e.getKey());
            String content = e.getValue() == null ? "" : e.getValue();
            if (content.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES) throw ApiException.badRequest(path + " is larger than 512 KB.");
            RepoFile f = files.findByRepoAndBranchAndPath(repo, branchName, path).orElse(null);
            String before = f == null ? "" : f.getContent();
            if (f != null && before.equals(content)) continue;
            LineDiff.Result d = LineDiff.diff(before, content);
            adds += d.additions();
            dels += d.deletions();
            if (f == null) {
                f = new RepoFile();
                f.setRepo(repo);
                f.setBranch(branchName);
                f.setPath(path);
            }
            f.setContent(content);
            f.setHash(hash(content));
            f.setLastCommitSha(sha);
            f.setLastCommitMessage(firstLine(message));
            f.setLastCommitAt(now);
            files.save(f);
            touched.add(path);
        }
        if (touched.isEmpty()) throw ApiException.badRequest("Nothing changed.");
        Commit c = new Commit();
        c.setRepo(repo);
        c.setSha(sha);
        c.setBranch(branchName);
        c.setMessage(message.trim());
        c.setAuthor(author);
        c.setAdditions(adds);
        c.setDeletions(dels);
        c.setPaths(trim(String.join(", ", touched), 400));
        c.setCreatedAt(now);
        c.setUpdatedAt(now);
        commits.save(c);
        b.setHeadSha(sha);
        b.setUpdatedAt(now);
        return c;
    }

    /** New branch with a copy of every file of {@code from}; each copy remembers its starting hash. */
    public Branch createBranch(CodeRepo repo, String name, String from, User by) {
        return createBranch(repo, name, from, by, null);
    }

    Branch createBranch(CodeRepo repo, String name, String from, User by, Instant at) {
        String n = name == null ? "" : name.trim();
        if (!BRANCH.matcher(n).matches() || n.length() > 100) throw ApiException.badRequest("Use letters, digits, dots, dashes and slashes in branch names.");
        if (branches.findByRepoAndName(repo, n).isPresent()) throw ApiException.conflict("Branch " + n + " already exists.");
        Branch src = branch(repo, from);
        Branch b = new Branch();
        b.setRepo(repo);
        b.setName(n);
        b.setBaseBranch(src.getName());
        b.setHeadSha(src.getHeadSha());
        b.setCreatedBy(by);
        if (at != null) {
            b.setCreatedAt(at);
            b.setUpdatedAt(at);
        }
        b = branches.save(b);
        for (RepoFile f : files.findByRepoAndBranch(repo, from)) {
            RepoFile c = new RepoFile();
            c.setRepo(repo);
            c.setBranch(n);
            c.setPath(f.getPath());
            c.setContent(f.getContent());
            c.setHash(f.getHash());
            c.setBaseHash(f.getHash());
            c.setLastCommitSha(f.getLastCommitSha());
            c.setLastCommitMessage(f.getLastCommitMessage());
            c.setLastCommitAt(f.getLastCommitAt());
            files.save(c);
        }
        return b;
    }

    public void deleteBranch(CodeRepo repo, String name) {
        if (name.equals(repo.getDefaultBranch())) throw ApiException.badRequest("The default branch can't be deleted.");
        Branch b = branch(repo, name);
        files.deleteByRepoAndBranch(repo, name);
        branches.delete(b);
    }

    /** Files changed on {@code source} since it forked, compared with {@code target}. */
    public List<Change> changes(CodeRepo repo, String source, String target) {
        Map<String, RepoFile> t = new LinkedHashMap<>();
        files.findByRepoAndBranch(repo, target).forEach(f -> t.put(f.getPath(), f));
        List<Change> out = new ArrayList<>();
        for (RepoFile s : files.findByRepoAndBranch(repo, source)) {
            boolean changed = s.getBaseHash() == null || !s.getHash().equals(s.getBaseHash());
            if (!changed) continue;
            RepoFile tf = t.get(s.getPath());
            if (tf != null && tf.getHash().equals(s.getHash())) continue;
            boolean conflict = tf != null && (s.getBaseHash() == null || !tf.getHash().equals(s.getBaseHash()));
            out.add(new Change(s.getPath(), tf == null ? "" : tf.getContent(), s.getContent(), tf == null, conflict));
        }
        out.sort((a, b) -> a.path().compareTo(b.path()));
        return out;
    }

    /** Brings target changes the branch hasn't touched into the branch. Returns the number of files updated. */
    public int updateFromTarget(CodeRepo repo, String source, String target) {
        Map<String, RepoFile> s = new LinkedHashMap<>();
        files.findByRepoAndBranch(repo, source).forEach(f -> s.put(f.getPath(), f));
        int n = 0;
        for (RepoFile tf : files.findByRepoAndBranch(repo, target)) {
            RepoFile sf = s.get(tf.getPath());
            if (sf == null) {
                RepoFile c = new RepoFile();
                c.setRepo(repo);
                c.setBranch(source);
                c.setPath(tf.getPath());
                c.setContent(tf.getContent());
                c.setHash(tf.getHash());
                c.setBaseHash(tf.getHash());
                c.setLastCommitSha(tf.getLastCommitSha());
                c.setLastCommitMessage(tf.getLastCommitMessage());
                c.setLastCommitAt(tf.getLastCommitAt());
                files.save(c);
                n++;
            } else if (sf.getBaseHash() != null && sf.getHash().equals(sf.getBaseHash()) && !tf.getHash().equals(sf.getBaseHash())) {
                sf.setContent(tf.getContent());
                sf.setHash(tf.getHash());
                sf.setBaseHash(tf.getHash());
                sf.setLastCommitSha(tf.getLastCommitSha());
                sf.setLastCommitMessage(tf.getLastCommitMessage());
                sf.setLastCommitAt(tf.getLastCommitAt());
                n++;
            } else if (sf.getHash().equals(tf.getHash())) {
                sf.setBaseHash(tf.getHash());
            }
        }
        return n;
    }

    /** Marks the branch's version of a conflicting file as the resolution (its base becomes the target's version). */
    public void resolve(CodeRepo repo, String source, String target, String path) {
        RepoFile sf = files.findByRepoAndBranchAndPath(repo, source, path).orElseThrow(() -> ApiException.notFound("File"));
        RepoFile tf = files.findByRepoAndBranchAndPath(repo, target, path).orElse(null);
        sf.setBaseHash(tf == null ? null : tf.getHash());
    }

    /** Merges the branch's changes into the target as one commit; refuses while any file conflicts. */
    public Commit merge(CodeRepo repo, String source, String target, User by, String message) {
        List<Change> cs = changes(repo, source, target);
        List<String> conflicts = cs.stream().filter(Change::conflict).map(Change::path).toList();
        if (!conflicts.isEmpty()) {
            throw ApiException.conflict("Resolve conflicts first: " + String.join(", ", conflicts) + ".");
        }
        if (cs.isEmpty()) throw ApiException.badRequest("There is nothing to merge.");
        Map<String, String> m = new LinkedHashMap<>();
        cs.forEach(c -> m.put(c.path(), c.after()));
        Commit c = commit(repo, target, by, message, m);
        // The branch now matches the target for these files.
        for (Change ch : cs) {
            files.findByRepoAndBranchAndPath(repo, source, ch.path()).ifPresent(f -> f.setBaseHash(f.getHash()));
        }
        return c;
    }

    // ---- helpers -------------------------------------------------------------------

    public static String cleanPath(String p) {
        String s = p == null ? "" : p.trim().replace('\\', '/');
        while (s.startsWith("/")) s = s.substring(1);
        if (!PATH.matcher(s).matches() || s.length() > 400 || s.contains("..")) throw ApiException.badRequest("“" + p + "” isn't a valid file path.");
        return s;
    }

    public static String hash(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String newSha() {
        byte[] b = new byte[20];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    static String firstLine(String s) {
        String l = s.trim().split("\n", 2)[0];
        return trim(l, 255);
    }

    static String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
