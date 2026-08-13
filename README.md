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

- **Automatic absorption — never loses a book you don't already have.** Guide books are consumed from your inventory the moment they arrive — no interaction required. Works with ground pickup, crafting output, smelting output, container-close (quest rewards, FTB popups, chests), direct inventory grants (KubeJS, `/give`), and a low-frequency safety sweep (configurable). A book is only ever removed once it's confirmed stored in your tome — if storage fails, the item is left untouched.
  **Absorption is deduplicating.** Exactly one copy of each book is ever retained, and extraction always returns one copy. What happens to the *rest* of a stack is controlled by `absorbWholeStack` (default `true`, matching every release before 0.6.0): absorbing a stack of eight identical lexicons stores one and destroys the other seven, and picking up a book you already have consumes it outright. Set `absorbWholeStack = false` to consume exactly one item per newly-unlocked book and leave the extras physical — duplicates of a book you already own are then never consumed at all.
- **Virtual storage.** Unlocked books live in a per-player server-side capability, persisted to disk and synced to the client. No duplicated ItemStacks, no inventory clutter. Corrupt or outdated entries are skipped on load rather than breaking your save.
- **Searchable library UI.** The tome opens to a scrolling list of your books with their real item icons and a live count. **Search** by name, **favorite** any book (right-click) to pin it to the top, and **left-click** to launch the original book's native GUI. A book that fails to open tells you why instead of silently doing nothing.
- **Physical extraction.** Click **Extract** beside an entry to return one physical book with its retained data and remove it from the virtual library. Extracted copies carry a persistent marker that exempts them from automatic re-absorption — including after an inventory-full drop, and for any other player who later picks them up. **The exemption survives everything short of an explicit operator command:** nothing in normal play clears it, so extract only what you want to keep physical. An operator holding the book can undo a mistaken extraction with `/runictome unmark`.
- **Patchouli support — zero compile-time dependency.** Runic Tome uses reflection to detect Patchouli at runtime and recognise **both** flavors of Patchouli books:
  - Generic `patchouli:guide_book` stacks with `{patchouli:book: "modid:book_id"}` NBT (fast NBT match).
  - Custom-item books declared with `dont_generate_book: true` (e.g. Ars Nouveau's Worn Notebook, Botania's Lexica Botania) — resolved by walking `BookRegistry` and mapping the book's item to its ID.
- **Built-in integrations.** Tinkers' Construct (all six books), Minecraft Comes Alive, Better Animals Plus, Immersive Engineering (manual), and Modonomicon are detected and registered automatically when loaded — plus a keyword catch-all that absorbs guide books from mods with no explicit support.
- **Config- and datapack-driven long tail.** Modpack authors can add any standalone book-like item via `extraBookItemIds` in `runictome-common.toml`, or define books in a datapack at `data/<namespace>/runictome/books/*.json` — no code, and datapack books sync to clients on dedicated servers.
- **Public API & IMC.** Other mods can register their own `GuideSystemAdapter` via the public `RunicTomeAPI.registerAdapter(...)` or via Inter-Mod Communication — including the `register_book` IMC message with an `ImcBook` payload for a single openable book.
- **Audit your pack before it costs a player anything.** `/runictome debug scan` sweeps every registered item and reports exactly what your current configuration *would* absorb, grouped by adapter and by mod, plus the items an adapter wanted but a global exclusion protected. Run it after changing the config or adding mods instead of finding out when someone loses a functional book.
- **Given on first join.** The tome is granted automatically the first time a player joins a world — no crafting required. It is granted **once**; a player who destroys it can be given another with `/runictome give`.

---

## Installation

### For players

1. Install [Minecraft Forge 47.3.0+](https://files.minecraftforge.net/) for Minecraft 1.20.1.
2. Download the latest `runictome-<version>.jar` from the [Releases page](https://github.com/otectus/runic-tome/releases) or [CurseForge](https://www.curseforge.com/minecraft/mc-mods/runic-tome).
3. Drop the jar into your `mods/` folder.
4. Launch Minecraft. The tome will appear in your inventory the first time you join a world.

**On a server, install the same version on both sides.** Runic Tome negotiates a network protocol
version at login, and a mismatch is refused rather than silently tolerated. 0.6.0 uses protocol `5`;
0.5.x used `4`, so a 0.6.0 client cannot join a 0.5.x server or vice versa.

### For modpack authors

Runic Tome works out of the box with no configuration, but you can customise its behaviour via `config/runictome-common.toml`:

```toml
[absorption]
    absorbOnPickup = true          # Absorb guide books picked up from the ground
    absorbOnCraft  = true          # Absorb crafted/smelted guide books
    # true  = consume the whole source stack (one copy is kept, the rest destroyed)
    # false = consume one item per newly-unlocked book and leave the extras physical
    absorbWholeStack = true

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
    bookBlocklistMods = ["irons_spellbooks", "ars_nouveau", "scriptor"]

    # Individual item IDs the catch-all never absorbs (vanilla books are blocked already).
    bookBlocklist = []

    # Extra global exclusions. Built-in audited safety entries remain active even
    # when an older common config does not contain them.
    absorbExclusionItems = []      # Extra item IDs no adapter may absorb
    absorbExclusionMods = []       # Extra namespaces no adapter may absorb

[ui]
    showUnlockToast = true         # DEPRECATED — moved to runictome-client.toml (see below)
    allowBookExtraction = true     # Let players return entries to physical items

[performance]
    # How often (in server ticks) the inventory-sweep fallback runs.
    # Pickup/craft/smelt/container-close are handled separately and always
    # absorb immediately. Set to 1 to restore legacy per-tick sweeping.
    # Players are staggered across the interval rather than all swept on one tick.
    sweepIntervalTicks = 20

    # Server-side cap on favorite toggles per player per second. 0 disables it.
    maxFavoriteTogglesPerSecond = 20

[items]
    grantTomeOnFirstJoin = true    # Give the tome on first login (off for quest-kit servers)

[debug]
    verboseLogging = false         # Extra logging for capability sync and adapter resolution
```

Per-player display preferences live in `config/runictome-client.toml`:

```toml
[ui]
    showUnlockToast = true         # Toast notification when a new book is absorbed
```

**`showUnlockToast` moved in 0.6.0.** It is a per-player display preference, and Forge never syncs
COMMON configs, so setting it on a dedicated server never affected any player. It now lives in the
client config. The old common option is still read for one release so an existing `false` is not
silently reset — while both exist, a `false` in *either* file suppresses the toast. Set the client
option; the common one will be removed in a future release.

Pickup, crafting, smelting, and container-close absorb books the instant they arrive. The
timer-driven sweep runs once per second by default (`sweepIntervalTicks = 20`) as a safety net for
inventory inserts that bypass Forge events (`/give`, quest-reward grants, direct KubeJS inserts).

**When config changes take effect.** Every option above is applied on config load *and* on config
reload — including `extraBookItemIds`, `absorbUnknownBooks`, `bookKeywords`, `bookBlocklist` and
`bookBlocklistMods`, whose adapters are rebuilt from scratch each time. Removing an entry removes
its adapter; setting `absorbUnknownBooks = false` removes the keyword catch-all. Books already
absorbed under an older configuration are not revoked — use `/runictome purge` for that.

---

## Usage

1. Join a world. A **Runic Tome** appears in your inventory.
2. Pick up, craft, or receive any supported guide book. It is absorbed near-instantly.
3. Right-click the Runic Tome (or press the bound key) to open your library.
4. Type in the search box to filter, **left-click** an entry to launch its native book UI, **right-click** to favorite it, or click **Extract** to return one physical copy.
5. Scroll through the list. Press Escape or click **Done** to close.

The tome itself has `EPIC` rarity, stacks to 1, and is fire-resistant — losing it is hard, but even if you do, your unlocked books are stored per-player and survive death, item loss, and the loss of the tome.

---

## Supported mods

Out of the box:

- **Patchouli** (and every mod that ships a Patchouli book — Botania, Ars Nouveau, Ice & Fire Delight, etc.)
- **Tinkers' Construct** (all six standard books)
- **Minecraft Comes Alive**, **Better Animals Plus**, **Immersive Engineering** (manual), **Modonomicon**
- A **keyword catch-all** that absorbs guide books from mods with no explicit support (configurable; see `absorbUnknownBooks`). It skips block items, vanilla books, blocklisted namespaces, stateful NBT stacks, and names associated with spells, skills, leveling/XP, summoning, abilities, or upgrades, so functional "tome"/"book" gear is left alone.

Midnight Apocalypse 3.2 compatibility includes permanent hard exclusions for mutable, consumable,
storage, teleport, summoning, combat, and admin-tool books found by scanning all 261 exact manifest
files. These built-in protections remain active when upgrading with an older common config.

Additional books can be enabled via `extraBookItemIds` in the config or a datapack (`data/<namespace>/runictome/books/*.json`). For mod authors building new integrations, see [Integration API](#integration-api) below.

### Forcing or preventing absorption

Two append-friendly item tags give packmakers explicit control over the catch-all, taking precedence **never-absorb > positive tag > heuristic**:

- **`#runictome:guide_books`** — force-absorb an item the heuristic would otherwise skip (e.g. a legitimately-named book the `looksFunctional` check rejects).
- **`#runictome:absorb_blocklist`** — hard opt-out; an item here is never absorbed by any adapter, even if it is also in `guide_books`.

Both ship empty (`"replace": false`); add entries from a datapack at `data/runictome/tags/items/<tag>.json`. Namespace-level exclusions go in the `bookBlocklistMods` config instead.

### Auditing a modpack

The keyword catch-all is deliberately broad, so the safety question for a pack is *"which items in
**my** pack does my configuration actually absorb?"* Answer it before shipping:

1. Start the pack and run **`/runictome debug scan`**. It classifies every registered item through
   the same code path absorption uses, and prints how many items would be absorbed, broken down by
   adapter and by mod.
2. Read the full report it writes to **`runictome-scan.txt`** in the server directory. Items are
   grouped by mod, each naming the adapter that would claim it, followed by the items an adapter
   claimed but a global exclusion protected — that second list is what your exclusion entries are
   currently buying you.
3. Anything in the first list that is *functional gear rather than documentation* goes into
   `absorbExclusionItems` / `absorbExclusionMods` (hard, all adapters) or the
   `#runictome:absorb_blocklist` tag.
4. Re-run the scan. Config changes apply on reload, so you do not need to restart between passes.
5. Use **`/runictome debug identify`** while holding a specific item to see why one particular item
   is or isn't classified as a book, and **`/runictome debug scan <namespace>`** to review a single
   mod in chat.

If a functional book was already absorbed before you excluded it, **`/runictome purge`** lists the
affected library entries and `purge confirm` removes them.

### Commands

`/runictome` (requires permission level 2). Every player-scoped subcommand takes an optional
`<target>` player, so operators can inspect and repair another player's library **from the server
console**. Omit it to act on yourself. All output is localized.

- `list [<page>]` / `list <target> [<page>]` — list unlocked books, 20 per page, favorites marked. A bare number is read as a page and a name as a player; a player whose name is only digits is reachable with a selector such as `@a[name="5"]`.
- `give [<target>]` — grant a Runic Tome.
- `unlock <system> <book> [<target>]` / `lock <system> <book> [<target>]` — add or remove a book entry.
- `purge [<target>]` — list library entries that the *current* global exclusion lists would no longer absorb (matched by book id and by retained stack). `purge confirm [<target>]` removes exactly the entries that were listed. This is the recovery path for a save that absorbed a functional book before it was hard-excluded; extract anything you want to keep first.
- `unmark [<target>]` — clear the extraction exemption from the held book so it can be absorbed again. The only thing that clears the marker; use it to undo a mistaken extraction.
- `debug dump` — list every registered adapter with its priority.
- `debug identify [<target>]` — explain how the held item would be classified: its id, `absorb_blocklist` membership, the first adapter that would match (and, for the catch-all, which keyword matched), and a final absorb/no-absorb verdict. The fastest way to diagnose why a book is or isn't being absorbed.
- `debug scan [<namespace>]` — **audit the whole pack before players lose anything.** Sweeps every registered item and reports exactly what the current configuration would absorb, grouped by adapter and by mod, plus the items an adapter wanted but a global exclusion protected. Writes the full report to `runictome-scan.txt` in the server directory; pass a namespace to list one mod's items in chat. Run this after changing the config or adding mods.

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

The jar will be placed in `build/libs/`. Every push and pull request is built and tested by
[`.github/workflows/build.yml`](.github/workflows/build.yml).

Builds are reproducible: archives use a fixed file order and no file timestamps, and the jar
manifest carries an `Implementation-Timestamp` only when `SOURCE_DATE_EPOCH` is set (as CI does,
from the commit date). Identical sources therefore produce an identical jar.

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
