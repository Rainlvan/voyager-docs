package com.voyager.docs.service;

public class LoginRateLimitException extends RuntimeException {
    public LoginRateLimitException(String message) {
        super(message);
    }
}
