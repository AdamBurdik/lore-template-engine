package me.adamix.lte.api.exception;

public class DefinitionNotFoundException extends RuntimeException {
    public DefinitionNotFoundException(String message) {
        super(message);
    }

    public DefinitionNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}