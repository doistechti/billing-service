package com.doistech.billingservice.shared.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class SecretCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void shouldRoundTripWithRandomIv() {
        SecretCipher cipher = new SecretCipher(KEY);
        String first = cipher.encrypt("APP_USR-token");
        String second = cipher.encrypt("APP_USR-token");

        assertNotEquals(first, second);
        assertEquals("APP_USR-token", cipher.decrypt(first));
        assertEquals("APP_USR-token", cipher.decrypt(second));
    }

    @Test
    void shouldRejectTamperedValueAndWrongKey() {
        SecretCipher cipher = new SecretCipher(KEY);
        String encrypted = cipher.encrypt("secret");
        String tampered = encrypted.substring(0, encrypted.length() - 4) + "AAAA";

        assertThrows(IllegalStateException.class, () -> cipher.decrypt(tampered));
        byte[] otherKey = new byte[32];
        otherKey[0] = 1;
        SecretCipher other = new SecretCipher(Base64.getEncoder().encodeToString(otherKey));
        assertThrows(IllegalStateException.class, () -> other.decrypt(encrypted));
    }

    @Test
    void shouldRequireA256BitKey() {
        assertThrows(IllegalStateException.class, () -> new SecretCipher(""));
        assertThrows(IllegalStateException.class, () -> new SecretCipher(Base64.getEncoder().encodeToString(new byte[16])));
    }
}
