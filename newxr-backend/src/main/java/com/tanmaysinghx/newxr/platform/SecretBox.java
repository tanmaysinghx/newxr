package com.tanmaysinghx.newxr.platform;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM for secrets kept in the database (Portal SSO client secret, Kafka password). The key
 * comes from {@code newxr.security.master-key} or is generated once into
 * {@code <newxr home>/secrets/master.key}. Ciphertext is {@code v1:<base64(iv | ciphertext)>}.
 */
@Component
public class SecretBox {

    private static final String PREFIX = "v1:";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKeySpec key;

    public SecretBox(@Value("${newxr.security.master-key:}") String configuredKey) {
        this.key = new SecretKeySpec(loadKey(configuredKey), "AES");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return null;
        }
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null || stored.isEmpty()) {
            return null;
        }
        if (!stored.startsWith(PREFIX)) {
            throw new IllegalStateException("Unrecognised secret format");
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, all, 0, 12));
            return new String(cipher.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("A stored secret cannot be decrypted: was the master key replaced?", e);
        }
    }

    private static byte[] loadKey(String configured) {
        if (configured != null && !configured.isBlank()) {
            return checkLength(Base64.getDecoder().decode(configured.trim()));
        }
        Path file = NewxrHome.secrets().resolve("master.key");
        try {
            if (Files.exists(file)) {
                return checkLength(Base64.getDecoder().decode(Files.readString(file).trim()));
            }
            byte[] fresh = new byte[32];
            RANDOM.nextBytes(fresh);
            Files.createDirectories(file.getParent());
            Files.writeString(file, Base64.getEncoder().encodeToString(fresh) + "\n");
            NewxrHome.ownerOnly(file, "rw-------");
            return fresh;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read or create " + file, e);
        }
    }

    private static byte[] checkLength(byte[] key) {
        if (key.length != 32) {
            throw new IllegalStateException("newxr.security.master-key must be 32 bytes, base64 encoded");
        }
        return key;
    }
}
