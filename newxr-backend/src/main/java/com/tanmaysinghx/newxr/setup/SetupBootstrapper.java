package com.tanmaysinghx.newxr.setup;

import com.tanmaysinghx.newxr.platform.NewxrConfigFile;
import com.tanmaysinghx.newxr.settings.SettingsService.OrgSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * On start: when no administrator exists, either create one from {@code newxr.bootstrap.*}
 * (file/env-driven installs) or issue the setup key for the wizard.
 */
@Component
public class SetupBootstrapper implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SetupBootstrapper.class);

    private final SetupService setup;
    private final SetupKeyService keys;
    private final String email;
    private final String password;
    private final String port;

    public SetupBootstrapper(SetupService setup, SetupKeyService keys,
                             @Value("${newxr.bootstrap.admin-email:}") String email,
                             @Value("${newxr.bootstrap.admin-password:}") String password,
                             @Value("${server.port:8080}") String port) {
        this.setup = setup;
        this.keys = keys;
        this.email = email;
        this.password = password;
        this.port = port;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!setup.isSetupRequired()) {
            keys.complete();
            log.info("Newxr is ready on port {} (config: {})", port, NewxrConfigFile.resolve());
            return;
        }
        if (!email.isBlank() && !password.isBlank()) {
            setup.initialize(new SetupService.AdminInput(email, email.substring(0, email.indexOf('@') < 0 ? email.length() : email.indexOf('@')), password),
                    new OrgSettings("Newxr", null, null), null, null, false, "accepted via newxr.bootstrap configuration");
            keys.complete();
            log.warn("Created administrator {} from newxr.bootstrap.*. Remove those settings now that the account exists.", email);
            return;
        }
        keys.issueIfAbsent();
    }
}
