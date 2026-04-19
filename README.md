# Runic Tome

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-brightgreen)]()
[![Forge](https://img.shields.io/badge/Forge-47.3.0%2B-orange)]()
[![Java](https://img.shields.io/badge/Java-17-blue)]()
[![License](https://img.shields.io/badge/License-MIT-lightgrey)]()

**One tome. Every manual.**

Runic Tome is a Minecraft Forge 1.20.1 mod that gives each player a single, persistent book called the **Runic Tome**. When a guide book enters your inventory — by pickup, crafting, quest reward, or command — the tome silently absorbs it. The physical book disappears; a new entry is added to your tome's library. Open the tome any time to browse every manual you've ever collected and launch the original book's UI with one click.

No more dragging eight identical lexicons between ender chests. No more losing your "Materials and You" to a lava pocket. One item, forever.

---

## Features

- **Automatic absorption.** Guide books are consumed from your inventory the moment they arrive — no interaction required. Works with ground pickup, crafting output, smelting output, container-close (quest rewards, FTB popups, chests), direct inventory grants (KubeJS, `/give`), and a per-tick fallback sweep.
- **Virtual storage.** Unlocked books live in a per-player server-side capability, persisted to disk and synced to the client. No duplicated ItemStacks, no inventory clutter.
- **Vanilla-book UI.** The tome opens as a vanilla-styled book (no fullscreen menu). Each page lists your unlocked books as clickable entries; clicking one closes the tome and launches the original book's native GUI.
- **Patchouli support — zero compile-time dependency.** Runic Tome uses reflection to detect Patchouli at runtime and recognise **both** flavors of Patchouli books:
  - Generic `patchouli:guide_book` stacks with `{patchouli:book: "modid:book_id"}` NBT (fast NBT match).
  - Custom-item books declared with `dont_generate_book: true` (e.g. Ars Nouveau's Worn Notebook, Botania's Lexica Botania) — resolved by walking `BookRegistry` and mapping the book's item to its ID.
- **Tinkers' Construct out of the box.** When `tconstruct` is loaded, all six standard books (Materials and You, Puny Smelting, Mighty Smelting, Fantastic Foundry, Encyclopedia of Tinkering, Tinkers' Gadgetry) are registered automatically.
- **Config-driven long tail.** Modpack authors can add any standalone book-like item to the absorption list via `extraBookItemIds` in `runictome-common.toml` without touching code.
- **Public API & IMC.** Other mods can register their own `GuideSystemAdapter` via either the public `RunicTomeAPI.registerAdapter(...)` or via Inter-Mod Communication (`InterModComms.sendTo("runictome", "register_adapter", supplier)`).
- **Given on first join.** The tome is granted automatically the first time a player joins a world — no crafting required.

---

## Installation

### For players

1. Install [Minecraft Forge 47.3.0+](https://files.minecraftforge.net/) for Minecraft 1.20.1.
2. Download the latest `runictome-<version>.jar` from the [Releases page](https://github.com/otectus/runic-tome/releases) or [CurseForge](https://www.curseforge.com/minecraft/mc-mods/runic-tome).
3. Drop the jar into your `mods/` folder.
4. Launch Minecraft. The tome will appear in your inventory the first time you join a world.

### For modpack authors

Runic Tome works out of the box with no configuration, but you can customise its behaviour via `config/runictome-common.toml`:

```toml
[absorption]
    absorbOnPickup = true          # Absorb guide books picked up from the ground
    absorbOnCraft  = true          # Absorb crafted/smelted guide books

[integrations]
    # Extra item IDs to treat as single-item guide books.
    # Unknown or malformed IDs are logged and skipped.
    extraBookItemIds = [
        "immersiveengineering:manual",
        "occultism:dictionary_of_spirits",
    ]

[ui]
    showUnlockToast = true         # Toast notification when a new book is absorbed

[performance]
    # How often (in server ticks) the inventory-sweep fallback runs.
    # Pickup/craft/smelt/container-close are handled separately and always
    # absorb immediately. Set to 1 to restore legacy per-tick sweeping.
    sweepIntervalTicks = 20

[items]
    grantTomeOnFirstJoin = true    # Give the tome on first login (off for quest-kit servers)

[debug]
    verboseLogging = false         # Extra logging for capability sync and adapter resolution
```

Pickup, crafting, smelting, and container-close absorb books the instant they arrive. The timer-driven sweep runs once per second by default (`sweepIntervalTicks = 20`) as a safety net for inventory inserts that bypass Forge events (`/give`, hopper pushes, direct KubeJS grants).

---

## Usage

1. Join a world. A **Runic Tome** appears in your inventory.
2. Pick up, craft, or receive any supported guide book. It is absorbed within one tick.
3. Right-click the Runic Tome (or press the bound key) to open your library.
4. Click any entry to launch its native book UI.
5. Page forward/back with the arrow buttons or the ← → keys. Press Escape or click **Done** to close.

The tome itself has `EPIC` rarity, stacks to 1, and is fire-resistant — losing it is hard, but even if you do, your unlocked books are stored per-player and survive death, item loss, and the loss of the tome.

---

## Supported mods

Out of the box:

- **Patchouli** (and every mod that ships a Patchouli book — Botania, Ars Nouveau, Ice & Fire Delight, etc.)
- **Tinkers' Construct** (all six standard books)

Additional books can be enabled via `extraBookItemIds` in the config. For mod authors building new integrations, see [Integration API](#integration-api) below.

---

## Integration API

Runic Tome exposes a small public API for third-party mods to register their own guide systems.

### Via direct API call (requires Runic Tome as a compile dependency)

```java
import com.otectus.runictome.api.RunicTomeAPI;
import com.otectus.runictome.api.GuideSystemAdapter;

RunicTomeAPI.registerAdapter(new MyGuideAdapter());
```

A `GuideSystemAdapter` provides:

- `systemId()` — a unique `ResourceLocation` identifying your guide system.
- `identify(ItemStack)` — returns an `Optional<BookKey>` if the stack represents one of your books.
- `open(BookKey, Player)` — opens the book's native UI client-side.
- `displayName(BookKey)`, `displayIcon(BookKey)` — for the tome's library listing.
- Optional: `supportsBulkEnumeration()` + `enumerateAll()` to list all known books (used for the "all" tab if you add one).

### Via IMC (no compile dependency on Runic Tome)

```java
InterModComms.sendTo("runictome", "register_adapter", () -> myAdapterInstance);
```

The IMC payload must implement `GuideSystemAdapter`. See `ImcHandler.java` for details.

---

## Building from source

```bash
git clone https://github.com/otectus/runic-tome.git
cd runic-tome
./gradlew build
```

The jar will be placed in `build/libs/`.

For a development client:

```bash
./gradlew runClient
```

---

## Architecture

For a deep dive into the mod's internals — capability design, server-authoritative filtering, adapter registry, reflective Patchouli integration, and more — see [`RUNIC-TOME-ARCHITECTURE.md`](./RUNIC-TOME-ARCHITECTURE.md).

---

## License

MIT — see [`LICENSE`](./LICENSE). You are free to use, modify, and redistribute this mod, including in modpacks, provided attribution is preserved.

---

## Credits

- **Otectus** — author, code, design.
- **vazkii** — for Patchouli, the book framework this mod plays so well with.
- The Forge MDK, Parchment mappings, and the Minecraft Forge community.
