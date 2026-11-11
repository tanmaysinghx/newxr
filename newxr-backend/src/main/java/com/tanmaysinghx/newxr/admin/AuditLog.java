package com.tanmaysinghx.newxr.admin;

import com.tanmaysinghx.newxr.user.User;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Writes audit events in their own transaction, so a failed sign-in or a rolled-back change is still recorded. */
@Service
public class AuditLog {

    private static final Logger log = LoggerFactory.getLogger(AuditLog.class);
    private final AuditEventRepository events;

    public AuditLog(AuditEventRepository events) {
        this.events = events;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(User actor, String action, String target, String detail) {
        record(actor == null ? null : actor.getId(), actor == null ? null : actor.getDisplayName(), actor == null ? null : actor.getEmail(),
                action, target, detail);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long actorId, String actorName, String actorEmail, String action, String target, String detail) {
        try {
            AuditEvent e = new AuditEvent();
            e.setActorId(actorId);
            e.setActorName(cut(actorName, 120));
            e.setActorEmail(cut(actorEmail, 254));
            e.setAction(cut(action, 60));
            e.setTarget(cut(target, 200));
            e.setDetail(cut(detail, 500));
            e.setIp(cut(clientIp(), 64));
            events.save(e);
        } catch (RuntimeException ex) {
            log.warn("Could not write audit event {}: {}", action, ex.getMessage());
        }
    }

    /** Remote address as seen by the servlet container (behind a proxy, configure forwarded headers). */
    private static String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes a) {
            HttpServletRequest r = a.getRequest();
            return r.getRemoteAddr();
        }
        return null;
    }

    private static String cut(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
