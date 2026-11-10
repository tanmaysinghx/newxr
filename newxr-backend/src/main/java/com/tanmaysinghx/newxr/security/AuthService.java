package com.tanmaysinghx.newxr.security;

import com.tanmaysinghx.newxr.common.ApiException;
import com.tanmaysinghx.newxr.settings.SettingsService;
import com.tanmaysinghx.newxr.user.User;
import com.tanmaysinghx.newxr.user.UserRepository;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Built-in (password) sign-in with lockout. */
@Service
public class AuthService {

    private static final String BAD_CREDENTIALS = "Incorrect email or password.";
    // Compared against when the email is unknown, so timing does not reveal which emails exist.
    private static final String DUMMY_HASH = "{bcrypt}$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5BWX4Z6r1nG9b/8wQ0gQ6ZzW6r1vS";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final SettingsService settings;
    private final int maxFailed;
    private final Duration lockout;

    public AuthService(UserRepository users, PasswordEncoder encoder, SettingsService settings,
                       @Value("${newxr.security.max-failed-logins:5}") int maxFailed,
                       @Value("${newxr.security.lockout-duration:15m}") Duration lockout) {
        this.users = users;
        this.encoder = encoder;
        this.settings = settings;
        this.maxFailed = maxFailed;
        this.lockout = lockout;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public User login(String email, String password) {
        User user = users.findByEmailIgnoreCase(email == null ? "" : email.trim()).orElse(null);
        if (user == null || user.getPasswordHash() == null) {
            encoder.matches(password == null ? "" : password, DUMMY_HASH);
            throw new ApiException(HttpStatus.UNAUTHORIZED, BAD_CREDENTIALS);
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.LOCKED, "Too many failed attempts. Try again in a few minutes.");
        }
        if (!encoder.matches(password == null ? "" : password, user.getPasswordHash())) {
            user.setFailedLogins(user.getFailedLogins() + 1);
            if (user.getFailedLogins() >= maxFailed) {
                user.setLockedUntil(Instant.now().plus(lockout));
                user.setFailedLogins(0);
            }
            users.save(user);
            throw new ApiException(HttpStatus.UNAUTHORIZED, BAD_CREDENTIALS);
        }
        if (!user.isActive()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "This account has been deactivated.");
        }
        // With built-in sign-in turned off, administrators keep a password as a way back in
        // when Portal SSO is unavailable or misconfigured.
        if (!settings.signIn().builtinEnabled() && !user.isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Sign in with " + settings.signIn().portalButtonLabel().replace("Continue with ", "") + ".");
        }
        user.setFailedLogins(0);
        user.setLockedUntil(null);
        user.setInvited(false);
        user.setLastActiveAt(Instant.now());
        return users.save(user);
    }
}
