package com.tanmaysinghx.newxr.user;

import com.tanmaysinghx.newxr.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    private static final String[] COLORS = {"#5a4bf5", "#0e9f8e", "#e8892a", "#2f6fde", "#d9466f", "#e5533d", "#2f9a57", "#7c3aed"};

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(nullable = false, length = 120)
    private String displayName;

    /** Null for accounts that only sign in through Portal SSO. */
    @Column(length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role = Role.MEMBER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AuthSource source = AuthSource.LOCAL;

    /** Subject from Portal SSO, once the account has signed in there. */
    @Column(length = 255)
    private String externalSubject;

    private boolean active = true;
    private boolean invited;
    private int failedLogins;
    private Instant lockedUntil;
    private Instant lastActiveAt;

    @Column(length = 120)
    private String jobTitle;

    @Column(length = 9)
    private String avatarColor;

    /** Comma-separated app codes this user may open; null means every enabled app. */
    @Column(length = 200)
    private String appAccess;

    public String initials() {
        String[] parts = displayName.trim().split("\\s+");
        String i = parts.length == 1
                ? parts[0].substring(0, Math.min(2, parts[0].length()))
                : "" + parts[0].charAt(0) + parts[parts.length - 1].charAt(0);
        return i.toUpperCase();
    }

    public String color() {
        return avatarColor != null ? avatarColor : COLORS[Math.floorMod(email.hashCode(), COLORS.length)];
    }

    public Set<String> apps() {
        return appAccess == null || appAccess.isBlank() ? null : new LinkedHashSet<>(Arrays.asList(appAccess.split(",")));
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
