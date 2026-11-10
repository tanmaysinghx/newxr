package com.tanmaysinghx.newxr.settings;

import com.tanmaysinghx.newxr.platform.SecretBox;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Typed access to {@link Setting}s. Values from {@code newxr.properties} under {@code newxr.sso.*}
 * and {@code newxr.kafka.*} act as defaults until an administrator saves the same section in the
 * console, so a fully file-driven install works and the console still wins afterwards.
 */
@Service
public class SettingsService {

    public static final String ORG_NAME = "org.name";
    public static final String ORG_LOGO = "org.logo-url";
    public static final String ORG_SUPPORT = "org.support-url";

    public static final String LICENSE_ID = "license.id";
    public static final String LICENSE_ACCEPTED_AT = "license.accepted-at";
    public static final String LICENSE_ACCEPTED_BY = "license.accepted-by";
    public static final String SETUP_COMPLETED_AT = "setup.completed-at";

    public static final String SSO_SAVED = "sso.saved";
    public static final String SSO_BUILTIN = "sso.builtin-enabled";
    public static final String SSO_PORTAL = "sso.portal-enabled";
    public static final String SSO_ISSUER = "sso.portal.issuer-url";
    public static final String SSO_CLIENT_ID = "sso.portal.client-id";
    public static final String SSO_CLIENT_SECRET = "sso.portal.client-secret";
    public static final String SSO_BUTTON = "sso.portal.button-label";
    public static final String SSO_AUTO_PROVISION = "sso.portal.auto-provision";

    public static final String KAFKA_SAVED = "kafka.saved";
    public static final String KAFKA_ENABLED = "kafka.enabled";
    public static final String KAFKA_SERVERS = "kafka.bootstrap-servers";
    public static final String KAFKA_PREFIX = "kafka.topic-prefix";
    public static final String KAFKA_PROTOCOL = "kafka.security-protocol";
    public static final String KAFKA_MECHANISM = "kafka.sasl-mechanism";
    public static final String KAFKA_USERNAME = "kafka.username";
    public static final String KAFKA_PASSWORD = "kafka.password";

    public static final String PRODUCTS_DISABLED = "products.disabled";

    public record OrgSettings(String name, String logoUrl, String supportUrl) {}

    public record SignInSettings(
            boolean builtinEnabled,
            boolean portalEnabled,
            String portalIssuerUrl,
            String portalClientId,
            boolean portalClientSecretSet,
            String portalButtonLabel,
            boolean autoProvision) {

        public boolean portalConfigured() {
            return portalEnabled && notBlank(portalIssuerUrl) && notBlank(portalClientId);
        }
    }

    public record KafkaSettings(
            boolean enabled,
            String bootstrapServers,
            String topicPrefix,
            String securityProtocol,
            String saslMechanism,
            String username,
            boolean passwordSet) {

        public String eventsTopic() {
            return (notBlank(topicPrefix) ? topicPrefix : "newxr") + ".events";
        }
    }

    /** Published after any section changes, so caches (SSO registration, Kafka producer) reset. */
    public record SettingsChanged(String section) {}

    private final SettingRepository repo;
    private final SecretBox secretBox;
    private final Environment env;
    private final ApplicationEventPublisher events;

    public SettingsService(SettingRepository repo, SecretBox secretBox, Environment env, ApplicationEventPublisher events) {
        this.repo = repo;
        this.secretBox = secretBox;
        this.env = env;
        this.events = events;
    }

    // ---- raw ----------------------------------------------------------------------------

    public Optional<String> get(String key) {
        return repo.findById(key).map(Setting::getValue).filter(v -> v != null);
    }

    public String get(String key, String fallback) {
        return get(key).orElse(fallback);
    }

    public boolean flag(String key, boolean fallback) {
        return get(key).map(Boolean::parseBoolean).orElse(fallback);
    }

    @Transactional
    public void put(Map<String, String> values) {
        values.forEach((k, v) -> {
            Setting s = repo.findById(k).orElseGet(() -> new Setting(k, null));
            s.setValue(v);
            s.setUpdatedAt(Instant.now());
            repo.save(s);
        });
    }

    // ---- organization -------------------------------------------------------------------

    public OrgSettings org() {
        return new OrgSettings(get(ORG_NAME, "Newxr"), get(ORG_LOGO, null), get(ORG_SUPPORT, null));
    }

    @Transactional
    public void saveOrg(OrgSettings org) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(ORG_NAME, blankToNull(org.name()) == null ? "Newxr" : org.name().trim());
        m.put(ORG_LOGO, blankToNull(org.logoUrl()));
        m.put(ORG_SUPPORT, blankToNull(org.supportUrl()));
        put(m);
        events.publishEvent(new SettingsChanged("org"));
    }

    // ---- sign-in ------------------------------------------------------------------------

    public SignInSettings signIn() {
        boolean saved = flag(SSO_SAVED, false);
        String issuer = saved ? get(SSO_ISSUER, null) : env.getProperty("newxr.sso.portal.issuer-url");
        String clientId = saved ? get(SSO_CLIENT_ID, null) : env.getProperty("newxr.sso.portal.client-id");
        boolean secretSet = saved ? get(SSO_CLIENT_SECRET).isPresent() : notBlank(env.getProperty("newxr.sso.portal.client-secret"));
        boolean portal = saved ? flag(SSO_PORTAL, false) : notBlank(issuer) && notBlank(clientId);
        boolean builtin = saved ? flag(SSO_BUILTIN, true) : env.getProperty("newxr.sso.builtin-enabled", Boolean.class, true);
        return new SignInSettings(builtin || !portal, portal, issuer, clientId, secretSet,
                get(SSO_BUTTON, "Continue with SSO"), flag(SSO_AUTO_PROVISION, true));
    }

    public String portalClientSecret() {
        return flag(SSO_SAVED, false)
                ? get(SSO_CLIENT_SECRET).map(secretBox::decrypt).orElse(null)
                : blankToNull(env.getProperty("newxr.sso.portal.client-secret"));
    }

    /** {@code clientSecret == null} keeps the stored one; empty string clears it. */
    @Transactional
    public void saveSignIn(boolean builtin, boolean portal, String issuer, String clientId, String clientSecret,
                           String buttonLabel, boolean autoProvision) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(SSO_SAVED, "true");
        m.put(SSO_BUILTIN, String.valueOf(builtin));
        m.put(SSO_PORTAL, String.valueOf(portal));
        m.put(SSO_ISSUER, blankToNull(stripSlash(issuer)));
        m.put(SSO_CLIENT_ID, blankToNull(clientId));
        if (clientSecret != null) {
            m.put(SSO_CLIENT_SECRET, clientSecret.isEmpty() ? null : secretBox.encrypt(clientSecret));
        } else if (!flag(SSO_SAVED, false)) {
            // First save after a file-driven start: carry the file's secret into the console.
            m.put(SSO_CLIENT_SECRET, secretBox.encrypt(portalClientSecret()));
        }
        m.put(SSO_BUTTON, blankToNull(buttonLabel) == null ? "Continue with SSO" : buttonLabel.trim());
        m.put(SSO_AUTO_PROVISION, String.valueOf(autoProvision));
        put(m);
        events.publishEvent(new SettingsChanged("sso"));
    }

    // ---- kafka --------------------------------------------------------------------------

    public KafkaSettings kafka() {
        if (flag(KAFKA_SAVED, false)) {
            return new KafkaSettings(flag(KAFKA_ENABLED, false), get(KAFKA_SERVERS, null), get(KAFKA_PREFIX, "newxr"),
                    get(KAFKA_PROTOCOL, "PLAINTEXT"), get(KAFKA_MECHANISM, "PLAIN"), get(KAFKA_USERNAME, null),
                    get(KAFKA_PASSWORD).isPresent());
        }
        String servers = env.getProperty("newxr.kafka.bootstrap-servers");
        return new KafkaSettings(notBlank(servers), servers, env.getProperty("newxr.kafka.topic-prefix", "newxr"),
                env.getProperty("newxr.kafka.security-protocol", "PLAINTEXT"),
                env.getProperty("newxr.kafka.sasl-mechanism", "PLAIN"), env.getProperty("newxr.kafka.username"),
                notBlank(env.getProperty("newxr.kafka.password")));
    }

    public String kafkaPassword() {
        return flag(KAFKA_SAVED, false)
                ? get(KAFKA_PASSWORD).map(secretBox::decrypt).orElse(null)
                : blankToNull(env.getProperty("newxr.kafka.password"));
    }

    /** {@code password == null} keeps the stored one; empty string clears it. */
    @Transactional
    public void saveKafka(KafkaSettings k, String password) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(KAFKA_SAVED, "true");
        m.put(KAFKA_ENABLED, String.valueOf(k.enabled()));
        m.put(KAFKA_SERVERS, blankToNull(k.bootstrapServers()));
        m.put(KAFKA_PREFIX, blankToNull(k.topicPrefix()) == null ? "newxr" : k.topicPrefix().trim());
        m.put(KAFKA_PROTOCOL, blankToNull(k.securityProtocol()) == null ? "PLAINTEXT" : k.securityProtocol());
        m.put(KAFKA_MECHANISM, blankToNull(k.saslMechanism()) == null ? "PLAIN" : k.saslMechanism());
        m.put(KAFKA_USERNAME, blankToNull(k.username()));
        if (password != null) {
            m.put(KAFKA_PASSWORD, password.isEmpty() ? null : secretBox.encrypt(password));
        } else if (!flag(KAFKA_SAVED, false)) {
            m.put(KAFKA_PASSWORD, secretBox.encrypt(kafkaPassword()));
        }
        put(m);
        events.publishEvent(new SettingsChanged("kafka"));
    }

    // ---- helpers ------------------------------------------------------------------------

    static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    static String stripSlash(String s) {
        return s == null ? null : s.trim().replaceAll("/+$", "");
    }
}
