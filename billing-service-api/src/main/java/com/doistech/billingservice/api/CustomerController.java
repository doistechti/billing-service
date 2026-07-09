package com.doistech.billingservice.api;

import com.doistech.billingservice.api.BillingApi.CreateCustomerRequest;
import com.doistech.billingservice.api.BillingApi.CustomerResponse;
import com.doistech.billingservice.core.customer.Customer;
import com.doistech.billingservice.core.customer.CustomerService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(@Valid @RequestBody CreateCustomerRequest request) {
        return toResponse(customerService.create(request));
    }

    @GetMapping("/{id}")
    public CustomerResponse getById(@PathVariable UUID id) {
        return toResponse(customerService.getById(id));
    }

    private CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getSourceSystem(),
                customer.getExternalReference(),
                customer.getName(),
                customer.getEmail(),
                customer.isActive()
        );
    }
}
