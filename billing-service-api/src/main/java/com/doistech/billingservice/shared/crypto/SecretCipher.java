package com.doistech.billingservice.shared.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Cifra segredos em repouso com AES-256-GCM. Formato: {@code v1:<base64(iv || ciphertext+tag)>}.
 */
public class SecretCipher {

    private static final String PREFIX = "v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("BILLING_ENCRYPTION_KEY is required (32 bytes, base64)");
        }
        byte[] raw = Base64.getDecoder().decode(base64Key.trim());
        if (raw.length != 32) {
            throw new IllegalStateException("BILLING_ENCRYPTION_KEY must decode to 32 bytes");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] output = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
            return PREFIX + Base64.getEncoder().encodeToString(output);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("could not encrypt secret", exception);
        }
    }

    public String decrypt(String value) {
        if (value == null) {
            return null;
        }
        if (!value.startsWith(PREFIX)) {
            throw new IllegalStateException("unsupported secret format");
        }
        try {
            byte[] input = Base64.getDecoder().decode(value.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, input, 0, IV_LENGTH));
            byte[] decrypted = cipher.doFinal(input, IV_LENGTH, input.length - IV_LENGTH);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("could not decrypt secret", exception);
        }
    }
}
