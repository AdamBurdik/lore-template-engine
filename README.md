# Lore Template Engine

[![Modrinth](https://img.shields.io/modrinth/dt/loretemplateengine?logo=modrinth)](https://modrinth.com/plugin/loretemplateengine/)
[![JitPack](https://jitpack.io/v/AdamBurdik/lore-template-engine.svg)](https://jitpack.io/#AdamBurdik/lore-template-engine)

A Paper plugin for making custom item lore (tooltips) from config templates.

Items get a template ID (saved in PDC). The lore is built from that template every time the item changes (crafting, enchanting, admin commands, ...).

## Features

- Lore layouts defined in config
- Templates can extend other templates
- Variables (for example `rarity`) and groups (lists of lines)
- Lines can hide themselves when a group is empty (`collapse-if-empty`)
- Sections you can reuse with `insert`
- Vanilla-style enchantments and attributes
- Nexo support

## Example

```
template "vanilla" {
    section "enchantments" {
		group "enchantments" each="<gray><ench_name> <ench_level_roman>" empty="skip"
	}

	section "attributes" {
		blank collapse-if-empty="attributes"
		text "<gray>When in Main Hand:" collapse-if-empty="attributes-main-hand"
		group "attributes-main-hand" each=" <attr_color><attr_value> <attr_name>" empty="skip"
		// ... same for off hand, head, chest, legs, feet
	}
}

template "vanilla_extended" extends="vanilla" {
	var "rarity"

	insert "enchantments"
	insert "attributes"

	blank collapse-if-empty="description"
	group "description" each="<gray><line>" empty="skip"
}
```

`vanilla_extended` uses everything from `vanilla` and adds a description at the bottom.

## Requirements

- Paper **26.1.2** (recommended). Other versions may work, but they are not tested.

## Nexo

Put this in your items folder, for example `items/flame_sword.yml`:

```yaml
flame_sword:
  material: NETHERITE_SWORD
  itemname: Flame Sword
  Mechanics:
    lte:
      # Which template builds the lore
      template: vanilla_extended
      # One entry for each variable the template reads
      variables:
        rarity: legendary
        item_level: '1'
      # One entry for each group the template reads
      groups:
        tags:
        - fire
        - unique
        description:
        - <gold>A blade forged in the depths
        - <gold>of the nether, still smoldering.
```

More info: [Nexo integration](https://github.com/AdamBurdik/lore-template-engine/wiki/Plugin-Integrations-Nexo)

## More Info

See the [Modrinth page](https://modrinth.com/plugin/loretemplateengine/) or the [wiki](https://github.com/AdamBurdik/lore-template-engine/wiki).

## API

You can use the plugin from your own plugin with JitPack. Check the [JitPack page](https://jitpack.io/#AdamBurdik/lore-template-engine) for the dependency setup and the latest version.

## Contributing and issues

Found a bug or have an idea? Open an [issue](https://github.com/AdamBurdik/lore-template-engine/issues). Pull requests are welcome.

## How to build

You need Java 25 or newer.

```shell
./gradlew build
```

The jar will be in `build/libs/`.

## License

[MIT](./LICENCE)