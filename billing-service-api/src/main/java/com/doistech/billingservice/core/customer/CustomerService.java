package com.doistech.billingservice.core.customer;

import com.doistech.billingservice.api.BillingApi.CreateCustomerRequest;
import com.doistech.billingservice.shared.ConflictException;
import com.doistech.billingservice.shared.NotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional
    public Customer create(UUID merchantId, CreateCustomerRequest request) {
        if (customerRepository.existsByMerchantIdAndSourceSystemAndExternalReference(
                merchantId, request.sourceSystem(), request.externalReference())) {
            throw new ConflictException("customer already exists for sourceSystem and externalReference");
        }
        Customer customer = new Customer();
        customer.setMerchantId(merchantId);
        customer.setSourceSystem(request.sourceSystem());
        customer.setExternalReference(request.externalReference());
        customer.setName(request.name());
        customer.setEmail(request.email());
        customer.setDocument(request.document());
        customer.setPhone(request.phone());
        customer.setActive(true);
        return customerRepository.save(customer);
    }

    @Transactional(readOnly = true)
    public Customer getById(UUID merchantId, UUID id) {
        return customerRepository.findByIdAndMerchantId(id, merchantId)
                .orElseThrow(() -> new NotFoundException("customer not found"));
    }

    @Transactional(readOnly = true)
    public Customer getByReference(UUID merchantId, String sourceSystem, String externalReference) {
        return customerRepository.findByMerchantIdAndSourceSystemAndExternalReference(merchantId, sourceSystem, externalReference)
                .orElseThrow(() -> new NotFoundException("customer not found"));
    }
}
