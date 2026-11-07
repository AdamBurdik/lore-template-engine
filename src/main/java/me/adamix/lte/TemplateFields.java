package me.adamix.lte;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TemplateFields {
    private static final Pattern FIELD = Pattern.compile("<([A-Za-z0-9_-]+)>");

    private TemplateFields() {
    }

    public static String expand(
            String template,
            Map<String, String> fields
    ) {
        Matcher matcher = FIELD.matcher(template);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String value = fields.get(matcher.group(1));
            matcher.appendReplacement(
                    result,
                    Matcher.quoteReplacement(value != null ? value : matcher.group())
            );
        }

        matcher.appendTail(result);
        return result.toString();
    }

    public static Set<String> referencedFields(String template) {
        Matcher matcher = FIELD.matcher(template);
        Set<String> fields = new LinkedHashSet<>();

        while (matcher.find()) {
            fields.add(matcher.group(1));
        }

        return fields;
    }
}
