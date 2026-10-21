package me.adamix.lte.definition;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class ParserTestSupport {
    private static final Pattern ANSI = Pattern.compile("\u001B\\[[0-9;]*m");

    private ParserTestSupport() {
    }

    public static Path write(
            Path directory,
            String name,
            String content
    ) throws IOException {
        Path file = directory.resolve(name);
        Files.writeString(file, content);
        return file;
    }

    public static Path resource(String name) {
        URL url = ParserTestSupport.class.getResource(name);
        assertNotNull(url, "Missing test resource " + name);

        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException exception) {
            throw new IllegalStateException("Invalid resource path " + name, exception);
        }
    }

    public static Failure captureFailure(ThrowingRunnable action) {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        try (PrintStream capture = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);

            try {
                IllegalStateException error = assertThrows(IllegalStateException.class, action::run);
                return new Failure(stripAnsi(buffer.toString(StandardCharsets.UTF_8)), error);
            } finally {
                System.setOut(original);
            }
        }
    }

    public static void assertFailure(
            Failure failure,
            String exceptionMessage,
            String message
    ) {
        assertEquals(exceptionMessage, failure.error().getMessage());
        assertTrue(
                failure.output().contains(message),
                () -> "Missing diagnostic message `" + message + "` in:\n" + failure.output()
        );
    }

    public static void assertFailure(
            Failure failure,
            String exceptionMessage,
            int line,
            int column,
            String message
    ) {
        assertFailure(failure, exceptionMessage, message);

        String location = ":" + line + ":" + column;
        assertTrue(
                failure.output().contains(location),
                () -> "Missing diagnostic location `" + location + "` in:\n" + failure.output()
        );
    }

    private static String stripAnsi(String text) {
        return ANSI.matcher(text).replaceAll("");
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    public record Failure(
            String output,
            IllegalStateException error
    ) {
    }
}
