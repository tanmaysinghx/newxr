package com.tanmaysinghx.newxr.security;

import com.tanmaysinghx.newxr.settings.SettingsService;
import com.tanmaysinghx.newxr.user.AuthSource;
import com.tanmaysinghx.newxr.user.Role;
import com.tanmaysinghx.newxr.user.User;
import com.tanmaysinghx.newxr.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns a verified Portal SSO sign-in into a Newxr session. The account is matched on the
 * Portal subject, then on email (verified by Portal), and created on first sign-in when
 * auto-provisioning is on. The OAuth token is dropped: Newxr only needs to know who it is.
 */
@Component
public class PortalLoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger log = LoggerFactory.getLogger(PortalLoginSuccessHandler.class);
    private final UserRepository users;
    private final SettingsService settings;
    private final com.tanmaysinghx.newxr.admin.AuditLog audit;
    private final HttpSessionSecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public PortalLoginSuccessHandler(UserRepository users, SettingsService settings, com.tanmaysinghx.newxr.admin.AuditLog audit) {
        this.audit = audit;
        this.users = users;
        this.settings = settings;
    }

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        if (!(authentication.getPrincipal() instanceof OidcUser oidc) || oidc.getEmail() == null) {
            fail(request, response, "sso_no_email");
            return;
        }
        String email = oidc.getEmail().trim().toLowerCase(Locale.ROOT);
        User user = users.findByExternalSubject(oidc.getSubject())
                .or(() -> users.findByEmailIgnoreCase(email))
                .orElse(null);
        if (user == null) {
            if (!settings.signIn().autoProvision()) {
                fail(request, response, "sso_not_invited");
                return;
            }
            user = new User();
            user.setEmail(email);
            user.setRole(Role.MEMBER);
            user.setSource(AuthSource.PORTAL);
            log.info("Created account {} on first Portal SSO sign-in", email);
        }
        if (!user.isActive()) {
            fail(request, response, "account_disabled");
            return;
        }
        String name = oidc.getFullName() != null ? oidc.getFullName()
                : (oidc.getGivenName() != null ? (oidc.getGivenName() + " " + nz(oidc.getFamilyName())).trim() : null);
        if (name != null && !name.isBlank()) {
            user.setDisplayName(name);
        } else if (user.getDisplayName() == null) {
            user.setDisplayName(email.substring(0, email.indexOf('@')));
        }
        user.setExternalSubject(oidc.getSubject());
        user.setInvited(false);
        user.setFailedLogins(0);
        user.setLastActiveAt(Instant.now());
        user = users.save(user);
        audit.record(user, "signin.portal", user.getEmail(), null);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(NewxrPrincipal.authenticationFor(user, "portal"));
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        response.sendRedirect(request.getContextPath() + "/");
    }

    private static void fail(HttpServletRequest request, HttpServletResponse response, String code) throws IOException {
        SecurityContextHolder.clearContext();
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        response.sendRedirect(request.getContextPath() + "/login?error=" + code);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
