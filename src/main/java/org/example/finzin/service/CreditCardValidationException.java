package org.example.finzin.service;

/** Thrown when a credit card purchase/payment fails validation (BLOCK-mode limit, or overpayment). */
public class CreditCardValidationException extends RuntimeException {
    public CreditCardValidationException(String message) {
        super(message);
    }
}
