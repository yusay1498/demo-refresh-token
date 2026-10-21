package com.yusay.api.application.exception;

public class UserAuthoritiesUnavailableException extends RuntimeException {
    public UserAuthoritiesUnavailableException(String username, Throwable cause) {
        super("Could not retrieve authorities for user: " + username, cause);
    }
}
