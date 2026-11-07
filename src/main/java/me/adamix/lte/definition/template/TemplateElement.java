package me.adamix.lte.definition.template;

import me.adamix.kidl.diagnostic.FileSpan;

import java.util.Optional;

public sealed interface TemplateElement {
    record Text(
            String value,
            Optional<String> collapseIfEmpty,
            FileSpan span
    ) implements TemplateElement {}
    record Blank(Optional<String> collapseIfEmpty) implements TemplateElement {}
    record Variable(
            String name,
            Optional<String> display,
            Optional<String> prefix,
            Optional<String> suffix,
            FileSpan span
    ) implements TemplateElement {}
    record Group(
            String name,
            String each,
            Empty empty,
            boolean inline,
            Optional<String> joiner,
            FileSpan span
    ) implements TemplateElement {
        public enum Empty {
            SKIP,
            KEEP;
        }
    }
    record Insert(String sectionName) implements TemplateElement {}
} 
