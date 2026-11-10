package com.tanmaysinghx.newxr.setup;

import com.tanmaysinghx.newxr.common.ApiException;
import com.tanmaysinghx.newxr.settings.SettingsService.OrgSettings;
import com.tanmaysinghx.newxr.user.PasswordPolicy;
import com.tanmaysinghx.newxr.user.User;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * The first-run wizard. Unauthenticated, so every call that changes anything (or probes the
 * network) requires the setup key in {@code X-Setup-Key}, and all of it closes once an
 * administrator exists.
 */
@RestController
@RequestMapping("/api/public/setup")
public class SetupController {

    public record Status(boolean setupRequired, boolean setupKeyIssued, String setupKeyFile, String licenseId,
                         String licenseName, String passwordPolicy, DatabaseConnections.Status database,
                         String suggestedPublicUrl, String portalRedirectUri) {}

    public record InitializeRequest(String setupKey, Boolean licenseAccepted, String licenseId,
                                    SetupService.AdminInput admin, OrgSettings organization,
                                    SetupService.SignInInput signIn, SetupService.KafkaInput kafka,
                                    boolean sampleData) {}

    public record IssuerRequest(String issuerUrl) {}

    private final SetupService setup;
    private final SetupKeyService keys;
    private final DatabaseConnections databases;
    private final KafkaConnections kafka;
    private final PortalDiscovery portal;
    private final Environment env;

    public SetupController(SetupService setup, SetupKeyService keys, DatabaseConnections databases,
                           KafkaConnections kafka, PortalDiscovery portal, Environment env) {
        this.setup = setup;
        this.keys = keys;
        this.databases = databases;
        this.kafka = kafka;
        this.portal = portal;
        this.env = env;
    }

    @GetMapping("/status")
    public Status status() {
        boolean required = setup.isSetupRequired();
        String base = publicUrl();
        return new Status(required, required && keys.isIssued(), required ? keys.keyFile().toString() : null,
                SetupLicense.ID, SetupLicense.NAME, PasswordPolicy.DESCRIPTION,
                required ? databases.status() : null, base, base + "/login/oauth2/code/portal");
    }

    @GetMapping(value = "/license", produces = "text/plain;charset=UTF-8")
    public String license() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/legal/LICENSE.txt")) {
            return in == null ? SetupLicense.NAME : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @PostMapping("/verify-key")
    public Map<String, Object> verifyKey(@RequestHeader(value = "X-Setup-Key", required = false) String key) {
        guard(key);
        return Map.of("valid", true);
    }

    @PostMapping("/database/test")
    public DatabaseConnections.TestResult testDatabase(@RequestHeader(value = "X-Setup-Key", required = false) String key,
                                                       @RequestBody DatabaseConnections.DatabaseRequest body) {
        guard(key);
        return databases.test(body);
    }

    /** {@code type: "h2"} keeps the embedded database; anything else switches and restarts. */
    @PostMapping("/database")
    public Map<String, Object> saveDatabase(@RequestHeader(value = "X-Setup-Key", required = false) String key,
                                            @RequestBody DatabaseConnections.DatabaseRequest body) {
        guard(key);
        if ("h2".equalsIgnoreCase(body.type())) {
            databases.keepEmbedded();
            return Map.of("restarting", false);
        }
        databases.switchTo(body);
        return Map.of("restarting", true);
    }

    @PostMapping("/kafka/test")
    public KafkaConnections.TestResult testKafka(@RequestHeader(value = "X-Setup-Key", required = false) String key,
                                                 @RequestBody KafkaConnections.KafkaRequest body) {
        guard(key);
        return kafka.test(body);
    }

    @PostMapping("/sso/test")
    public PortalDiscovery.TestResult testPortal(@RequestHeader(value = "X-Setup-Key", required = false) String key,
                                                 @RequestBody IssuerRequest body) {
        guard(key);
        return portal.test(body.issuerUrl());
    }

    @PostMapping("/initialize")
    public Map<String, Object> initialize(@RequestBody InitializeRequest body) {
        // Checked before the key so a typo costs nothing.
        SetupLicense.requireAcceptance(body.licenseAccepted(), body.licenseId());
        setup.validateAdmin(body.admin());
        guard(body.setupKey());
        User admin = setup.initialize(body.admin(), body.organization(), body.signIn(), body.kafka(), body.sampleData(),
                "accepted in the setup wizard");
        keys.complete();
        return Map.of("success", true, "email", admin.getEmail());
    }

    private void guard(String key) {
        if (!setup.isSetupRequired()) {
            throw ApiException.conflict("Setup has already been completed.");
        }
        if (!keys.matches(key)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "That setup key is not correct. Copy it from " + keys.keyFile() + ".");
        }
    }

    private String publicUrl() {
        String configured = env.getProperty("newxr.public-url");
        if (configured != null && !configured.isBlank()) {
            return configured.trim().replaceAll("/+$", "");
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }
}
