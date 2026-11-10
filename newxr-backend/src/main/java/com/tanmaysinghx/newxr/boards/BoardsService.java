package com.tanmaysinghx.newxr.boards;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Boards: boards, lists, cards (Trello-style) and its hub contributor. */
@RestController
@RequestMapping("/api/boards")
@Transactional
public class BoardsService implements HubContributor {

    public record BoardSummary(Long id, String name, String description, String background, String visibility, UserRef owner,
                               List<UserRef> members, long cardCount) {}

    public record CardSummary(Long id, Long listId, String title, List<String> labels, String cover, Instant due, boolean dueDone,
                              List<UserRef> members, int checkDone, int checkTotal, long comments, boolean hasDescription, double position) {}

    public record ListView(Long id, String name, double position, List<CardSummary> cards) {}

    public record BoardView(BoardSummary board, List<ListView> lists) {}

    public record CommentView(Long id, UserRef author, String body, Instant createdAt) {}

    public record CardView(Long id, Long boardId, String boardName, Long listId, String listName, String title, String description,
                           List<String> labels, String cover, Instant due, boolean dueDone, List<UserRef> members, List<CardCheck> checklist,
                           String linkedIssue, Long linkedPage, List<CommentView> comments, Instant createdAt) {}

    public record BoardRequest(String name, String description, String background, String visibility) {}

    public record CardRequest(Long listId, String title, String description, List<String> labels, String cover, Instant due, Boolean dueDone,
                              List<Long> memberIds, List<CardCheck> checklist, String linkedIssue, Long linkedPage, List<String> clear) {}

    public record MoveCard(Long listId, Long beforeId, Long afterId) {}

    private final BoardRepository boards;
    private final BoardListRepository lists;
    private final CardRepository cards;
    private final CardCommentRepository comments;
    private final UserRepository users;
    private final CurrentUser current;
    private final EventBus events;

    public BoardsService(BoardRepository boards, BoardListRepository lists, CardRepository cards, CardCommentRepository comments,
                         UserRepository users, CurrentUser current, EventBus events) {
        this.boards = boards;
        this.lists = lists;
        this.cards = cards;
        this.comments = comments;
        this.users = users;
        this.current = current;
        this.events = events;
    }

    // ---- boards -------------------------------------------------------------------

    @GetMapping
    @Transactional(readOnly = true)
    public List<BoardSummary> list() {
        User me = current.require();
        return boards.findByArchivedFalseOrderByNameAsc().stream().filter(b -> canSee(b, me)).map(this::summary).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BoardSummary create(@RequestBody BoardRequest r) {
        User me = current.require();
        if (r.name() == null || r.name().isBlank()) throw ApiException.badRequest("Enter a board name.");
        Board b = new Board();
        b.setName(r.name().trim());
        b.setDescription(r.description());
        if (r.background() != null) b.setBackground(r.background());
        if (r.visibility() != null) b.setVisibility(r.visibility());
        b.setOwner(me);
        b.getMembers().add(me);
        b = boards.save(b);
        for (String n : List.of("To do", "Doing", "Done")) addList(b, n);
        return summary(b);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public BoardView get(@PathVariable Long id) {
        Board b = board(id);
        List<ListView> ls = lists.findByBoardAndArchivedFalseOrderByPositionAsc(b).stream()
                .map(l -> new ListView(l.getId(), l.getName(), l.getPosition(),
                        cards.findByListAndArchivedFalseOrderByPositionAsc(l).stream().map(this::summary).toList())).toList();
        return new BoardView(summary(b), ls);
    }

    @PutMapping("/{id}")
    public BoardSummary update(@PathVariable Long id, @RequestBody BoardRequest r) {
        Board b = board(id);
        if (r.name() != null && !r.name().isBlank()) b.setName(r.name().trim());
        if (r.description() != null) b.setDescription(r.description());
        if (r.background() != null) b.setBackground(r.background());
        if (r.visibility() != null) b.setVisibility(r.visibility());
        return summary(b);
    }

    @PostMapping("/{id}/members/{userId}")
    public BoardSummary addMember(@PathVariable Long id, @PathVariable Long userId) {
        Board b = board(id);
        b.getMembers().add(users.findById(userId).orElseThrow(() -> ApiException.notFound("User")));
        return summary(b);
    }

    @DeleteMapping("/{id}")
    public void archiveBoard(@PathVariable Long id) {
        board(id).setArchived(true);
    }

    // ---- lists --------------------------------------------------------------------

    @PostMapping("/{id}/lists")
    public ListView createList(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String name = body.get("name");
        if (name == null || name.isBlank()) throw ApiException.badRequest("Enter a list name.");
        BoardList l = addList(board(id), name.trim());
        return new ListView(l.getId(), l.getName(), l.getPosition(), List.of());
    }

    @PutMapping("/lists/{listId}")
    public void renameList(@PathVariable Long listId, @RequestBody Map<String, Object> body) {
        BoardList l = lists.findById(listId).orElseThrow(() -> ApiException.notFound("List"));
        if (body.get("name") instanceof String n && !n.isBlank()) l.setName(n.trim());
        if (body.get("position") instanceof Number p) l.setPosition(p.doubleValue());
    }

    @DeleteMapping("/lists/{listId}")
    public void archiveList(@PathVariable Long listId) {
        lists.findById(listId).orElseThrow(() -> ApiException.notFound("List")).setArchived(true);
    }

    private BoardList addList(Board b, String name) {
        BoardList l = new BoardList();
        l.setBoard(b);
        l.setName(name);
        l.setPosition(lists.findByBoardAndArchivedFalseOrderByPositionAsc(b).stream().mapToDouble(BoardList::getPosition).max().orElse(0) + 1000);
        return lists.save(l);
    }

    // ---- cards --------------------------------------------------------------------

    @PostMapping("/cards")
    @ResponseStatus(HttpStatus.CREATED)
    public CardSummary createCard(@RequestBody CardRequest r) {
        User me = current.require();
        if (r.title() == null || r.title().isBlank()) throw ApiException.badRequest("Enter a title for this card.");
        BoardList l = lists.findById(r.listId()).orElseThrow(() -> ApiException.notFound("List"));
        Card c = new Card();
        c.setList(l);
        c.setTitle(r.title().trim());
        c.setPosition(cards.maxPosition(l) + 1000);
        c = cards.save(c);
        events.publish(DomainEvent.of("card.created", "boards", "card", String.valueOf(c.getId()), c.getTitle(), link(c),
                UserRef.of(me), "added " + c.getTitle() + " to " + l.getBoard().getName(), List.of(), Map.of("board", l.getBoard().getName())));
        return summary(c);
    }

    @GetMapping("/cards/{cardId}")
    @Transactional(readOnly = true)
    public CardView card(@PathVariable Long cardId) {
        return view(card0(cardId));
    }

    @PatchMapping("/cards/{cardId}")
    public CardView updateCard(@PathVariable Long cardId, @RequestBody CardRequest r) {
        User me = current.require();
        Card c = card0(cardId);
        List<String> clear = r.clear() == null ? List.of() : r.clear();
        Set<Long> before = c.getMembers().stream().map(User::getId).collect(Collectors.toSet());
        if (r.title() != null && !r.title().isBlank()) c.setTitle(r.title().trim());
        if (r.description() != null) c.setDescription(r.description());
        if (r.labels() != null) c.setLabels(r.labels().stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty())
                .map(s -> s.replace(",", "")).distinct().collect(Collectors.joining(",")));
        if (clear.contains("cover")) c.setCover(null); else if (r.cover() != null) c.setCover(r.cover());
        if (clear.contains("due")) c.setDue(null); else if (r.due() != null) c.setDue(r.due());
        if (r.dueDone() != null) c.setDueDone(r.dueDone());
        if (r.memberIds() != null) {
            c.getMembers().clear();
            c.getMembers().addAll(users.findAllById(r.memberIds()));
        }
        if (r.checklist() != null) {
            c.getChecklist().clear();
            r.checklist().stream().filter(x -> x.getText() != null && !x.getText().isBlank()).forEach(c.getChecklist()::add);
        }
        if (clear.contains("linkedIssue")) c.setLinkedIssue(null); else if (r.linkedIssue() != null) c.setLinkedIssue(r.linkedIssue().trim().toUpperCase());
        if (clear.contains("linkedPage")) c.setLinkedPage(null); else if (r.linkedPage() != null) c.setLinkedPage(r.linkedPage());
        c.setUpdatedAt(Instant.now());
        Set<Long> added = c.getMembers().stream().map(User::getId).filter(id -> !before.contains(id)).collect(Collectors.toCollection(LinkedHashSet::new));
        if (!added.isEmpty()) {
            events.publish(DomainEvent.of("card.assigned", "boards", "card", String.valueOf(c.getId()), c.getTitle(), link(c),
                    UserRef.of(me), "added you to " + c.getTitle(), added, Map.of()));
        }
        return view(c);
    }

    @PostMapping("/cards/{cardId}/move")
    public CardSummary move(@PathVariable Long cardId, @RequestBody MoveCard r) {
        Card c = card0(cardId);
        if (r.listId() != null && !r.listId().equals(c.getList().getId())) {
            c.setList(lists.findById(r.listId()).orElseThrow(() -> ApiException.notFound("List")));
        }
        Double b = r.beforeId() == null ? null : cards.findById(r.beforeId()).map(Card::getPosition).orElse(null);
        Double a = r.afterId() == null ? null : cards.findById(r.afterId()).map(Card::getPosition).orElse(null);
        if (b != null && a != null) c.setPosition((a + b) / 2);
        else if (b != null) c.setPosition(b + 1000);
        else if (a != null) c.setPosition(a - 1000);
        else c.setPosition(cards.maxPosition(c.getList()) + 1000);
        return summary(c);
    }

    @DeleteMapping("/cards/{cardId}")
    public void archiveCard(@PathVariable Long cardId) {
        card0(cardId).setArchived(true);
    }

    @PostMapping("/cards/{cardId}/comments")
    public CommentView comment(@PathVariable Long cardId, @RequestBody Map<String, String> body) {
        User me = current.require();
        String text = body.get("body");
        if (text == null || text.isBlank()) throw ApiException.badRequest("Write a comment first.");
        Card c = card0(cardId);
        CardComment cc = new CardComment();
        cc.setCard(c);
        cc.setAuthor(me);
        cc.setBody(text.trim());
        comments.save(cc);
        events.publish(DomainEvent.of("card.commented", "boards", "card", String.valueOf(c.getId()), c.getTitle(), link(c), UserRef.of(me),
                "commented on " + c.getTitle(), c.getMembers().stream().map(User::getId).toList(), Map.of("notifyKind", "watching")));
        return new CommentView(cc.getId(), UserRef.of(me), cc.getBody(), cc.getCreatedAt());
    }

    // ---- helpers ------------------------------------------------------------------

    Board board(Long id) {
        Board b = boards.findById(id).orElseThrow(() -> ApiException.notFound("Board"));
        if (!canSee(b, current.require())) throw ApiException.notFound("Board");
        return b;
    }

    Card card0(Long id) {
        Card c = cards.findById(id).orElseThrow(() -> ApiException.notFound("Card"));
        board(c.getList().getBoard().getId());
        return c;
    }

    private static boolean canSee(Board b, User me) {
        return !"PRIVATE".equals(b.getVisibility()) || me.isAdmin() || b.getMembers().stream().anyMatch(u -> u.getId().equals(me.getId()));
    }

    private BoardSummary summary(Board b) {
        return new BoardSummary(b.getId(), b.getName(), b.getDescription(), b.getBackground(), b.getVisibility(), UserRef.of(b.getOwner()),
                b.getMembers().stream().map(UserRef::of).toList(), cards.countByList_Board(b));
    }

    private CardSummary summary(Card c) {
        int done = (int) c.getChecklist().stream().filter(CardCheck::isDone).count();
        return new CardSummary(c.getId(), c.getList().getId(), c.getTitle(), c.labelList(), c.getCover(), c.getDue(), c.isDueDone(),
                c.getMembers().stream().map(UserRef::of).toList(), done, c.getChecklist().size(), comments.countByCard(c),
                c.getDescription() != null && !c.getDescription().isBlank(), c.getPosition());
    }

    private CardView view(Card c) {
        Board b = c.getList().getBoard();
        return new CardView(c.getId(), b.getId(), b.getName(), c.getList().getId(), c.getList().getName(), c.getTitle(), c.getDescription(),
                c.labelList(), c.getCover(), c.getDue(), c.isDueDone(), c.getMembers().stream().map(UserRef::of).toList(),
                List.copyOf(c.getChecklist()), c.getLinkedIssue(), c.getLinkedPage(),
                comments.findByCardOrderByCreatedAtDesc(c).stream().map(x -> new CommentView(x.getId(), UserRef.of(x.getAuthor()), x.getBody(), x.getCreatedAt())).toList(),
                c.getCreatedAt());
    }

    static String link(Card c) {
        return "/boards/" + c.getList().getBoard().getId() + "?card=" + c.getId();
    }

    // ---- hub ----------------------------------------------------------------------

    @Override
    public String app() {
        return "boards";
    }

    @Override
    public List<WorkItem> assignedTo(User user) {
        return cards.assignedTo(user, PageRequest.of(0, 20)).stream()
                .map(c -> WorkItem.of("boards", "card", null, c.getTitle(), c.getList().getBoard().getName() + " · " + c.getList().getName(),
                        "Done".equalsIgnoreCase(c.getList().getName()) ? "DONE" : null, null, link(c), c.getUpdatedAt())
                        .withDue(c.getDue() == null ? null : c.getDue().toString()))
                .toList();
    }

    @Override
    public List<WorkItem> search(String q, User user, int limit) {
        return cards.search(q, PageRequest.of(0, limit)).stream().filter(c -> canSee(c.getList().getBoard(), user))
                .map(c -> WorkItem.of("boards", "card", null, c.getTitle(), c.getList().getBoard().getName() + " · " + c.getList().getName(),
                        null, null, link(c), c.getUpdatedAt()))
                .toList();
    }
}
