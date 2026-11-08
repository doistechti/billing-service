package com.doistech.billingservice.gateway;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.doistech.billingservice.core.payment.Gateway;
import com.doistech.billingservice.shared.BusinessRuleException;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaymentGatewayRouterTest {

    @Test
    void routesByGateway() {
        PaymentGatewayService mercadoPago = service(Gateway.MERCADO_PAGO);
        PaymentGatewayService pagarMe = service(Gateway.PAGAR_ME);
        PaymentGatewayRouter router = new PaymentGatewayRouter(List.of(mercadoPago, pagarMe));

        assertSame(mercadoPago, router.forGateway(Gateway.MERCADO_PAGO));
        assertSame(pagarMe, router.forGateway(Gateway.PAGAR_ME));
    }

    @Test
    void rejectsGatewayWithoutImplementation() {
        PaymentGatewayRouter router = new PaymentGatewayRouter(List.of(service(Gateway.MERCADO_PAGO)));

        assertThrows(BusinessRuleException.class, () -> router.forGateway(Gateway.PAGAR_ME));
        assertThrows(BusinessRuleException.class, () -> router.forGateway(null));
    }

    @Test
    void rejectsDuplicateImplementations() {
        assertThrows(IllegalStateException.class, () -> new PaymentGatewayRouter(
                List.of(service(Gateway.MERCADO_PAGO), service(Gateway.MERCADO_PAGO))));
    }

    private static PaymentGatewayService service(Gateway gateway) {
        PaymentGatewayService service = mock(PaymentGatewayService.class);
        when(service.gateway()).thenReturn(gateway);
        return service;
    }
}
