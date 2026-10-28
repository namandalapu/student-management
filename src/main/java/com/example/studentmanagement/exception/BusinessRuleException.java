package com.example.studentmanagement.exception;

/** Thrown when a request is well-formed but breaks a domain rule (for example, enrolling in a full course). */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
