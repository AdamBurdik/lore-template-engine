package me.adamix.lte.editor;

import lombok.RequiredArgsConstructor;
import me.adamix.lte.GroupService;
import me.adamix.lte.TemplateService;
import me.adamix.lte.VariableService;
import me.adamix.lte.api.LoreTemplateAPI;
import me.adamix.lte.definition.variable.VariableValue;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public final class ItemEditorImpl implements LoreTemplateAPI.ItemEditor {
    private final ItemStack itemStack;
    private final TemplateService templateService;
    private final VariableService variableService;
    private final GroupService groupService;
    private @Nullable String templateId;
    private final Map<String, VariableValue> variables = new HashMap<>(0);
    private final Map<String, List<String>> groups = new HashMap<>(0);

    @Override
    public LoreTemplateAPI.@NonNull ItemEditor template(@NonNull String templateId) {
        this.templateId = templateId;
        return this;
    }

    @Override
    public LoreTemplateAPI.@NonNull ItemEditor variable(@NonNull String variable, @NonNull VariableValue value) {
        variables.put(variable, value);
        return this;
    }

    @Override
    public LoreTemplateAPI.@NonNull ItemEditor group(@NonNull String group, @NonNull List<String> values) {
        groups.put(group, values);
        return this;
    }

    @Override
    public void apply() {
        variables.forEach((name, value) -> variableService.setValue(itemStack, name, value));
        groups.forEach((name, values) -> groupService.set(itemStack, name, values));

        if (templateId != null) {
            templateService.apply(templateId, itemStack);
        } else {
            templateService.rebuild(itemStack);
        }
    }
}
