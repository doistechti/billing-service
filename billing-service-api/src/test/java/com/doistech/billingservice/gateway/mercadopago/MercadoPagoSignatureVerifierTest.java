package com.doistech.billingservice.gateway.mercadopago;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.doistech.billingservice.shared.crypto.Hmac;
import org.junit.jupiter.api.Test;

class MercadoPagoSignatureVerifierTest {

    private static final String SECRET = "webhook-secret";

    @Test
    void shouldBuildManifestAsDocumentedByMercadoPago() {
        assertEquals("id:abc123;request-id:req-1;ts:1704908010;",
                MercadoPagoSignatureVerifier.manifest("ABC123", "req-1", "1704908010"));
        assertEquals("ts:1704908010;", MercadoPagoSignatureVerifier.manifest(null, null, "1704908010"));
    }

    @Test
    void shouldAcceptValidSignatureAndRejectTampering() {
        String v1 = Hmac.sha256Hex(SECRET, "id:123456;request-id:req-1;ts:1704908010;");
        String header = "ts=1704908010,v1=" + v1;

        assertTrue(MercadoPagoSignatureVerifier.isValid(SECRET, header, "req-1", "123456"));
        assertFalse(MercadoPagoSignatureVerifier.isValid(SECRET, header, "req-1", "999999"));
        assertFalse(MercadoPagoSignatureVerifier.isValid("other-secret", header, "req-1", "123456"));
        assertFalse(MercadoPagoSignatureVerifier.isValid(SECRET, null, "req-1", "123456"));
        assertFalse(MercadoPagoSignatureVerifier.isValid(SECRET, "garbage", "req-1", "123456"));
    }
}
