package com.kt.terraform.exception;

public class AzureAuthException extends RuntimeException {

    public AzureAuthException(String message) {
        super(message);
    }

    public AzureAuthException(String message, Throwable cause) {
        super(message, cause);
    }
} 