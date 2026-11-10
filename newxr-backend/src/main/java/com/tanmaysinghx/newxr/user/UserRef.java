package com.tanmaysinghx.newxr.user;

/** How a person appears inside other objects (assignee, author, reporter...). */
public record UserRef(Long id, String name, String email, String initials, String color) {

    public static UserRef of(User u) {
        return u == null ? null : new UserRef(u.getId(), u.getDisplayName(), u.getEmail(), u.initials(), u.color());
    }
}
