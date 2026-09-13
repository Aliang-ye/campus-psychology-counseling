package com.example.psychology.security;

import org.springframework.stereotype.Component;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class PasswordHasher {
    private static final String PREFIX = "pbkdf2$";
    private static final int ITERATIONS = 120000;
    private static final int KEY_LENGTH = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    public String hash(String rawPassword) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] digest = pbkdf2(rawPassword, salt, ITERATIONS);
        return PREFIX + ITERATIONS + "$" + encode(salt) + "$" + encode(digest);
    }

    public boolean matches(String rawPassword, String stored) {
        if (rawPassword == null || stored == null || stored.isBlank()) {
            return false;
        }
        if (!stored.startsWith(PREFIX)) {
            return stored.equals(rawPassword);
        }
        String[] parts = stored.split("\\$");
        if (parts.length != 4) {
            return false;
        }
        int iterations = Integer.parseInt(parts[1]);
        byte[] salt = decode(parts[2]);
        byte[] expected = decode(parts[3]);
        byte[] actual = pbkdf2(rawPassword, salt, iterations);
        return MessageDigest.isEqual(expected, actual);
    }

    public boolean isHashed(String stored) {
        return stored != null && stored.startsWith(PREFIX);
    }

    private byte[] pbkdf2(String password, byte[] salt, int iterations) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH);
            return factory.generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("密码哈希失败", e);
        }
    }

    private String encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private byte[] decode(String text) {
        return Base64.getDecoder().decode(text.getBytes(StandardCharsets.UTF_8));
    }
}
