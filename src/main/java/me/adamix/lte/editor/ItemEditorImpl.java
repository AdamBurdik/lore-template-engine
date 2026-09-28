package me.adamix.lte.editor;

import lombok.RequiredArgsConstructor;
import me.adamix.lte.GroupService;
import me.adamix.lte.TemplateService;
import me.adamix.lte.VariableService;
import me.adamix.lte.api.LoreTemplateAPI;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public final class ItemEditorImpl implements LoreTemplateAPI.ItemEditor {
    private final @NotNull ItemStack itemStack;
    private final @NotNull TemplateService templateService;
    private final @NotNull VariableService variableService;
    private final @NotNull GroupService groupService;
    private @Nullable String templateId;
    private final @NotNull Map<String, String> variables = new HashMap<>(0);
    private final @NotNull Map<String, List<String>> groups = new HashMap<>(0);

    @Override
    public @NotNull LoreTemplateAPI.ItemEditor template(@NotNull String templateId) {
        this.templateId = templateId;
        return this;
    }

    @Override
    public @NotNull LoreTemplateAPI.ItemEditor variable(@NotNull String variable, @NotNull String value) {
        variables.put(variable, value);
        return this;
    }

    @Override
    public @NotNull LoreTemplateAPI.ItemEditor group(@NotNull String group, @NotNull List<String> values) {
        groups.put(group, values);
        return this;
    }

    @Override
    public void apply() {
        variables.forEach((name, value) -> variableService.set(itemStack, name, value));
        groups.forEach((name, values) -> groupService.set(itemStack, name, values));

        if (templateId != null) {
            templateService.apply(templateId, itemStack);
        } else {
            templateService.rebuild(itemStack);
        }
    }
}
