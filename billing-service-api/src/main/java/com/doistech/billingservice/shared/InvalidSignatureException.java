package com.doistech.billingservice.shared;

public class InvalidSignatureException extends RuntimeException {

    public InvalidSignatureException(String message) {
        super(message);
    }
}
