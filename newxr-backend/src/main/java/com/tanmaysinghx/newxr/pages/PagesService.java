package com.tanmaysinghx.newxr.pages;

import com.tanmaysinghx.newxr.common.ApiException;
import com.tanmaysinghx.newxr.events.DomainEvent;
import com.tanmaysinghx.newxr.events.EventBus;
import com.tanmaysinghx.newxr.hub.HubContributor;
import com.tanmaysinghx.newxr.hub.WorkItem;
import com.tanmaysinghx.newxr.security.CurrentUser;
import com.tanmaysinghx.newxr.user.User;
import com.tanmaysinghx.newxr.user.UserRef;
import com.tanmaysinghx.newxr.user.UserRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Pages (spaces, page tree, editor, comments, reactions) and its hub contributor. */
@RestController
@RequestMapping("/api/pages")
@Transactional
public class PagesService implements HubContributor {

    public record SpaceView(Long id, String key, String name, String description, String color, String type, UserRef owner,
                            String linkedProject, long pageCount, boolean archived) {}

    public record SpaceRequest(String key, String name, String description, String color, String type, String linkedProject) {}

    public record TreeNode(Long id, String title, String status, List<TreeNode> children) {}

    public record PageSummary(Long id, String title, String kind, String status, UserRef lastEditor, Instant updatedAt, String excerpt) {}

    public record Overview(SpaceView space, List<TreeNode> tree, List<PageSummary> pinned, List<PageSummary> recent,
                           List<UserRef> contributors) {}

    public record Crumb(Long id, String title) {}

    public record Reaction(String emoji, int count, boolean mine) {}

    public record CommentView(Long id, UserRef author, String body, Instant createdAt) {}

    public record PageView(Long id, String title, String body, String draftTitle, String draftBody, boolean hasDraft, String kind,
                           String status, int version, UserRef author, UserRef lastEditor, Instant createdAt, Instant updatedAt,
                           Instant publishedAt, Long parentId, boolean pinned, SpaceView space, List<Crumb> crumbs,
                           List<Reaction> reactions, List<CommentView> comments, int readMinutes) {}

    public record PageRequest2(String spaceKey, Long parentId, String title, String body, String kind, Boolean publish, Boolean pinned) {}

    public record MoveRequest(Long parentId, Long beforeId) {}

    public record VersionView(Long id, int version, String title, UserRef author, Instant createdAt) {}

    private final SpaceRepository spaces;
    private final PageRepository pages;
    private final PageVersionRepository versions;
    private final PageCommentRepository comments;
    private final PageReactionRepository reactions;
    private final UserRepository users;
    private final CurrentUser current;
    private final EventBus events;

    public PagesService(SpaceRepository spaces, PageRepository pages, PageVersionRepository versions, PageCommentRepository comments,
                        PageReactionRepository reactions, UserRepository users, CurrentUser current, EventBus events) {
        this.spaces = spaces;
        this.pages = pages;
        this.versions = versions;
        this.comments = comments;
        this.reactions = reactions;
        this.users = users;
        this.current = current;
        this.events = events;
    }

    // ================================================================== spaces

    @GetMapping("/spaces")
    @Transactional(readOnly = true)
    public List<SpaceView> listSpaces(@RequestParam(defaultValue = "false") boolean archived) {
        return spaces.findByArchivedOrderByNameAsc(archived).stream().map(this::view).toList();
    }

    @PostMapping("/spaces")
    @ResponseStatus(HttpStatus.CREATED)
    public SpaceView createSpace(@RequestBody SpaceRequest r) {
        User me = current.require();
        return view(createSpace(r, me));
    }

    public Space createSpace(SpaceRequest r, User owner) {
        String key = r.key() == null ? "" : r.key().trim().toUpperCase(Locale.ROOT);
        if (!key.matches("[A-Z~][A-Z0-9]{1,15}")) throw ApiException.badRequest("Space key: 2–16 letters or digits.");
        if (r.name() == null || r.name().isBlank()) throw ApiException.badRequest("Enter a space name.");
        if (spaces.existsBySpaceKeyIgnoreCase(key)) throw ApiException.conflict("Space key " + key + " is already used.");
        Space s = new Space();
        s.setSpaceKey(key);
        s.setName(r.name().trim());
        s.setDescription(r.description());
        s.setColor(r.color() == null ? "#0e9f8e" : r.color());
        s.setType(r.type() == null ? "TEAM" : r.type());
        s.setLinkedProject(r.linkedProject());
        s.setOwner(owner);
        s = spaces.save(s);
        Page home = new Page();
        home.setSpace(s);
        home.setTitle(s.getName() + " home");
        home.setBody("# Welcome to " + s.getName() + "\n\n" + (s.getDescription() == null ? "" : s.getDescription() + "\n\n")
                + "Use **Create** to add pages. Pin the ones your team needs most.");
        publishInternal(home, owner);
        home.setPinned(false);
        pages.save(home);
        return s;
    }

    @PutMapping("/spaces/{key}")
    public SpaceView updateSpace(@PathVariable String key, @RequestBody SpaceRequest r) {
        Space s = space(key);
        if (r.name() != null && !r.name().isBlank()) s.setName(r.name().trim());
        if (r.description() != null) s.setDescription(r.description());
        if (r.color() != null) s.setColor(r.color());
        if (r.linkedProject() != null) s.setLinkedProject(r.linkedProject().isBlank() ? null : r.linkedProject().trim().toUpperCase(Locale.ROOT));
        return view(s);
    }

    @PostMapping("/spaces/{key}/archive")
    public SpaceView archive(@PathVariable String key, @RequestParam(defaultValue = "true") boolean archived) {
        Space s = space(key);
        s.setArchived(archived);
        return view(s);
    }

    @GetMapping("/spaces/{key}")
    @Transactional(readOnly = true)
    public Overview overview(@PathVariable String key) {
        Space s = space(key);
        List<PageSummary> recent = pages.findBySpaceOrderByUpdatedAtDesc(s, PageRequest.of(0, 8)).stream().map(this::summary).toList();
        List<PageSummary> pinned = pages.findBySpaceAndPinnedTrueOrderByPositionAsc(s).stream().map(this::summary).toList();
        List<UserRef> contributors = users.findAllById(pages.contributorIds(s)).stream().map(UserRef::of).toList();
        return new Overview(view(s), tree(s), pinned, recent, contributors);
    }

    @GetMapping("/spaces/{key}/tree")
    @Transactional(readOnly = true)
    public List<TreeNode> treeOf(@PathVariable String key) {
        return tree(space(key));
    }

    @GetMapping("/spaces/{key}/blog")
    @Transactional(readOnly = true)
    public List<PageSummary> blog(@PathVariable String key) {
        return pages.findBySpaceAndKindOrderByPublishedAtDesc(space(key), "BLOG").stream()
                .filter(p -> "PUBLISHED".equals(p.getStatus())).map(this::summary).toList();
    }

    Space space(String key) {
        return spaces.findBySpaceKeyIgnoreCase(key).orElseThrow(() -> ApiException.notFound("Space " + key));
    }

    SpaceView view(Space s) {
        return new SpaceView(s.getId(), s.getSpaceKey(), s.getName(), s.getDescription(), s.getColor(), s.getType(),
                UserRef.of(s.getOwner()), s.getLinkedProject(), pages.countBySpace(s), s.isArchived());
    }

    private List<TreeNode> tree(Space s) {
        List<Page> all = pages.findBySpaceAndKindOrderByPositionAsc(s, "PAGE");
        Map<Long, List<Page>> byParent = new LinkedHashMap<>();
        for (Page p : all) byParent.computeIfAbsent(p.getParent() == null ? 0L : p.getParent().getId(), k -> new ArrayList<>()).add(p);
        return build(byParent, 0L, 0);
    }

    private List<TreeNode> build(Map<Long, List<Page>> byParent, Long parent, int depth) {
        if (depth > 20) return List.of();
        return byParent.getOrDefault(parent, List.of()).stream()
                .map(p -> new TreeNode(p.getId(), p.getTitle(), p.getStatus(), build(byParent, p.getId(), depth + 1))).toList();
    }

    // ================================================================== pages

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PageView create(@RequestBody PageRequest2 r) {
        User me = current.require();
        Space s = space(r.spaceKey());
        Page p = new Page();
        p.setSpace(s);
        p.setKind("BLOG".equals(r.kind()) ? "BLOG" : "PAGE");
        p.setTitle(r.title() == null || r.title().isBlank() ? "Untitled" : r.title().trim());
        if (r.parentId() != null) {
            Page parent = page(r.parentId());
            if (!parent.getSpace().getId().equals(s.getId())) throw ApiException.badRequest("Parent page is in another space.");
            p.setParent(parent);
        }
        p.setAuthor(me);
        p.setLastEditor(me);
        p.setPosition(pages.maxPosition(s) + 1000);
        p.setDraftBody(r.body() == null ? "" : r.body());
        p.setDraftTitle(p.getTitle());
        if (Boolean.TRUE.equals(r.publish())) {
            p.setBody(p.getDraftBody());
            publishInternal(p, me);
        } else {
            pages.save(p);
        }
        return view(p, me);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public PageView get(@PathVariable Long id) {
        User me = current.require();
        Page p = page(id);
        if ("DRAFT".equals(p.getStatus()) && (p.getAuthor() == null || !p.getAuthor().getId().equals(me.getId())) && !me.isAdmin()) {
            throw ApiException.notFound("Page");
        }
        return view(p, me);
    }

    /** Saves a draft, or publishes when {@code publish} is true. */
    @PutMapping("/{id}")
    public PageView save(@PathVariable Long id, @RequestBody PageRequest2 r) {
        User me = current.require();
        Page p = page(id);
        if (r.title() != null) p.setDraftTitle(r.title().isBlank() ? "Untitled" : r.title().trim());
        if (r.body() != null) p.setDraftBody(r.body());
        if (r.pinned() != null) p.setPinned(r.pinned());
        p.setLastEditor(me);
        p.setUpdatedAt(Instant.now());
        if (Boolean.TRUE.equals(r.publish())) {
            p.setTitle(p.getDraftTitle() == null ? p.getTitle() : p.getDraftTitle());
            p.setBody(p.getDraftBody() == null ? p.getBody() : p.getDraftBody());
            publishInternal(p, me);
        }
        return view(p, me);
    }

    @PostMapping("/{id}/discard")
    public PageView discard(@PathVariable Long id) {
        User me = current.require();
        Page p = page(id);
        if ("DRAFT".equals(p.getStatus())) throw ApiException.badRequest("This page has never been published; delete it instead.");
        p.setDraftBody(null);
        p.setDraftTitle(null);
        return view(p, me);
    }

    private void publishInternal(Page p, User me) {
        boolean first = !"PUBLISHED".equals(p.getStatus());
        p.setStatus("PUBLISHED");
        p.setVersion(p.getVersion() + 1);
        p.setPublishedAt(Instant.now());
        p.setDraftBody(null);
        p.setDraftTitle(null);
        p.setLastEditor(me);
        if (p.getAuthor() == null) p.setAuthor(me);
        pages.save(p);
        PageVersion v = new PageVersion();
        v.setPage(p);
        v.setVersion(p.getVersion());
        v.setTitle(p.getTitle());
        v.setBody(p.getBody());
        v.setAuthor(me);
        versions.save(v);
        Set<Long> notify = new LinkedHashSet<>();
        if (p.getAuthor() != null) notify.add(p.getAuthor().getId());
        events.publish(DomainEvent.of(first ? "page.published" : "page.updated", "pages", p.getKind().toLowerCase(Locale.ROOT),
                String.valueOf(p.getId()), p.getTitle(), link(p), UserRef.of(me),
                (first ? "published " : "updated ") + p.getTitle(), notify, Map.of("space", p.getSpace().getSpaceKey(), "version", p.getVersion(), "notifyKind", "watching")));
    }

    @PostMapping("/{id}/move")
    public List<TreeNode> move(@PathVariable Long id, @RequestBody MoveRequest r) {
        Page p = page(id);
        Page parent = r.parentId() == null ? null : page(r.parentId());
        for (Page a = parent; a != null; a = a.getParent()) {
            if (a.getId().equals(p.getId())) throw ApiException.badRequest("A page cannot move under itself.");
        }
        p.setParent(parent);
        if (r.beforeId() != null) {
            Page before = page(r.beforeId());
            p.setPosition(before.getPosition() - 0.5);
        }
        return tree(p.getSpace());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        User me = current.require();
        Page p = page(id);
        if (!me.isAdmin() && (p.getAuthor() == null || !p.getAuthor().getId().equals(me.getId()))) {
            throw ApiException.forbidden("Only the author or an administrator can delete a page.");
        }
        pages.findByParentOrderByPositionAsc(p).forEach(c -> c.setParent(p.getParent()));
        comments.deleteByPage(p);
        reactions.deleteByPage(p);
        versions.deleteByPage(p);
        pages.delete(p);
    }

    @GetMapping("/{id}/versions")
    @Transactional(readOnly = true)
    public List<VersionView> versions(@PathVariable Long id) {
        return versions.findByPageOrderByVersionDesc(page(id)).stream()
                .map(v -> new VersionView(v.getId(), v.getVersion(), v.getTitle(), UserRef.of(v.getAuthor()), v.getCreatedAt())).toList();
    }

    @PostMapping("/{id}/versions/{versionId}/restore")
    public PageView restore(@PathVariable Long id, @PathVariable Long versionId) {
        User me = current.require();
        Page p = page(id);
        PageVersion v = versions.findById(versionId).filter(x -> x.getPage().getId().equals(id)).orElseThrow(() -> ApiException.notFound("Version"));
        p.setTitle(v.getTitle());
        p.setBody(v.getBody());
        publishInternal(p, me);
        return view(p, me);
    }

    @PostMapping("/{id}/comments")
    public CommentView comment(@PathVariable Long id, @RequestBody Map<String, String> body) {
        User me = current.require();
        String text = body.get("body");
        if (text == null || text.isBlank()) throw ApiException.badRequest("Write a comment first.");
        Page p = page(id);
        PageComment c = new PageComment();
        c.setPage(p);
        c.setAuthor(me);
        c.setBody(text.trim());
        comments.save(c);
        List<Long> notify = p.getAuthor() == null ? List.of() : List.of(p.getAuthor().getId());
        events.publish(DomainEvent.of("page.commented", "pages", "page", String.valueOf(p.getId()), p.getTitle(), link(p), UserRef.of(me),
                "commented on " + p.getTitle(), notify, Map.of()));
        return new CommentView(c.getId(), UserRef.of(me), c.getBody(), c.getCreatedAt());
    }

    @DeleteMapping("/comments/{commentId}")
    public void deleteComment(@PathVariable Long commentId) {
        User me = current.require();
        PageComment c = comments.findById(commentId).orElseThrow(() -> ApiException.notFound("Comment"));
        if (!me.isAdmin() && (c.getAuthor() == null || !c.getAuthor().getId().equals(me.getId()))) throw ApiException.forbidden("You can only delete your own comments.");
        comments.delete(c);
    }

    @PostMapping("/{id}/reactions")
    public List<Reaction> react(@PathVariable Long id, @RequestBody Map<String, String> body) {
        User me = current.require();
        String emoji = body.get("emoji");
        if (emoji == null || emoji.isBlank() || emoji.length() > 16) throw ApiException.badRequest("Pick a reaction.");
        Page p = page(id);
        reactions.findByPageAndUserIdAndEmoji(p, me.getId(), emoji).ifPresentOrElse(reactions::delete, () -> {
            PageReaction r = new PageReaction();
            r.setPage(p);
            r.setUserId(me.getId());
            r.setEmoji(emoji);
            reactions.save(r);
        });
        reactions.flush();
        return reactionsOf(p, me);
    }

    Page page(Long id) {
        return pages.findById(id).orElseThrow(() -> ApiException.notFound("Page"));
    }

    private PageView view(Page p, User me) {
        List<Crumb> crumbs = new ArrayList<>();
        for (Page a = p.getParent(); a != null && crumbs.size() < 20; a = a.getParent()) crumbs.add(new Crumb(a.getId(), a.getTitle()));
        Collections.reverse(crumbs);
        List<CommentView> cs = comments.findByPageOrderByCreatedAtAsc(p).stream()
                .map(c -> new CommentView(c.getId(), UserRef.of(c.getAuthor()), c.getBody(), c.getCreatedAt())).toList();
        String b = p.getBody() == null ? "" : p.getBody();
        int words = b.isBlank() ? 0 : b.trim().split("\\s+").length;
        boolean hasDraft = p.getDraftBody() != null || p.getDraftTitle() != null;
        return new PageView(p.getId(), p.getTitle(), p.getBody(), p.getDraftTitle(), p.getDraftBody(), hasDraft, p.getKind(), p.getStatus(),
                p.getVersion(), UserRef.of(p.getAuthor()), UserRef.of(p.getLastEditor()), p.getCreatedAt(), p.getUpdatedAt(), p.getPublishedAt(),
                p.getParent() == null ? null : p.getParent().getId(), p.isPinned(), view(p.getSpace()), crumbs, reactionsOf(p, me), cs,
                Math.max(1, words / 220));
    }

    private List<Reaction> reactionsOf(Page p, User me) {
        Map<String, int[]> m = new LinkedHashMap<>();
        Map<String, Boolean> mine = new LinkedHashMap<>();
        for (PageReaction r : reactions.findByPage(p)) {
            m.computeIfAbsent(r.getEmoji(), k -> new int[1])[0]++;
            if (r.getUserId().equals(me.getId())) mine.put(r.getEmoji(), true);
        }
        return m.entrySet().stream().map(e -> new Reaction(e.getKey(), e.getValue()[0], mine.getOrDefault(e.getKey(), false))).toList();
    }

    private PageSummary summary(Page p) {
        return new PageSummary(p.getId(), p.getTitle(), p.getKind(), p.getStatus(), UserRef.of(p.getLastEditor()), p.getUpdatedAt(), excerpt(p.getBody()));
    }

    static String link(Page p) {
        return "/pages/" + p.getSpace().getSpaceKey() + "/" + p.getId();
    }

    static String excerpt(String body) {
        if (body == null) return null;
        String t = body.replaceAll("(?m)^#+\\s*", "").replaceAll("[*_`>|\\[\\]]", "").replaceAll("\\(/?[^)]*\\)", "").replaceAll("\\s+", " ").trim();
        return t.length() > 180 ? t.substring(0, 180) + "…" : t;
    }

    // ================================================================== hub

    @Override
    public String app() {
        return "pages";
    }

    @Override
    public List<WorkItem> search(String query, User user, int limit) {
        return pages.search(query, PageRequest.of(0, limit)).stream()
                .map(p -> WorkItem.of("pages", "page", p.getSpace().getSpaceKey(), p.getTitle(),
                        p.getSpace().getName() + " · updated by " + (p.getLastEditor() == null ? "someone" : p.getLastEditor().getDisplayName()),
                        null, null, link(p), p.getUpdatedAt()).withSnippet(snippetAround(p.getBody(), query)))
                .toList();
    }

    private static String snippetAround(String body, String q) {
        String t = excerpt(body == null ? "" : body.length() > 20000 ? body.substring(0, 20000) : body);
        if (t == null) return null;
        String plain = body == null ? "" : body.replaceAll("\\s+", " ");
        int i = plain.toLowerCase(Locale.ROOT).indexOf(q.toLowerCase(Locale.ROOT));
        if (i < 0) return t;
        int s = Math.max(0, i - 60);
        return (s > 0 ? "…" : "") + plain.substring(s, Math.min(plain.length(), i + 120)).replaceAll("[*_`#>|]", "") + "…";
    }
}
