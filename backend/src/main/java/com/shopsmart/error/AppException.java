package com.shopsmart.error;

public class AppException extends RuntimeException {
    public final int status;
    public AppException(int status, String message) {
        super(message);
        this.status = status;
    }
}

