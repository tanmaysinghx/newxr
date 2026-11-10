package com.tanmaysinghx.newxr.platform;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Fills in the data source when the operator has not: an embedded H2 file database in
 * {@code <newxr home>/data}. For an external database only the driver is derived from the URL;
 * Hibernate detects the dialect from the connection.
 */
public class DatabaseEnvironmentPostProcessor implements EnvironmentPostProcessor {

    public static final String SOURCE = "newxrDatabaseDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        Map<String, Object> defaults = new LinkedHashMap<>();
        String url = env.getProperty("spring.datasource.url");
        if (url == null || url.isBlank()) {
            String file = NewxrHome.data().resolve("newxr").toString().replace('\\', '/');
            defaults.put("spring.datasource.url", "jdbc:h2:file:" + file + ";AUTO_SERVER=FALSE;DB_CLOSE_ON_EXIT=FALSE");
            defaults.put("spring.datasource.username", "sa");
            defaults.put("spring.datasource.password", "");
            defaults.put("spring.datasource.driver-class-name", "org.h2.Driver");
        } else if (env.getProperty("spring.datasource.driver-class-name") == null) {
            String driver = driverFor(url);
            if (driver != null) {
                defaults.put("spring.datasource.driver-class-name", driver);
            }
        }
        defaults.put("newxr.database.type", typeOf(env.getProperty("spring.datasource.url", (String) defaults.get("spring.datasource.url"))));
        env.getPropertySources().addLast(new MapPropertySource(SOURCE, defaults));
    }

    public static String driverFor(String url) {
        return switch (typeOf(url)) {
            case "postgresql" -> "org.postgresql.Driver";
            case "mysql" -> "com.mysql.cj.jdbc.Driver";
            case "h2" -> "org.h2.Driver";
            default -> null;
        };
    }

    public static String typeOf(String url) {
        String u = url == null ? "" : url.toLowerCase(Locale.ROOT);
        if (u.startsWith("jdbc:postgresql:")) return "postgresql";
        if (u.startsWith("jdbc:mysql:") || u.startsWith("jdbc:mariadb:")) return "mysql";
        if (u.startsWith("jdbc:h2:")) return "h2";
        return "other";
    }
}
