package com.tanmaysinghx.newxr;

import com.tanmaysinghx.newxr.platform.NewxrConfigFile;
import com.tanmaysinghx.newxr.platform.NewxrHome;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point. {@code java -jar newxr.jar} with no arguments runs on an embedded database in
 * {@code ~/.newxr}; the first-run wizard does the rest.
 *
 * <p>Launcher flags (Jenkins style): {@code --httpPort=8080}, {@code --httpListenAddress=0.0.0.0},
 * {@code --newxrHome=/var/lib/newxr}, {@code --config=/etc/newxr/newxr.properties}. Anything else
 * is passed to Spring unchanged.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@EnableAsync
public class NewxrApplication {

    private static volatile ConfigurableApplicationContext context;
    private static String[] savedArgs = new String[0];

    public static void main(String[] args) {
        savedArgs = args == null ? new String[0] : args.clone();
        context = start(savedArgs);
    }

    private static ConfigurableApplicationContext start(String[] args) {
        String[] springArgs = normalizeArguments(args);
        NewxrHome.ensureDirectories();
        Path configFile = NewxrConfigFile.resolve();
        NewxrConfigFile.writeTemplateIfAbsent(configFile);
        // Lower precedence than environment variables and command-line arguments, higher than the
        // built-in application.yaml — the operator's file beats our defaults, never their overrides.
        System.setProperty("spring.config.import", "optional:file:" + configFile);
        return SpringApplication.run(NewxrApplication.class, springArgs);
    }

    /**
     * Closes the running context and starts a new one with the same arguments. Used after the
     * database is changed, so the new connection settings in {@code newxr.properties} take effect
     * without the operator touching the server.
     */
    public static void restart() {
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(750);
                if (context != null) {
                    context.close();
                }
                context = start(savedArgs);
            } catch (Exception e) {
                // Under systemd / Docker a non-zero exit restarts us with the saved configuration.
                System.err.println("Newxr restart failed: " + e.getMessage());
                System.exit(3);
            }
        }, "newxr-restart");
        thread.setDaemon(false);
        thread.start();
    }

    static String[] normalizeArguments(String[] args) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            String value = null;
            String name = arg;
            int eq = arg.indexOf('=');
            if (arg.startsWith("--") && eq > 0) {
                name = arg.substring(0, eq);
                value = arg.substring(eq + 1);
            } else if (isLauncherFlag(arg) && i + 1 < args.length) {
                value = args[++i];
            }
            switch (name) {
                case "--httpPort" -> out.add("--server.port=" + value);
                case "--httpListenAddress" -> out.add("--server.address=" + value);
                case "--prefix" -> out.add("--server.servlet.context-path=" + value);
                case "--newxrHome" -> System.setProperty(NewxrHome.PROPERTY, value);
                case "--config" -> System.setProperty(NewxrConfigFile.CONFIG_PROPERTY, value);
                default -> out.add(arg);
            }
        }
        return out.toArray(String[]::new);
    }

    private static boolean isLauncherFlag(String arg) {
        return switch (arg) {
            case "--httpPort", "--httpListenAddress", "--prefix", "--newxrHome", "--config" -> true;
            default -> false;
        };
    }
}
