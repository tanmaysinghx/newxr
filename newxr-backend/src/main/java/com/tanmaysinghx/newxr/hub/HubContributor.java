package com.tanmaysinghx.newxr.hub;

import com.tanmaysinghx.newxr.user.User;
import java.util.List;

/** Each app tells the hub what is assigned to someone, what is waiting on them, and what matches a search. */
public interface HubContributor {

    String app();

    default List<WorkItem> assignedTo(User user) {
        return List.of();
    }

    default List<WorkItem> waitingOn(User user) {
        return List.of();
    }

    default List<WorkItem> search(String query, User user, int limit) {
        return List.of();
    }
}
