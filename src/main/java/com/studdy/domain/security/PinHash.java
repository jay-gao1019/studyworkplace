package com.studdy.domain.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** 家长 PIN：PBKDF2 加盐散列（docs/05 §3 pin_hash；防孩子顺手改配置的门槛，非安全边界——docs/05 v1 §10 语义保留）。 */
public final class PinHash {

    private static final int ITERATIONS = 120_000;
    private static final int KEY_LENGTH = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PinHash() {}

    /** 返回格式 "pbkdf2:迭代次数:saltB64:hashB64"。 */
    public static String hash(String pin) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] out = pbkdf2(pin.toCharArray(), salt);
        Base64.Encoder b64 = Base64.getEncoder();
        return "pbkdf2:" + ITERATIONS + ":" + b64.encodeToString(salt) + ":" + b64.encodeToString(out);
    }

    public static boolean verify(String pin, String stored) {
        if (pin == null || stored == null) {
            return false;
        }
        String[] parts = stored.split(":");
        if (parts.length != 4 || !parts[0].equals("pbkdf2")) {
            return false;
        }
        byte[] salt = Base64.getDecoder().decode(parts[2]);
        byte[] expected = Base64.getDecoder().decode(parts[3]);
        byte[] actual = pbkdf2(pin.toCharArray(), salt);
        return java.security.MessageDigest.isEqual(expected, actual);
    }

    private static byte[] pbkdf2(char[] pin, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(pin, salt, ITERATIONS, KEY_LENGTH);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("PIN 散列失败", e);
        }
    }
}
