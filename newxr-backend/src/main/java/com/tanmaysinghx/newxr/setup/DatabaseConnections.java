package com.tanmaysinghx.newxr.setup;

import com.tanmaysinghx.newxr.NewxrApplication;
import com.tanmaysinghx.newxr.common.ApiException;
import com.tanmaysinghx.newxr.platform.DatabaseEnvironmentPostProcessor;
import com.tanmaysinghx.newxr.platform.NewxrConfigFile;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import javax.sql.DataSource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/** Tests a PostgreSQL / MySQL connection and points Newxr at it (newxr.properties + restart). */
@Service
public class DatabaseConnections {

    public record DatabaseRequest(String type, String host, Integer port, String database, String username,
                                  String password, String jdbcUrl) {}

    public record TestResult(boolean ok, String message, String product, String version, boolean hasNewxrData, String jdbcUrl) {}

    public record Status(String type, String product, String version, String url, boolean embedded,
                         boolean confirmed, String configFile) {}

    private final DataSource dataSource;
    private final Environment env;

    public DatabaseConnections(DataSource dataSource, Environment env) {
        this.dataSource = dataSource;
        this.env = env;
    }

    public Status status() {
        String type = env.getProperty("newxr.database.type", "h2");
        String product = null, version = null;
        try (Connection c = dataSource.getConnection()) {
            DatabaseMetaData md = c.getMetaData();
            product = md.getDatabaseProductName();
            version = md.getDatabaseProductVersion();
        } catch (SQLException ignored) {
            // Reported as unknown.
        }
        return new Status(type, product, version, maskUrl(env.getProperty("spring.datasource.url")), "h2".equals(type),
                env.getProperty("newxr.setup.database-confirmed", Boolean.class, false),
                NewxrConfigFile.resolve().toString());
    }

    public static String jdbcUrl(DatabaseRequest r) {
        if (r.jdbcUrl() != null && !r.jdbcUrl().isBlank()) {
            String u = r.jdbcUrl().trim();
            if (!u.startsWith("jdbc:postgresql:") && !u.startsWith("jdbc:mysql:") && !u.startsWith("jdbc:mariadb:")) {
                throw ApiException.badRequest("Only jdbc:postgresql: and jdbc:mysql: URLs are supported.");
            }
            return u;
        }
        String type = r.type() == null ? "" : r.type().toLowerCase(Locale.ROOT);
        if (r.host() == null || r.host().isBlank() || r.database() == null || r.database().isBlank()) {
            throw ApiException.badRequest("Host and database name are required.");
        }
        if (!r.host().matches("[A-Za-z0-9._\\-:\\[\\]]+") || !r.database().matches("[A-Za-z0-9_\\-]+")) {
            throw ApiException.badRequest("Host or database name contains characters that are not allowed.");
        }
        return switch (type) {
            case "postgresql" -> "jdbc:postgresql://" + r.host().trim() + ":" + (r.port() == null ? 5432 : r.port()) + "/" + r.database().trim();
            case "mysql" -> "jdbc:mysql://" + r.host().trim() + ":" + (r.port() == null ? 3306 : r.port()) + "/" + r.database().trim()
                    + "?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC";
            default -> throw ApiException.badRequest("Choose PostgreSQL or MySQL.");
        };
    }

    public TestResult test(DatabaseRequest r) {
        String url = jdbcUrl(r);
        try {
            Class.forName(DatabaseEnvironmentPostProcessor.driverFor(url));
        } catch (ClassNotFoundException | NullPointerException e) {
            return new TestResult(false, "No driver for that database type.", null, null, false, maskUrl(url));
        }
        Properties p = new Properties();
        if (r.username() != null) p.setProperty("user", r.username());
        if (r.password() != null) p.setProperty("password", r.password());
        p.setProperty("connectTimeout", url.startsWith("jdbc:mysql") ? "8000" : "8");
        p.setProperty("loginTimeout", "8");
        DriverManager.setLoginTimeout(8);
        try (Connection c = DriverManager.getConnection(url, p)) {
            DatabaseMetaData md = c.getMetaData();
            boolean existing = false;
            for (String name : new String[]{"users", "USERS"}) {
                try (ResultSet rs = md.getTables(c.getCatalog(), null, name, new String[]{"TABLE"})) {
                    existing |= rs.next();
                }
            }
            String msg = existing
                    ? "Connected. This database already holds Newxr data, which will be used as-is."
                    : "Connected. Tables will be created on first start.";
            return new TestResult(true, msg, md.getDatabaseProductName(), md.getDatabaseProductVersion(), existing, maskUrl(url));
        } catch (SQLException e) {
            return new TestResult(false, "Could not connect: " + rootMessage(e), null, null, false, maskUrl(url));
        }
    }

    /** Writes the connection to newxr.properties and restarts onto it. */
    public void switchTo(DatabaseRequest r) {
        TestResult t = test(r);
        if (!t.ok()) {
            throw ApiException.badRequest(t.message());
        }
        Map<String, String> changes = new LinkedHashMap<>();
        changes.put("spring.datasource.url", jdbcUrl(r));
        changes.put("spring.datasource.username", r.username() == null ? "" : r.username());
        changes.put("spring.datasource.password", r.password() == null ? "" : r.password());
        changes.put("spring.datasource.driver-class-name", null);
        changes.put("newxr.setup.database-confirmed", "true");
        write(changes, "Database set in the Newxr console");
        NewxrApplication.restart();
    }

    /** Keeps the embedded database; only records that the choice was made. */
    public void keepEmbedded() {
        write(Map.of("newxr.setup.database-confirmed", "true"), "Embedded database confirmed in setup");
        // Picked up without a restart for the wizard's next status call.
        System.setProperty("newxr.setup.database-confirmed", "true");
    }

    private static void write(Map<String, String> changes, String reason) {
        try {
            NewxrConfigFile.update(NewxrConfigFile.resolve(), changes, reason);
        } catch (IOException e) {
            throw ApiException.badRequest("Cannot write " + NewxrConfigFile.resolve() + ": " + e.getMessage());
        }
    }

    static String maskUrl(String url) {
        return url == null ? null : url.replaceAll("(?i)(password=)[^&;]*", "$1****");
    }

    private static String rootMessage(Throwable t) {
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        String m = t.getMessage();
        return m == null ? t.getClass().getSimpleName() : (m.length() > 300 ? m.substring(0, 300) + "..." : m);
    }
}
