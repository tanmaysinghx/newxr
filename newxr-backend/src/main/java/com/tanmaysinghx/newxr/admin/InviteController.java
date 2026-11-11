package com.tanmaysinghx.newxr.admin;

import com.tanmaysinghx.newxr.common.ApiException;
import com.tanmaysinghx.newxr.settings.SettingsService;
import com.tanmaysinghx.newxr.user.PasswordPolicy;
import com.tanmaysinghx.newxr.user.User;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Accepting an invitation or a reset link: the person chooses a password, then signs in normally. */
@RestController
@RequestMapping("/api/public/invite/{token}")
@Transactional
public class InviteController {

    public record InviteView(String email, String name, String purpose, String organization, String passwordPolicy, boolean portalEnabled) {}

    public record AcceptRequest(String password) {}

    private final UserTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final SettingsService settings;
    private final AuditLog audit;

    public InviteController(UserTokenRepository tokens, PasswordEncoder encoder, SettingsService settings, AuditLog audit) {
        this.tokens = tokens;
        this.encoder = encoder;
        this.settings = settings;
        this.audit = audit;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public InviteView get(@PathVariable String token) {
        UserToken t = valid(token);
        User u = t.getUser();
        return new InviteView(u.getEmail(), u.getDisplayName(), t.getPurpose(), settings.org().name(), PasswordPolicy.DESCRIPTION,
                settings.signIn().portalConfigured());
    }

    @PostMapping
    public java.util.Map<String, String> accept(@PathVariable String token, @RequestBody AcceptRequest r) {
        UserToken t = valid(token);
        User u = t.getUser();
        if (!settings.signIn().builtinEnabled() && !u.isAdmin()) {
            throw ApiException.badRequest("Password sign-in is turned off here. Sign in with " + settings.signIn().portalButtonLabel().replace("Continue with ", "") + " instead.");
        }
        PasswordPolicy.check(r.password());
        u.setPasswordHash(encoder.encode(r.password()));
        u.setInvited(false);
        u.setFailedLogins(0);
        u.setLockedUntil(null);
        t.setUsedAt(Instant.now());
        audit.record(u, "INVITE".equals(t.getPurpose()) ? "user.invite-accepted" : "user.password-reset", u.getEmail(), null);
        return java.util.Map.of("email", u.getEmail());
    }

    private UserToken valid(String token) {
        UserToken t = tokens.findByTokenHash(AdminService.hash(token)).orElse(null);
        if (t == null || t.getUsedAt() != null || t.getExpiresAt().isBefore(Instant.now()) || !t.getUser().isActive()) {
            throw new ApiException(HttpStatus.GONE, "This link has expired or was already used. Ask an administrator for a new one.");
        }
        return t;
    }
}
