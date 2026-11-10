package com.tanmaysinghx.newxr.platform;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * The data directory: {@code ~/.newxr} unless {@code --newxrHome}, {@code NEWXR_HOME} or
 * {@code newxr.home} says otherwise. Holds the embedded database, secrets and newxr.properties.
 */
public final class NewxrHome {

    public static final String PROPERTY = "newxr.home";
    public static final String ENV = "NEWXR_HOME";

    private NewxrHome() {}

    public static Path resolve() {
        String explicit = System.getProperty(PROPERTY);
        if (explicit == null || explicit.isBlank()) {
            explicit = System.getenv(ENV);
        }
        if (explicit != null && !explicit.isBlank()) {
            return Path.of(explicit.trim()).toAbsolutePath().normalize();
        }
        return Path.of(System.getProperty("user.home"), ".newxr").toAbsolutePath();
    }

    public static Path data() {
        return resolve().resolve("data");
    }

    public static Path secrets() {
        return resolve().resolve("secrets");
    }

    public static void ensureDirectories() {
        try {
            Files.createDirectories(data());
            Files.createDirectories(secrets());
            ownerOnly(secrets(), "rwx------");
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create the Newxr home directory " + resolve(), e);
        }
    }

    /** Best effort: not every file system supports POSIX permissions. */
    public static void ownerOnly(Path path, String perms) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(perms));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows and some mounted volumes.
        }
    }
}
