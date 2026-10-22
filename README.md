# Lore Template Engine

A Paper plugin for defining custom item lore and tooltip layouts through config.

Tag an item with a template, and its lore is generated from that template whenever the item changes (crafting, enchanting, admin commands, ...).

![Extended vanilla lore with custom tooltip style](https://github.com/AdamBurdik/lore-template-engine/blob/main/github/extended-vanilla-tooltip-style.png?raw=true)

## How it works

The config uses [KDL](https://kdl.dev) and has four parts:

- **Variables**: single values read from an item (`rarity`, `item_level`)
- **Groups**: lists of values read from an item (`enchantments`, `attributes`, `tags`)
- **Transformers**: map values to `color`, `icon`, and `label` fields
- **Templates**: arrange variables, groups, and transformer output into a lore layout

## Examples

**A transformer:**

```kdl
transformer "rarity_lookup" {
	entry "common" {
		color "<gray>"
		label "Common"
	}
	entry "uncommon" {
		color "<green>"
		label "Uncommon"
	}
	entry "rare" {
		color "<blue>"
		label "Rare"
	}
	entry "epic" {
		color "<light_purple><bold>"
		label "Epic"
	}
	entry "legendary" {
		color "<gold><bold>"
		label "Legendary"
	}
	fallback {
		color "<gray>"
		label "Unknown"
	}
}
```

**A variable using a transformer:**

```kdl
variable "rarity" {
	source "pdc" key="rarity" type="string" default="common"
	transformer "rarity_lookup"
}
```

When a variable uses a transformer, its `display` is required in the template because the raw value is otherwise not shown:

```kdl
var "rarity" display="<color><label>"
```

**A template:**

```kdl
template "weapon_default" {
    var "rarity" display="<color><label>"

    blank collapse-if-empty="enchantments"
    group "enchantments" each="<gray><ench_name> <ench_level_roman>" empty="skip"

    text "<red>Hello, World"
}
```

Lines marked with `collapse-if-empty` disappear when their group has nothing to show, so an item without enchantments has no leftover gaps.

**Extending a template and adding a tooltip style (resource pack):**

```kdl
template "vanilla_extended" extends="vanilla" tooltip-style="simple" {
    group "description" each="<gray><line>" empty="skip"
    var "rarity" display="<color><label>"
}
```

Transformers can also be bound to groups. Builtin groups use `key` to name the field being transformed, while PDC groups transform their stored values directly:

```kdl
group "styled_enchantments" {
	builtin "enchantments"
	transformer "ench_style" key="ench_id"
	// exposes: <color>, <icon>, <ench_name>, <ench_level>, <ench_level_roman>
}
```

Values without an entry use the transformer's `fallback`. If there is no fallback, the variable or group entry is omitted.

## Error messages

Config is parsed with kidl, which turns config mistakes into compiler-style diagnostics instead of a generic "invalid config" at startup:

- the exact file, line, and column
- the offending source line
- a caret underline on the exact node
- a short label explaining what was expected
- optional notes and a `help:` line for fixes

For example, a misspelled source produces:

```text
error: unknown variable source `config`
 --> plugins/LTE/variables.kdl:2:2
  |
2 |     source "config" key="rarity"
  |     ^^^^^^^^^^^^^^^ use `pdc` or `computed`
```

Duplicate definitions, missing group sources, unknown sections, and invalid transformer keys are reported the same way, with ANSI colors in the server console. Invalid config fails before any lore is rendered, so you see the problem immediately.

## Commands

```
/lte set template weapon_default
/lte set variable rarity epic
/lte group set description Hello World\nThis is second line
```

## Documentation

Full config reference (variables, groups, transformers, templates, extending, tooltip styles) is on the [wiki](https://github.com/AdamBurdik/lore-template-engine/wiki).

## For developers

Other plugins can apply templates and read item data through the developer API. Setup and dependency instructions are on the [wiki](https://github.com/AdamBurdik/lore-template-engine/wiki/Developer%E2%80%90API%E2%80%90Dependency).
