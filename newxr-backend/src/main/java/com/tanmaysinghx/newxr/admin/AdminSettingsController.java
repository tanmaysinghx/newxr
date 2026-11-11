package com.tanmaysinghx.newxr.admin;

import com.tanmaysinghx.newxr.common.ApiException;
import com.tanmaysinghx.newxr.security.CurrentUser;
import com.tanmaysinghx.newxr.settings.SettingsService;
import com.tanmaysinghx.newxr.settings.SettingsService.KafkaSettings;
import com.tanmaysinghx.newxr.settings.SettingsService.OrgSettings;
import com.tanmaysinghx.newxr.settings.SettingsService.SignInSettings;
import com.tanmaysinghx.newxr.setup.DatabaseConnections;
import com.tanmaysinghx.newxr.setup.KafkaConnections;
import com.tanmaysinghx.newxr.setup.PortalDiscovery;
import com.tanmaysinghx.newxr.user.User;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Organization, sign-in, database and Kafka settings after setup. Secrets are never returned, only whether they are set. */
@RestController
@RequestMapping("/api/admin/settings")
public class AdminSettingsController {

    public record SignInView(SignInSettings settings, String redirectUri) {}

    /** {@code clientSecret}: null keeps the stored one, "" clears it. */
    public record SignInRequest(boolean builtinEnabled, boolean portalEnabled, String issuerUrl, String clientId, String clientSecret,
                                String buttonLabel, boolean autoProvision) {}

    /** {@code password}: null keeps the stored one, "" clears it. */
    public record KafkaRequest(boolean enabled, String bootstrapServers, String topicPrefix, String securityProtocol, String saslMechanism,
                               String username, String password) {}

    public record IssuerRequest(String issuerUrl) {}

    private final SettingsService settings;
    private final DatabaseConnections databases;
    private final KafkaConnections kafka;
    private final PortalDiscovery portal;
    private final AuditLog audit;
    private final CurrentUser current;
    private final Environment env;

    public AdminSettingsController(SettingsService settings, DatabaseConnections databases, KafkaConnections kafka, PortalDiscovery portal,
                                   AuditLog audit, CurrentUser current, Environment env) {
        this.settings = settings;
        this.databases = databases;
        this.kafka = kafka;
        this.portal = portal;
        this.audit = audit;
        this.current = current;
        this.env = env;
    }

    @GetMapping("/org")
    public OrgSettings org() {
        return settings.org();
    }

    @PutMapping("/org")
    public OrgSettings saveOrg(@RequestBody OrgSettings r) {
        User me = current.require();
        if (r.logoUrl() != null && !r.logoUrl().isBlank() && !r.logoUrl().matches("(https?://|/).*")) throw ApiException.badRequest("The logo URL must start with https://.");
        if (r.supportUrl() != null && !r.supportUrl().isBlank() && !r.supportUrl().matches("(https?://|mailto:).*")) throw ApiException.badRequest("The support link must be a web address or mailto:.");
        settings.saveOrg(r);
        audit.record(me, "settings.organization", settings.org().name(), null);
        return settings.org();
    }

    @GetMapping("/sign-in")
    public SignInView signIn() {
        return new SignInView(settings.signIn(), publicUrl() + "/login/oauth2/code/portal");
    }

    @PutMapping("/sign-in")
    public SignInView saveSignIn(@RequestBody SignInRequest r) {
        User me = current.require();
        if (!r.builtinEnabled() && !r.portalEnabled()) throw ApiException.badRequest("Keep at least one way to sign in.");
        if (r.portalEnabled()) {
            if (blank(r.issuerUrl()) || blank(r.clientId())) throw ApiException.badRequest("Portal SSO needs an issuer URL and a client ID.");
            boolean secretAfter = r.clientSecret() == null ? settings.signIn().portalClientSecretSet() : !r.clientSecret().isEmpty();
            if (!secretAfter) throw ApiException.badRequest("Portal SSO needs the client secret.");
        }
        settings.saveSignIn(r.builtinEnabled(), r.portalEnabled(), r.issuerUrl(), r.clientId(), r.clientSecret(), r.buttonLabel(), r.autoProvision());
        audit.record(me, "settings.sign-in", null, (r.builtinEnabled() ? "built-in on" : "built-in off") + ", "
                + (r.portalEnabled() ? "Portal SSO on" : "Portal SSO off") + (r.clientSecret() != null ? ", client secret changed" : ""));
        return signIn();
    }

    @PostMapping("/sign-in/test")
    public PortalDiscovery.TestResult testPortal(@RequestBody IssuerRequest r) {
        return portal.test(r.issuerUrl());
    }

    /** Current connection. Switching databases needs a data copy, which arrives later; for now this tests a target. */
    @GetMapping("/database")
    public DatabaseConnections.Status database() {
        return databases.status();
    }

    @PostMapping("/database/test")
    public DatabaseConnections.TestResult testDatabase(@RequestBody DatabaseConnections.DatabaseRequest r) {
        return databases.test(r);
    }

    @GetMapping("/kafka")
    public KafkaSettings kafkaSettings() {
        return settings.kafka();
    }

    @PutMapping("/kafka")
    public KafkaSettings saveKafka(@RequestBody KafkaRequest r) {
        User me = current.require();
        if (r.enabled() && blank(r.bootstrapServers())) throw ApiException.badRequest("Enter the bootstrap servers.");
        settings.saveKafka(new KafkaSettings(r.enabled(), r.bootstrapServers(), r.topicPrefix(), r.securityProtocol(), r.saslMechanism(), r.username(),
                false), r.password());
        audit.record(me, "settings.kafka", r.enabled() ? r.bootstrapServers() : null, r.enabled() ? "enabled" : "disabled");
        return settings.kafka();
    }

    @PostMapping("/kafka/test")
    public KafkaConnections.TestResult testKafka(@RequestBody KafkaRequest r) {
        // An empty password in the form means "keep the stored one" for the test as well.
        String password = r.password() == null || r.password().isEmpty() ? settings.kafkaPassword() : r.password();
        return kafka.test(new KafkaConnections.KafkaRequest(r.bootstrapServers(), r.topicPrefix(), r.securityProtocol(), r.saslMechanism(),
                r.username(), password));
    }

    private String publicUrl() {
        String configured = env.getProperty("newxr.public-url");
        if (configured != null && !configured.isBlank()) return configured.trim().replaceAll("/+$", "");
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
