package com.tanmaysinghx.newxr.hub;

import java.time.Instant;

/**
 * One row on the home page or in search results, from any app.
 *
 * @param kind    issue type, "page", "pr", "request", "alert", "goal", "card", "repo", "person"...
 * @param status  raw status (IN_PROGRESS, WAITING_FOR_SUPPORT...) rendered as a lozenge
 * @param due     optional due date (ISO) for "Due soon"
 */
public record WorkItem(String app, String kind, String key, String title, String subtitle, String status,
                       String priority, String link, Instant updatedAt, String due, String snippet) {

    public static WorkItem of(String app, String kind, String key, String title, String subtitle, String status,
                              String priority, String link, Instant updatedAt) {
        return new WorkItem(app, kind, key, title, subtitle, status, priority, link, updatedAt, null, null);
    }

    public WorkItem withDue(String d) {
        return new WorkItem(app, kind, key, title, subtitle, status, priority, link, updatedAt, d, snippet);
    }

    public WorkItem withSnippet(String s) {
        return new WorkItem(app, kind, key, title, subtitle, status, priority, link, updatedAt, due, s);
    }
}
