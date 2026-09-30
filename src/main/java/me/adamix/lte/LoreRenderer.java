package me.adamix.lte;

import me.adamix.lte.api.ResolvedGroup;
import me.adamix.lte.definition.template.LoreTemplateDefinition;
import me.adamix.lte.definition.template.TemplateElement;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class LoreRenderer {
    private final VariableService variableService;
    private final GroupService groupService;

    public LoreRenderer(
            @NotNull VariableService variableService,
            @NotNull GroupService groupService
    ) {
        this.variableService = variableService;
        this.groupService = groupService;
    }

    public @NotNull List<Component> render(
            @NotNull LoreTemplateDefinition template,
            @NotNull ItemStack itemStack
    ) {
        List<Component> lore = new ArrayList<>();

        for (TemplateElement element : template.elements()) {
            switch (element) {
                case TemplateElement.Blank(@Nullable String collapseIfEmpty) -> {
                    if (!shouldRender(collapseIfEmpty, itemStack)) break;
                    lore.add(Component.text(""));
                }
                case TemplateElement.Text(String value, @Nullable String collapseIfEmpty) -> {
                    if (!shouldRender(collapseIfEmpty, itemStack)) break;
                    lore.add(LTEPlugin.MINI_MESSAGE.deserialize("<!italic><white>" + value));
                }
                case TemplateElement.Variable(String name, String prefix, String suffix) -> {
                    var variableValue = variableService.getValue(name, itemStack);
                    if (variableValue.isEmpty()) break;

                    String finalValue = (prefix == null ? "" : prefix)
                            + variableValue.get()
                            + (suffix == null ? "" : suffix);

                    lore.add(LTEPlugin.MINI_MESSAGE.deserialize("<!italic><white>" + finalValue));
                }
                case TemplateElement.Group(
                        @NotNull String name,
                        @NotNull String each,
                        @NotNull TemplateElement.Group.Empty empty,
                        boolean inline,
                        @Nullable String joiner
                ) -> renderGroup(lore, name, each, empty, inline, joiner, itemStack);

                case TemplateElement.Insert insert -> {
                    // Should never reach here. resolveExtensions() replaces every Insert
                    // node with the referenced section's actual elements at load time.
                    // Left as a no-op instead of throwing, in case a template somehow
                    // skips extension resolution (e.g. rendered directly mid-parse).
                }
            }
        }

        return lore;
    }

    private boolean shouldRender(@Nullable String collapseIfEmptyGroup, @NotNull ItemStack itemStack) {
        if (collapseIfEmptyGroup == null) return true;
        return !groupService.resolve(collapseIfEmptyGroup, itemStack).isEmpty();
    }

    private void renderGroup(
            @NotNull List<Component> lore,
            @NotNull String name,
            @NotNull String each,
            @NotNull TemplateElement.Group.Empty empty,
            boolean inline,
            @Nullable String joiner,
            @NotNull ItemStack itemStack
    ) {
        ResolvedGroup resolved = groupService.resolve(name, itemStack);

        if (resolved.isEmpty()) {
            switch (empty) {
                case TemplateElement.Group.Empty.Skip _ -> {}
            }
            return;
        }

        if (inline) {
            String sep = joiner != null ? joiner : "";
            List<Component> rendered = new ArrayList<>();
            for (TagResolver resolver : resolved.elements()) {
                rendered.add(LTEPlugin.MINI_MESSAGE.deserialize("<!italic><white>" + each, resolver));
            }
            lore.add(Component.join(
                    JoinConfiguration.separator(LTEPlugin.MINI_MESSAGE.deserialize(sep)),
                    rendered
            ));
        } else {
            for (TagResolver resolver : resolved.elements()) {
                lore.add(LTEPlugin.MINI_MESSAGE.deserialize("<!italic><white>" + each, resolver));
            }
        }
    }
}