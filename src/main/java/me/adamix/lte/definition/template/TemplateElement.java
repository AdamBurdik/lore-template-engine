package me.adamix.lte.definition.template;

import me.adamix.kidl.diagnostic.FileSpan;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

public sealed interface TemplateElement {
    record Text(
            @NotNull String value,
            @NotNull Optional<String> collapseIfEmpty,
            @NotNull FileSpan span
    ) implements TemplateElement {}
    record Blank(@NotNull Optional<String> collapseIfEmpty) implements TemplateElement {}
    record Variable(
            @NotNull String name,
            @NotNull Optional<String> display,
            @NotNull Optional<String> prefix,
            @NotNull Optional<String> suffix,
            @NotNull FileSpan span
    ) implements TemplateElement {}
    record Group(
            @NotNull String name,
            @NotNull String each,
            @NotNull Empty empty,
            boolean inline,
            @NotNull Optional<String> joiner,
            @NotNull FileSpan span
    ) implements TemplateElement {
        public enum Empty {
            SKIP,
            KEEP;
        }
    }
    record Insert(@NotNull String sectionName) implements TemplateElement {}
} 
