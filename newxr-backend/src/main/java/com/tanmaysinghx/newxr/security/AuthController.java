package com.tanmaysinghx.newxr.security;

import com.tanmaysinghx.newxr.settings.SettingsService;
import com.tanmaysinghx.newxr.setup.SetupService;
import com.tanmaysinghx.newxr.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    public record SignInOptions(
            String organization, String logoUrl, String supportUrl, boolean setupRequired,
            boolean builtinEnabled, boolean portalEnabled, String portalButtonLabel, String ssoLoginPath) {}

    public record LoginRequest(@NotBlank(message = "Email is required") String email,
                               @NotBlank(message = "Password is required") String password) {}

    public record Me(Long id, String email, String name, String initials, String color, String role,
                     String signedInWith, String jobTitle, Set<String> apps) {}

    private final AuthService auth;
    private final SettingsService settings;
    private final SetupService setup;
    private final CurrentUser currentUser;
    private final SecurityContextRepository contextRepository;
    private final com.tanmaysinghx.newxr.admin.ProductService products;

    public AuthController(AuthService auth, SettingsService settings, SetupService setup, CurrentUser currentUser,
                          SecurityContextRepository contextRepository, com.tanmaysinghx.newxr.admin.ProductService products) {
        this.auth = auth;
        this.settings = settings;
        this.setup = setup;
        this.currentUser = currentUser;
        this.contextRepository = contextRepository;
        this.products = products;
    }

    @GetMapping("/api/public/auth/options")
    public SignInOptions options() {
        var org = settings.org();
        var s = settings.signIn();
        boolean portal = s.portalConfigured();
        return new SignInOptions(org.name(), org.logoUrl(), org.supportUrl(), setup.isSetupRequired(),
                s.builtinEnabled(), portal, s.portalButtonLabel(),
                portal ? "/oauth2/authorization/" + PortalClientRegistrations.REGISTRATION_ID : null);
    }

    @PostMapping("/api/public/auth/login")
    public Me login(@Valid @RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        User user = auth.login(body.email(), body.password());
        // New session id on sign-in (fixation); the CSRF token is rotated with it.
        request.getSession(true);
        request.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(NewxrPrincipal.authenticationFor(user, "password"));
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
        return me(user, "password");
    }

    @GetMapping("/api/auth/me")
    public Me me() {
        return me(currentUser.require(), currentUser.principal().via());
    }

    private Me me(User u, String via) {
        return new Me(u.getId(), u.getEmail(), u.getDisplayName(), u.initials(), u.color(), u.getRole().name(), via,
                u.getJobTitle(), products.appsFor(u));
    }
}
