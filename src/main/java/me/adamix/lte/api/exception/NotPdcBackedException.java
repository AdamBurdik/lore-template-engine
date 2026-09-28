package me.adamix.lte.api.exception;

public class NotPdcBackedException extends RuntimeException {
    public NotPdcBackedException(String message) {
        super(message);
    }

    public NotPdcBackedException(String message, Throwable cause) {
        super(message, cause);
    }
}