package com.doistech.billingservice.gateway;

import com.doistech.billingservice.core.payment.Gateway;
import com.doistech.billingservice.shared.BusinessRuleException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Escolhe a implementação de {@link PaymentGatewayService} pelo gateway do merchant.
 */
@Component
public class PaymentGatewayRouter {

    private final Map<Gateway, PaymentGatewayService> services = new EnumMap<>(Gateway.class);

    public PaymentGatewayRouter(List<PaymentGatewayService> implementations) {
        for (PaymentGatewayService implementation : implementations) {
            PaymentGatewayService previous = services.put(implementation.gateway(), implementation);
            if (previous != null) {
                throw new IllegalStateException("duplicate payment gateway implementation for " + implementation.gateway());
            }
        }
    }

    public PaymentGatewayService forGateway(Gateway gateway) {
        PaymentGatewayService service = gateway == null ? null : services.get(gateway);
        if (service == null) {
            throw new BusinessRuleException("unsupported payment gateway");
        }
        return service;
    }
}
