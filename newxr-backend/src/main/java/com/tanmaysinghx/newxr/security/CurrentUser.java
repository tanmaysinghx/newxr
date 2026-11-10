package com.tanmaysinghx.newxr.security;

import com.tanmaysinghx.newxr.common.ApiException;
import com.tanmaysinghx.newxr.user.User;
import com.tanmaysinghx.newxr.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

    private final UserRepository users;

    public CurrentUser(UserRepository users) {
        this.users = users;
    }

    public NewxrPrincipal principal() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof NewxrPrincipal p) {
            return p;
        }
        throw new ApiException(HttpStatus.UNAUTHORIZED, "Sign in to continue.");
    }

    public Long id() {
        return principal().id();
    }

    /** The signed-in user, refreshed from the database (a deactivated account stops here). */
    public User require() {
        User u = users.findById(id()).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Sign in to continue."));
        if (!u.isActive()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "This account has been deactivated.");
        }
        Instant now = Instant.now();
        if (u.getLastActiveAt() == null || Duration.between(u.getLastActiveAt(), now).toMinutes() >= 5) {
            u.setLastActiveAt(now);
            users.save(u);
        }
        return u;
    }

    public void requireAdmin() {
        if (!"ADMIN".equals(principal().role())) {
            throw ApiException.forbidden("Only administrators can do that.");
        }
    }
}
