package com.doistech.billingservice.callback;

import com.doistech.billingservice.shared.crypto.Hmac;

/**
 * Assinatura dos callbacks: {@code v1=hex(HMAC-SHA256(secret, timestamp + "." + body))}.
 */
public final class CallbackSigner {

    private CallbackSigner() {
    }

    public static String sign(String secret, long timestamp, String body) {
        return "v1=" + Hmac.sha256Hex(secret, timestamp + "." + body);
    }
}
