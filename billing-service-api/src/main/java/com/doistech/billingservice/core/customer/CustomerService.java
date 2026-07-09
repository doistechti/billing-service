package com.doistech.billingservice.core.customer;

import com.doistech.billingservice.api.BillingApi.CreateCustomerRequest;
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
    public Customer create(CreateCustomerRequest request) {
        Customer customer = new Customer();
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
    public Customer getById(UUID id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("customer not found"));
    }
}
