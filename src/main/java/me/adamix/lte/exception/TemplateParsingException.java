package me.adamix.lte.exception;

public class TemplateParsingException extends Exception {
    public TemplateParsingException() {
    }

    public TemplateParsingException(String message) {
        super(message);
    }

    public TemplateParsingException(String message, Throwable cause) {
        super(message, cause);
    }

    public TemplateParsingException(Throwable cause) {
        super(cause);
    }
}
