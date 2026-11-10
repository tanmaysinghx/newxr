package com.tanmaysinghx.newxr.setup;

import com.tanmaysinghx.newxr.platform.NewxrHome;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The one-time key that proves the person in the wizard can read the server's files (Jenkins
 * style). Lives in {@code <newxr home>/secrets/initialSetupKey} until setup completes, so it
 * survives the restart that follows the database step.
 */
@Service
public class SetupKeyService {

    private static final Logger log = LoggerFactory.getLogger(SetupKeyService.class);
    private final SecureRandom random = new SecureRandom();

    public Path keyFile() {
        return NewxrHome.secrets().resolve("initialSetupKey");
    }

    public boolean isIssued() {
        return Files.exists(keyFile());
    }

    public void issueIfAbsent() {
        try {
            String key;
            if (Files.exists(keyFile())) {
                key = Files.readString(keyFile()).trim();
            } else {
                byte[] b = new byte[16];
                random.nextBytes(b);
                key = HexFormat.of().formatHex(b);
                Files.createDirectories(keyFile().getParent());
                Files.writeString(keyFile(), key + "\n");
                NewxrHome.ownerOnly(keyFile(), "rw-------");
            }
            String bar = "*".repeat(72);
            log.info("""

                    {}
                    Newxr setup is required. Open this server in a browser and use this setup key:

                        {}

                    It is also stored in {}
                    {}""", bar, key, keyFile(), bar);
        } catch (IOException e) {
            log.error("Cannot write the setup key to {}: {}", keyFile(), e.getMessage());
        }
    }

    public boolean matches(String candidate) {
        if (candidate == null || !Files.exists(keyFile())) {
            return false;
        }
        try {
            String key = Files.readString(keyFile()).trim();
            return MessageDigest.isEqual(key.getBytes(StandardCharsets.UTF_8), candidate.trim().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            return false;
        }
    }

    public void complete() {
        try {
            Files.deleteIfExists(keyFile());
        } catch (IOException e) {
            log.warn("Could not delete {}: {}", keyFile(), e.getMessage());
        }
    }
}
