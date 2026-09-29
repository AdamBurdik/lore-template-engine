package me.adamix.lte.definition.template;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public sealed interface TemplateElement {
    record Text(@NotNull String value, @Nullable String collapseIfEmpty) implements TemplateElement {}
    record Blank(@Nullable String collapseIfEmpty) implements TemplateElement {}
    record Variable(
            @NotNull String name,
            @Nullable String prefix,
            @Nullable String suffix
    ) implements TemplateElement {}
    record Group(
            @NotNull String name,
            @NotNull String each,
            @NotNull Empty empty,
            boolean inline,
            @Nullable String joiner
    ) implements TemplateElement {
        public sealed interface Empty {
            record Skip() implements Empty {}
        }
    }
    record Insert(@NotNull String sectionName) implements TemplateElement {}
} 
