package com.tanmaysinghx.newxr.notify;

import com.tanmaysinghx.newxr.common.ApiException;
import com.tanmaysinghx.newxr.events.DomainEvent;
import com.tanmaysinghx.newxr.security.CurrentUser;
import com.tanmaysinghx.newxr.user.UserRef;
import com.tanmaysinghx.newxr.user.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** In-app notifications: written from domain events, read by the bell in the top nav. */
@RestController
public class NotificationService {

    public record NotificationView(Long id, UserRef actor, String app, String kind, String text, String link,
                                   boolean read, Instant at) {}

    private final NotificationRepository repo;
    private final UserRepository users;
    private final CurrentUser current;

    public NotificationService(NotificationRepository repo, UserRepository users, CurrentUser current) {
        this.repo = repo;
        this.users = users;
        this.current = current;
    }

    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onEvent(DomainEvent e) {
        Long actorId = e.actor() == null ? null : e.actor().id();
        for (Long recipient : e.recipients()) {
            if (recipient.equals(actorId)) {
                continue;
            }
            Notification n = new Notification();
            n.setRecipientId(recipient);
            n.setActor(actorId == null ? null : users.getReferenceById(actorId));
            n.setApp(e.app());
            n.setKind(String.valueOf(e.data().getOrDefault("notifyKind", "direct")));
            n.setText(trim((e.actor() == null ? "Newxr" : e.actor().name()) + " " + e.summary(), 500));
            n.setLink(e.link());
            repo.save(n);
        }
    }

    @GetMapping("/api/notifications")
    @Transactional(readOnly = true)
    public Map<String, Object> list(@RequestParam(defaultValue = "false") boolean unreadOnly) {
        Long me = current.id();
        List<NotificationView> items = repo.findByRecipientIdOrderByCreatedAtDesc(me, PageRequest.of(0, 60)).stream()
                .filter(n -> !unreadOnly || !n.isReadFlag())
                .map(n -> new NotificationView(n.getId(), UserRef.of(n.getActor()), n.getApp(), n.getKind(), n.getText(),
                        n.getLink(), n.isReadFlag(), n.getCreatedAt()))
                .toList();
        return Map.of("items", items, "unread", repo.countByRecipientIdAndReadFlagFalse(me));
    }

    @PostMapping("/api/notifications/{id}/read")
    @Transactional
    public void read(@PathVariable Long id) {
        Notification n = repo.findById(id).filter(x -> x.getRecipientId().equals(current.id()))
                .orElseThrow(() -> ApiException.notFound("Notification"));
        n.setReadFlag(true);
    }

    @PostMapping("/api/notifications/read-all")
    @Transactional
    public void readAll() {
        repo.markAllRead(current.id());
    }

    private static String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
