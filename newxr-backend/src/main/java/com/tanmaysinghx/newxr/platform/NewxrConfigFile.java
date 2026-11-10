package com.tanmaysinghx.newxr.platform;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The operator's configuration file: {@code <newxr home>/newxr.properties}, or the file named by
 * {@code --config=<file>} / {@code NEWXR_CONFIG}.
 *
 * <p>Imported by Spring at startup, so anything Spring accepts can go in it. The setup wizard and
 * Administration → Database edit it in place: only the keys they own change; every other line,
 * comments included, is kept, because this file is also the operator's to edit by hand.
 */
public final class NewxrConfigFile {

    public static final String CONFIG_PROPERTY = "newxr.config";
    public static final String CONFIG_ENV = "NEWXR_CONFIG";

    private NewxrConfigFile() {}

    public static Path resolve() {
        String explicit = System.getProperty(CONFIG_PROPERTY);
        if (explicit == null || explicit.isBlank()) {
            explicit = System.getenv(CONFIG_ENV);
        }
        if (explicit != null && !explicit.isBlank()) {
            return Path.of(explicit.trim()).toAbsolutePath().normalize();
        }
        return NewxrHome.resolve().resolve("newxr.properties");
    }

    /**
     * Writes the commented-out template when the file does not exist yet, so an operator who
     * prefers files to the wizard finds every option in one place.
     */
    public static void writeTemplateIfAbsent(Path file) {
        if (Files.exists(file)) {
            return;
        }
        try (var in = NewxrConfigFile.class.getResourceAsStream("/newxr.properties.template")) {
            if (in == null) {
                return;
            }
            Files.createDirectories(file.toAbsolutePath().getParent());
            Files.write(file, in.readAllBytes(), StandardOpenOption.CREATE_NEW);
            NewxrHome.ownerOnly(file, "rw-------");
        } catch (IOException ignored) {
            // A read-only home is not a reason to refuse to start; the wizard still works.
        }
    }

    /** Active (uncommented) entries. */
    public static Map<String, String> read(Path file) {
        Map<String, String> values = new LinkedHashMap<>();
        if (!Files.exists(file)) {
            return values;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String key = keyOf(line);
                if (key != null) {
                    values.put(key, unescape(line.substring(line.indexOf('=') + 1).trim()));
                }
            }
        } catch (IOException ignored) {
            // Treated as empty.
        }
        return values;
    }

    /** Sets ({@code value}) or removes ({@code null}) keys in place; new keys go at the end. */
    public static void update(Path file, Map<String, String> changes, String reason) throws IOException {
        List<String> lines = Files.exists(file)
                ? Files.readAllLines(file, StandardCharsets.UTF_8)
                : List.of();
        Map<String, String> pending = new LinkedHashMap<>(changes);
        List<String> result = new ArrayList<>(lines.size() + changes.size() + 2);
        for (String line : lines) {
            String key = keyOf(line);
            if (key != null && pending.containsKey(key)) {
                String value = pending.remove(key);
                if (value != null) {
                    result.add(key + "=" + escape(value));
                }
            } else if (key == null || !changes.containsKey(key)) {
                result.add(line);
            }
        }
        List<String> appended = pending.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .map(e -> e.getKey() + "=" + escape(e.getValue()))
                .toList();
        if (!appended.isEmpty()) {
            if (!result.isEmpty() && !result.getLast().isBlank()) {
                result.add("");
            }
            result.add("# " + reason + " (" + LocalDate.now() + ")");
            result.addAll(appended);
        }
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.write(file, result, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        NewxrHome.ownerOnly(file, "rw-------");
    }

    private static String keyOf(String line) {
        String t = line.strip();
        if (t.isEmpty() || t.startsWith("#") || t.startsWith("!")) {
            return null;
        }
        int eq = t.indexOf('=');
        return eq <= 0 ? null : t.substring(0, eq).strip();
    }

    /** Backslash is the properties escape character; Windows paths and passwords would lose it. */
    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\n", "\\n");
    }

    private static String unescape(String value) {
        return value.replace("\\n", "\n").replace("\\\\", "\\");
    }
}
