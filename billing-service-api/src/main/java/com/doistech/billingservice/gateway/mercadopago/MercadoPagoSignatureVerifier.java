package com.doistech.billingservice.gateway.mercadopago;

import com.doistech.billingservice.shared.crypto.Hmac;
import org.springframework.util.StringUtils;

/**
 * Valida o header x-signature dos webhooks do Mercado Pago.
 *
 * <p>Formato do header: {@code ts=1704908010,v1=618c8534...}. O manifesto assinado é
 * {@code id:<data.id>;request-id:<x-request-id>;ts:<ts>;}, omitindo as partes ausentes.
 */
public final class MercadoPagoSignatureVerifier {

    private MercadoPagoSignatureVerifier() {
    }

    public static boolean isValid(String secret, String signatureHeader, String requestId, String dataId) {
        if (!StringUtils.hasText(secret) || !StringUtils.hasText(signatureHeader)) {
            return false;
        }
        String ts = null;
        String v1 = null;
        for (String part : signatureHeader.split(",")) {
            String[] keyValue = part.trim().split("=", 2);
            if (keyValue.length != 2) {
                continue;
            }
            if ("ts".equals(keyValue[0].trim())) {
                ts = keyValue[1].trim();
            } else if ("v1".equals(keyValue[0].trim())) {
                v1 = keyValue[1].trim();
            }
        }
        if (!StringUtils.hasText(ts) || !StringUtils.hasText(v1)) {
            return false;
        }
        return Hmac.constantTimeEquals(Hmac.sha256Hex(secret, manifest(dataId, requestId, ts)), v1);
    }

    static String manifest(String dataId, String requestId, String ts) {
        StringBuilder manifest = new StringBuilder();
        if (StringUtils.hasText(dataId)) {
            String id = dataId.matches("[A-Za-z0-9]+") ? dataId.toLowerCase() : dataId;
            manifest.append("id:").append(id).append(';');
        }
        if (StringUtils.hasText(requestId)) {
            manifest.append("request-id:").append(requestId).append(';');
        }
        manifest.append("ts:").append(ts).append(';');
        return manifest.toString();
    }
}
