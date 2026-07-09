package com.doistech.billingservice.core.invoice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.doistech.billingservice.api.BillingApi.CreateInvoiceItemRequest;
import com.doistech.billingservice.api.BillingApi.CreateInvoiceRequest;
import com.doistech.billingservice.core.customer.Customer;
import com.doistech.billingservice.core.customer.CustomerRepository;
import com.doistech.billingservice.shared.BusinessRuleException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private CustomerRepository customerRepository;

    @InjectMocks
    private InvoiceService invoiceService;

    @Test
    void shouldCalculateInvoiceTotalFromItems() {
        UUID customerId = UUID.randomUUID();
        Customer customer = new Customer();
        customer.setName("Joao");

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Invoice invoice = invoiceService.create(new CreateInvoiceRequest(
                customerId,
                "MATER_ECCLESIAE",
                "ALUNO-123-MENSALIDADE-2026-07",
                "Mensalidade Julho/2026",
                LocalDate.of(2026, 7, 15),
                List.of(
                        new CreateInvoiceItemRequest("Mensalidade", BigDecimal.ONE, new BigDecimal("150.00")),
                        new CreateInvoiceItemRequest("Taxa", BigDecimal.ONE, new BigDecimal("5.00"))
                )));

        assertEquals(new BigDecimal("155.00"), invoice.getTotalAmount());
        assertEquals(InvoiceStatus.OPEN, invoice.getStatus());
        assertEquals(2, invoice.getItems().size());
    }

    @Test
    void shouldRejectInvoiceWithoutItems() {
        CreateInvoiceRequest request = new CreateInvoiceRequest(
                UUID.randomUUID(),
                "MATER_ECCLESIAE",
                "ALUNO-123-MENSALIDADE-2026-07",
                "Mensalidade Julho/2026",
                LocalDate.of(2026, 7, 15),
                List.of());

        assertThrows(BusinessRuleException.class, () -> invoiceService.create(request));
    }

    @Test
    void shouldRejectCancelPaidInvoice() {
        UUID invoiceId = UUID.randomUUID();
        Invoice invoice = new Invoice();
        invoice.setStatus(InvoiceStatus.PAID);

        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(invoice));

        assertThrows(BusinessRuleException.class, () -> invoiceService.cancel(invoiceId));
    }
}
