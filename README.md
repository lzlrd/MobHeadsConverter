# MobHeadsConverter

Paper plugin that turns heads from Vanilla Tweaks' More Mob Heads into the matching heads from [Jodek's Mob Heads](https://modrinth.com/datapack/mob-heads), and old player heads into the heads [Player Drops Head](https://modrinth.com/datapack/player-drops-head) drops, for servers that switched packs.

A converted head is the head Mob Heads' loot table would drop today: same texture, name, note block sound and rarity colour. It stacks with new drops and counts for Mob Heads' collection advancements. A name given on an anvil is kept.

Heads convert where they turn up:

- player inventories and ender chests, on join
- chests, barrels, shulker boxes and other containers, when opened
- dropped heads, when picked up
- placed heads, item frames, armour stands, mob equipment and dropped items, when their chunk loads
- heads inside shulker boxes and bundles, wherever those are found

Everything but pickups is logged, for example `Converted 3 old heads in Steve's inventory`.

## What converts

197 of Vanilla Tweaks' 213 heads have a counterpart in Mob Heads 5.3.1. These stay as they are because Mob Heads has no equivalent: creaking, cold strider, sulfur cube, the generic tropical fish, both wither projectiles and the nine angry wolves.

- Six faces are used by both packs (dolphin, snowy fox, parched, cold pig, warm pig, plain villager). A head with one of these converts only while it still has its Vanilla Tweaks name, so Mob Heads' own heads are never touched. Placed, these convert unless the skull has a name, since only Mob Heads' heads keep one when placed.
- Vanilla Tweaks uses one face for the leatherworker, mason and nitwit villagers, and one for their zombie versions. The item name picks the profession. A placed one has no name and becomes the plain villager.
- The rarity colour follows Mob Heads' current `mob_heads.rarity` score for that mob, as new drops do.

## Player heads

A player head with a lore line starting with "Killed by", such as one from an older player head pack or `/give @s player_head[profile={name:"Steve"},lore=["Killed by Alex"]]`, becomes the head Player Drops Head 3.7.1 drops:

- the "Killed by" line goes; other lore lines stay
- it's named after the player, as Player Drops Head names it, unless it already has a name
- a name-only profile becomes the player's full profile with their current skin, looked up once in the background, so the head stops changing when they change skin; if the player can't be found, the name-only profile stays

Placed player heads convert when broken and picked up.

## Install

1. Install Mob Heads 5.3.1 and lib-5555ff, and Player Drops Head 3.7.1 if you use it, on a Paper 26.3 server.
2. Put `MobHeadsConverter-1.1.jar` from [Releases](https://github.com/lzlrd/MobHeadsConverter/releases) in `plugins/` and restart.

There is nothing to configure. Leave it installed: chunks nobody has visited since the switch still hold old heads.

## Build

```sh
./gradlew build
```

Needs JDK 25. Writes `build/libs/MobHeadsConverter-1.1.jar`.

## Regenerate the mapping

`src/main/resources/mapping.json` pairs each Vanilla Tweaks texture with its Mob Heads head. To rebuild it after either pack changes, unzip both packs and run:

```sh
python3 -I tools/extract.py more-mob-heads/ mob-heads/ heads.json
python3 -I tools/mapper.py heads.json pairs.json
python3 -I tools/make_mapping.py heads.json pairs.json src/main/resources/mapping.json
```

`mapper.py` prints every head it can't pair. Check new ones by eye before shipping, and update `FROM` and `TO` in `make_mapping.py`.

## Licence

[PolyForm Noncommercial 1.0.0](LICENSE.md). `mapping.json` holds head textures and names from Jodek's Mob Heads, under the MIT licence in [NOTICE.txt](src/main/resources/NOTICE.txt).
