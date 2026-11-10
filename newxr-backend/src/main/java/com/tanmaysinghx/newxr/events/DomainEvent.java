package com.tanmaysinghx.newxr.events;

import com.tanmaysinghx.newxr.user.UserRef;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Something that happened, as published to Kafka ({@code <prefix>.events}, key = {@code entityKey})
 * and turned into notifications.
 *
 * @param type        dotted name, e.g. {@code issue.created}, {@code page.published}, {@code alert.raised}
 * @param app         track, pages, boards, code, desk, pulse, goals, admin
 * @param entityKey   human key (ORB-412, HELP-1093) or id
 * @param summary     notification line without the actor, e.g. "assigned you ORB-412 Offline mode"
 * @param recipients  user ids to notify (the actor is never notified of their own action)
 */
public record DomainEvent(
        String id,
        String type,
        String app,
        String entityType,
        String entityKey,
        String title,
        String link,
        UserRef actor,
        String summary,
        Set<Long> recipients,
        Map<String, Object> data,
        Instant occurredAt) {

    public static DomainEvent of(String type, String app, String entityType, String entityKey, String title, String link,
                                 UserRef actor, String summary, Collection<Long> recipients, Map<String, Object> data) {
        Set<Long> r = new LinkedHashSet<>();
        if (recipients != null) {
            recipients.stream().filter(id -> id != null).forEach(r::add);
        }
        return new DomainEvent(UUID.randomUUID().toString(), type, app, entityType, entityKey, title, link, actor, summary,
                r, data == null ? Map.of() : data, Instant.now());
    }
}
