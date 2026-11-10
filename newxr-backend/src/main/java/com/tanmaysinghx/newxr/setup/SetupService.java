package com.tanmaysinghx.newxr.setup;

import com.tanmaysinghx.newxr.common.ApiException;
import com.tanmaysinghx.newxr.seed.SampleDataService;
import com.tanmaysinghx.newxr.settings.SettingsService;
import com.tanmaysinghx.newxr.settings.SettingsService.KafkaSettings;
import com.tanmaysinghx.newxr.settings.SettingsService.OrgSettings;
import com.tanmaysinghx.newxr.user.AuthSource;
import com.tanmaysinghx.newxr.user.PasswordPolicy;
import com.tanmaysinghx.newxr.user.Role;
import com.tanmaysinghx.newxr.user.User;
import com.tanmaysinghx.newxr.user.UserRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SetupService {

    public record AdminInput(String email, String name, String password) {}

    public record SignInInput(boolean builtinEnabled, boolean portalEnabled, String issuerUrl, String clientId,
                              String clientSecret, String buttonLabel, boolean autoProvision) {}

    public record KafkaInput(boolean enabled, String bootstrapServers, String topicPrefix, String securityProtocol,
                             String saslMechanism, String username, String password) {}

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final SettingsService settings;
    private final SampleDataService sampleData;

    public SetupService(UserRepository users, PasswordEncoder encoder, SettingsService settings, SampleDataService sampleData) {
        this.users = users;
        this.encoder = encoder;
        this.settings = settings;
        this.sampleData = sampleData;
    }

    public boolean isSetupRequired() {
        return !users.existsByRoleAndActiveTrue(Role.ADMIN);
    }

    public void validateAdmin(AdminInput admin) {
        if (admin == null || admin.email() == null || !admin.email().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw ApiException.badRequest("Enter a valid administrator email.");
        }
        if (admin.name() == null || admin.name().isBlank()) {
            throw ApiException.badRequest("Enter the administrator's name.");
        }
        PasswordPolicy.check(admin.password());
    }

    @Transactional
    public User initialize(AdminInput admin, OrgSettings org, SignInInput signIn, KafkaInput kafka,
                           boolean loadSampleData, String acceptedVia) {
        if (!isSetupRequired()) {
            throw ApiException.conflict("Setup has already been completed.");
        }
        validateAdmin(admin);
        if (signIn != null && signIn.portalEnabled()
                && (isBlank(signIn.issuerUrl()) || isBlank(signIn.clientId()))) {
            throw ApiException.badRequest("Portal SSO needs its address and a client ID.");
        }

        User u = users.findByEmailIgnoreCase(admin.email().trim()).orElseGet(User::new);
        u.setEmail(admin.email().trim().toLowerCase(Locale.ROOT));
        u.setDisplayName(admin.name().trim());
        u.setPasswordHash(encoder.encode(admin.password()));
        u.setRole(Role.ADMIN);
        u.setSource(AuthSource.LOCAL);
        u.setActive(true);
        u.setJobTitle("Administrator");
        u = users.save(u);

        settings.saveOrg(org == null ? new OrgSettings("Newxr", null, null) : org);
        if (signIn != null) {
            settings.saveSignIn(signIn.builtinEnabled(), signIn.portalEnabled(), signIn.issuerUrl(), signIn.clientId(),
                    signIn.clientSecret() == null ? "" : signIn.clientSecret(), signIn.buttonLabel(), signIn.autoProvision());
        }
        if (kafka != null) {
            settings.saveKafka(new KafkaSettings(kafka.enabled(), kafka.bootstrapServers(), kafka.topicPrefix(),
                    kafka.securityProtocol(), kafka.saslMechanism(), kafka.username(), false),
                    kafka.password() == null ? "" : kafka.password());
        }
        Map<String, String> record = new LinkedHashMap<>();
        record.put(SettingsService.LICENSE_ID, SetupLicense.ID);
        record.put(SettingsService.LICENSE_ACCEPTED_AT, Instant.now().toString());
        record.put(SettingsService.LICENSE_ACCEPTED_BY, u.getEmail() + " (" + acceptedVia + ")");
        record.put(SettingsService.SETUP_COMPLETED_AT, Instant.now().toString());
        settings.put(record);

        if (loadSampleData) {
            sampleData.load(u);
        }
        return u;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
