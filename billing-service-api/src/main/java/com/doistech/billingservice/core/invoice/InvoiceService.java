package com.doistech.billingservice.core.invoice;

import com.doistech.billingservice.api.BillingApi.CreateInvoiceItemRequest;
import com.doistech.billingservice.api.BillingApi.CreateInvoiceRequest;
import com.doistech.billingservice.core.customer.Customer;
import com.doistech.billingservice.core.customer.CustomerRepository;
import com.doistech.billingservice.shared.BusinessRuleException;
import com.doistech.billingservice.shared.NotFoundException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;

    public InvoiceService(InvoiceRepository invoiceRepository, CustomerRepository customerRepository) {
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
    }

    @Transactional
    public Invoice create(CreateInvoiceRequest request) {
        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessRuleException("invoice must contain at least one item");
        }

        Customer customer = customerRepository.findById(request.customerId())
                .orElseThrow(() -> new NotFoundException("customer not found"));

        List<InvoiceItem> items = request.items().stream()
                .map(this::toItem)
                .toList();

        BigDecimal totalAmount = items.stream()
                .map(InvoiceItem::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("invoice total amount must be greater than zero");
        }

        Invoice invoice = new Invoice();
        invoice.setCustomer(customer);
        invoice.setSourceSystem(request.sourceSystem());
        invoice.setExternalReference(request.externalReference());
        invoice.setDescription(request.description());
        invoice.setDueDate(request.dueDate());
        invoice.setTotalAmount(totalAmount);
        invoice.setStatus(InvoiceStatus.OPEN);
        invoice.setItems(items);
        return invoiceRepository.save(invoice);
    }

    @Transactional(readOnly = true)
    public Invoice getById(UUID id) {
        return invoiceRepository.findDetailedById(id)
                .orElseThrow(() -> new NotFoundException("invoice not found"));
    }

    @Transactional(readOnly = true)
    public Invoice getByReference(String sourceSystem, String externalReference) {
        return invoiceRepository.findBySourceSystemAndExternalReference(sourceSystem, externalReference)
                .orElseThrow(() -> new NotFoundException("invoice not found"));
    }

    @Transactional
    public Invoice cancel(UUID id) {
        Invoice invoice = getById(id);
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new BusinessRuleException("paid invoice cannot be canceled");
        }
        if (invoice.getStatus() == InvoiceStatus.CANCELED) {
            return invoice;
        }

        invoice.setStatus(InvoiceStatus.CANCELED);
        invoice.setCanceledAt(LocalDateTime.now());
        return invoiceRepository.save(invoice);
    }

    private InvoiceItem toItem(CreateInvoiceItemRequest request) {
        InvoiceItem item = new InvoiceItem();
        item.setDescription(request.description());
        item.setQuantity(request.quantity());
        item.setUnitAmount(request.unitAmount());
        item.setTotalAmount(request.quantity().multiply(request.unitAmount()));
        return item;
    }
}
