package com.tanmaysinghx.newxr.track;

import com.tanmaysinghx.newxr.hub.HubContributor;
import com.tanmaysinghx.newxr.hub.WorkItem;
import com.tanmaysinghx.newxr.user.User;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class TrackHub implements HubContributor {

    private final IssueRepository issues;

    public TrackHub(IssueRepository issues) {
        this.issues = issues;
    }

    @Override
    public String app() {
        return "track";
    }

    @Override
    public List<WorkItem> assignedTo(User user) {
        return issues.findByAssigneeOrderByUpdatedAtDesc(user, PageRequest.of(0, 40)).stream().map(this::item).toList();
    }

    @Override
    public List<WorkItem> search(String query, User user, int limit) {
        return issues.search(query, query.trim().toUpperCase(java.util.Locale.ROOT), PageRequest.of(0, limit)).stream()
                .map(i -> item(i).withSnippet(snippet(i.getDescription()))).toList();
    }

    private WorkItem item(Issue i) {
        return WorkItem.of("track", i.getType().name(), i.getIssueKey(), i.getSummary(),
                i.getProject().getName() + (i.getAssignee() != null ? " · " + i.getAssignee().getDisplayName() : ""),
                i.getStatus().name(), i.getPriority().name(), TrackService.link(i), i.getUpdatedAt())
                .withDue(i.getDueDate() == null ? null : i.getDueDate().toString());
    }

    private static String snippet(String s) {
        if (s == null) return null;
        String t = s.replaceAll("[#*`>\\-\\[\\]]", "").replaceAll("\\s+", " ").trim();
        return t.length() > 160 ? t.substring(0, 160) + "…" : t;
    }
}
