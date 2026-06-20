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

- **Automatic absorption — and never destructive.** Guide books are consumed from your inventory the moment they arrive — no interaction required. Works with ground pickup, crafting output, smelting output, container-close (quest rewards, FTB popups, chests), direct inventory grants (KubeJS, `/give`), and a low-frequency safety sweep (configurable). A book is only ever removed once it's confirmed stored in your tome — if storage fails, the item is left untouched.
- **Virtual storage.** Unlocked books live in a per-player server-side capability, persisted to disk and synced to the client. No duplicated ItemStacks, no inventory clutter. Corrupt or outdated entries are skipped on load rather than breaking your save.
- **Searchable library UI.** The tome opens to a scrolling list of your books with their real item icons and a live count. **Search** by name, **favorite** any book (right-click) to pin it to the top, and **left-click** to launch the original book's native GUI. A book that fails to open tells you why instead of silently doing nothing.
- **Patchouli support — zero compile-time dependency.** Runic Tome uses reflection to detect Patchouli at runtime and recognise **both** flavors of Patchouli books:
  - Generic `patchouli:guide_book` stacks with `{patchouli:book: "modid:book_id"}` NBT (fast NBT match).
  - Custom-item books declared with `dont_generate_book: true` (e.g. Ars Nouveau's Worn Notebook, Botania's Lexica Botania) — resolved by walking `BookRegistry` and mapping the book's item to its ID.
- **Built-in integrations.** Tinkers' Construct (all six books), Minecraft Comes Alive, Better Animals Plus, Immersive Engineering (manual), and Modonomicon are detected and registered automatically when loaded — plus a keyword catch-all that absorbs guide books from mods with no explicit support.
- **Config- and datapack-driven long tail.** Modpack authors can add any standalone book-like item via `extraBookItemIds` in `runictome-common.toml`, or define books in a datapack at `data/<namespace>/runictome/books/*.json` — no code, and datapack books sync to clients on dedicated servers.
- **Public API & IMC.** Other mods can register their own `GuideSystemAdapter` via the public `RunicTomeAPI.registerAdapter(...)` or via Inter-Mod Communication — including the `register_book` IMC message with an `ImcBook` payload for a single openable book.
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
        "occultism:dictionary_of_spirits",
    ]

    # Keyword catch-all: absorb any item whose registry path matches a
    # documentation keyword. Disable to absorb only explicitly-supported books.
    absorbUnknownBooks = true

    # Mod IDs the catch-all never touches, for mods whose "book"/"tome" items are
    # functional gear rather than documentation.
    bookBlocklistMods = ["irons_spellbooks", "ars_nouveau", "rpglore", "scriptor"]

    # Individual item IDs the catch-all never absorbs (vanilla books are blocked already).
    bookBlocklist = []

    # Item tags whose members are NEVER absorbed by any adapter — the escape hatch
    # for functional items. Append to #runictome:absorb_blocklist from a datapack.
    bookBlocklistTags = ["runictome:absorb_blocklist"]

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

Pickup, crafting, smelting, and container-close absorb books the instant they arrive. The
timer-driven sweep runs once per second by default (`sweepIntervalTicks = 20`) as a safety net for
inventory inserts that bypass Forge events (`/give`, hopper pushes, direct KubeJS grants).

---

## Usage

1. Join a world. A **Runic Tome** appears in your inventory.
2. Pick up, craft, or receive any supported guide book. It is absorbed near-instantly.
3. Right-click the Runic Tome (or press the bound key) to open your library.
4. Type in the search box to filter, **left-click** an entry to launch its native book UI, or **right-click** to favorite it (favorites pin to the top).
5. Scroll through the list. Press Escape or click **Done** to close.

The tome itself has `EPIC` rarity, stacks to 1, and is fire-resistant — losing it is hard, but even if you do, your unlocked books are stored per-player and survive death, item loss, and the loss of the tome.

---

## Supported mods

Out of the box:

- **Patchouli** (and every mod that ships a Patchouli book — Botania, Ars Nouveau, Ice & Fire Delight, etc.)
- **Tinkers' Construct** (all six standard books)
- **Minecraft Comes Alive**, **Better Animals Plus**, **Immersive Engineering** (manual), **Modonomicon**
- A **keyword catch-all** that absorbs guide books from mods with no explicit support (configurable; see `absorbUnknownBooks`). It skips block items, vanilla books, blocklisted namespaces, and items that *look functional* — anything whose path contains `spell`, `scroll`, `caster`, `focus`, `rune`, or `wand`, or that is damageable or enchanted — so functional "tome"/"book" gear is left alone.

Additional books can be enabled via `extraBookItemIds` in the config or a datapack (`data/<namespace>/runictome/books/*.json`). For mod authors building new integrations, see [Integration API](#integration-api) below.

### Forcing or preventing absorption

Two append-friendly item tags give packmakers explicit control over the catch-all, taking precedence **never-absorb > positive tag > heuristic**:

- **`#runictome:guide_books`** — force-absorb an item the heuristic would otherwise skip (e.g. a legitimately-named book the `looksFunctional` check rejects).
- **`#runictome:absorb_blocklist`** — hard opt-out; an item here is never absorbed by any adapter, even if it is also in `guide_books`.

Both ship empty (`"replace": false`); add entries from a datapack at `data/runictome/tags/items/<tag>.json`. Namespace-level exclusions go in the `bookBlocklistMods` config instead.

### Commands

`/runictome` (requires permission level 2):

- `list` — list the books you've unlocked.
- `give` — grant yourself a Runic Tome.
- `unlock <system> <book>` / `lock <system> <book>` — add or remove a book entry.
- `debug dump` — list every registered adapter.
- `debug identify` — explain how the held item would be classified: its id, `absorb_blocklist` membership, the first adapter that would match (and, for the catch-all, which keyword matched), and a final absorb/no-absorb verdict. The fastest way to diagnose why a book is or isn't being absorbed.

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
// Register a whole guide system:
InterModComms.sendTo("runictome", "register_adapter", () -> myAdapterInstance);

// Or register a single openable book (the item is used as icon and opened via its use()):
InterModComms.sendTo("runictome", "register_book",
        () -> new ImcBook(new BookKey(systemId, bookId), new ItemStack(myBookItem)));
```

The `register_adapter` payload must implement `GuideSystemAdapter`; `register_book` accepts an
`ImcBook` (or a plain `BookKey` for a message-only entry). Use `RunicTomeAPI.API_VERSION` to check
compatibility. See `ImcHandler.java` for details.

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
