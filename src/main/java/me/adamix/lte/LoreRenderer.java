package me.adamix.lte;

import me.adamix.kidl.diagnostic.FileSpan;
import me.adamix.lte.api.ResolvedGroup;
import me.adamix.lte.definition.template.LoreTemplateDefinition;
import me.adamix.lte.definition.template.TemplateElement;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
                case TemplateElement.Blank(Optional<String> collapseIfEmpty) -> {
                    if (!shouldRender(collapseIfEmpty, itemStack)) break;
                    lore.add(Component.text(""));
                }
                case TemplateElement.Text(
                        String value,
                        Optional<String> collapseIfEmpty,
                        FileSpan ignored
                ) -> {
                    if (!shouldRender(collapseIfEmpty, itemStack)) break;
                    lore.add(LTEPlugin.MINI_MESSAGE.deserialize("<!italic><white>" + value));
                }
                case TemplateElement.Variable(
                        String name,
                        Optional<String> display,
                        Optional<String> prefix,
                        Optional<String> suffix,
                        FileSpan ignored
                ) -> {
                    var resolved = variableService.resolve(name, itemStack);
                    if (resolved.isEmpty()) break;

                    String value = display
                            .map(layout -> TemplateFields.expand(layout, resolved.get().fields()))
                            .orElse(resolved.get().raw());

                    String finalValue = prefix.orElse("")
                            + value
                            + suffix.orElse("");

                    lore.add(LTEPlugin.MINI_MESSAGE.deserialize("<!italic><white>" + finalValue));
                }
                case TemplateElement.Group(
                        @NotNull String name,
                        @NotNull String each,
                        @NotNull TemplateElement.Group.Empty empty,
                        boolean inline,
                        Optional<String> joiner,
                        FileSpan ignored
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

    private boolean shouldRender(@NotNull Optional<String> collapseIfEmptyGroup, @NotNull ItemStack itemStack) {
        if (collapseIfEmptyGroup.isEmpty()) return true;
        return !groupService.resolve(collapseIfEmptyGroup.get(), itemStack).isEmpty();
    }

    private void renderGroup(
            @NotNull List<Component> lore,
            @NotNull String name,
            @NotNull String each,
            @NotNull TemplateElement.Group.Empty empty,
            boolean inline,
            @NotNull Optional<String> joiner,
            @NotNull ItemStack itemStack
    ) {
        ResolvedGroup resolved = groupService.resolve(name, itemStack);

        if (resolved.isEmpty()) {
            switch (empty) {
                case SKIP, KEEP -> {}
            }
            return;
        }

        if (inline) {
            String sep = joiner.orElse("");
            List<Component> rendered = new ArrayList<>();
            for (ResolvedGroup.Entry entry : resolved.entries()) {
                String line = TemplateFields.expand(each, entry.fields());
                rendered.add(LTEPlugin.MINI_MESSAGE.deserialize("<!italic><white>" + line, entry.resolver()));
            }
            lore.add(Component.join(
                    JoinConfiguration.separator(LTEPlugin.MINI_MESSAGE.deserialize(sep)),
                    rendered
            ));
        } else {
            for (ResolvedGroup.Entry entry : resolved.entries()) {
                String line = TemplateFields.expand(each, entry.fields());
                lore.add(LTEPlugin.MINI_MESSAGE.deserialize("<!italic><white>" + line, entry.resolver()));
            }
        }
    }
}