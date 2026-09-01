# Changelog

All notable changes to Runic Tome are documented here. Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); this project uses [Semantic Versioning](https://semver.org/).

## [1.0.0] - 2026-09-01

The tome finally looks like part of Minecraft. Every pixel of its screen used to be drawn in code
with `fill()` and a hardcoded brown; it is now a proper textured, vanilla-style container window.

**Upgrading.** Nothing under the hood moved. The network protocol stays `6` and the public API
stays `4`, so a 1.0.0 client and a 0.9.0 server still talk to each other; saves, the config schema
and datapacks are all untouched. The 1.0.0 number marks the interface settling down, not a break.

The one thing that can surprise you is a **resource pack**: this release ships the mod's first GUI
texture, `runictome:textures/gui/runic_tome.png`, and redraws the Curios slot icon
`runictome:textures/slot/empty_runic_tome_slot.png`. A pack that overrode the old slot icon will
still apply and will simply look like the old placeholder.

### Added

- **A textured, vanilla-style GUI.** The tome opens as a fixed 300x214 panel centred on screen,
  the way vanilla's own container screens do, instead of stretching to fill the window. Panel,
  troughs, buttons, scrollbar, row backgrounds and glyphs all come from a single 256x256 sprite
  sheet.

  Every colour in that sheet was sampled out of vanilla 1.20.1's own textures rather than picked by
  eye: `generic_54.png` gives the panel (`#000000` outline, 2px `#FFFFFF` bevel, `#C6C6C6` face,
  2px `#555555` shadow) and the troughs (`#373737` / `#8B8B8B` / `#FFFFFF`), and `widgets.png`
  gives the buttons — including the detail that a hovered vanilla button swaps its outline to
  white. The panel corner is chamfered because vanilla's is.

  The panel size is not arbitrary. `Window.calculateScale` guarantees at least a 320x240 scaled
  GUI at every legal scale, and 300x214 leaves a margin inside that at the floor. Its height is
  what makes exactly six 22px rows fit the well.

- **Copy and Extract are icon buttons with tooltips.** They were two hand-filled rectangles with
  text labels eating about 100px of every row. As 16x16 icons with hover states and two-line
  tooltips they give the book name column 205px instead of 138.

  New strings: `gui.runictome.copy.tooltip`, `gui.runictome.extract.tooltip`,
  `gui.runictome.favorite`, `gui.runictome.favorite.add`, `gui.runictome.favorite.remove`.

- **The favourite star is a sprite, not a character.** Its appearance no longer depends on the
  player's font or resource pack. A hollow outline star now appears on the hovered row whether or
  not the book is favourited, which is the only hint that right-click toggles it; the gutter is a
  fixed width either way, so names line up down the list. Each row also gained a vanilla slot
  well behind its book icon.

- **A real Curios slot icon.** The old one was a solid 8x8 grey block — a placeholder. It is now a
  closed book with a spine and a rune mark on the cover, drawn to the same specification as
  Curios' own eleven icons: 16x16, a 1px hollow outline in exactly `#555555`, inset from the
  border, 52 opaque pixels inside their 32-54 range.

### Changed

- The bottom buttons are drawn from the mod's sheet rather than vanilla's `widgets.png`. Only
  `renderWidget` is overridden, so the click sound, keyboard activation, focus handling and
  narration are still vanilla's.
- Book names are clipped once when a row is built instead of every frame for every visible row,
  and the unlocked-count component is rebuilt only when the count changes. `ClientDataCache.size()`
  is called every frame by design, so the things around it should not allocate.
- The empty-library message now wraps. At roughly 340px it was always wider than the screen it was
  centred on and had been clipping since it was introduced.

### Fixed

- Resizing the window while a search was active left the list filtered behind an apparently empty
  search box: `Screen.resize` re-runs `init()`, which built a fresh `EditBox` while the filter
  field kept the old query. The query and the scroll position now survive a resize.
- The book list was being rendered twice per frame — once as a registered renderable widget and
  again by an explicit call in `render()`.
- Clicking a row no longer paints vanilla's white-and-black selection rectangles over it. Selection
  was always being set on click; it was simply never meant to be drawn that way.

### Internal

- GUI art is generated, not hand-drawn: `tools/gui/generate_gui_sheet.py` is the palette and
  geometry specification in executable form and emits both PNGs deterministically. It verifies its
  own output before writing — exact-palette pixels only, no overlapping sprite rects, and uniform
  nine-slice edge strips and centres. That last one matters because `blitRepeating` tiles those
  regions and samples the centre of a partial tile, so a non-uniform strip produces a seam that
  shows up at one panel width and not another. `--check` re-verifies the committed PNGs.
- `GuiSheetTest` asserts the same properties against the shipped sheet, including that it is
  exactly 256x256 — `blitNineSliced` hardcodes that texture size in 1.20.1, so any other size
  silently rescales every UV.
- The textured scrollbar is drawn in `renderDecorations`, not by overriding `render()`.
  `AbstractSelectionList` paints its scrollbar as three `fill()` rects with no hook of its own, and
  its `render()` is also what assigns the private `hovered` field, so overriding it outright would
  cost every row its hover state.
- Row hit-testing is recomputed from the list rather than cached during `render()`, so it no longer
  depends on the row having been drawn at least once.

## [0.9.0] - 2026-08-21

Two answers to the same complaint: the tome eats books you wanted to keep.

**Upgrading.** The network protocol moves `5` -> `6` and the public API `3` -> `4`, so client and
server must both be on 0.9.0 — a mismatched peer is refused at connect rather than allowed in to
misread the three new message ids. The API change is additive (new `default` interface methods and
one new static; `RunicTomeAPI.Delegate` is unchanged), so integrators compiled against `3` still
link. Saves gain one boolean that defaults to `false`; existing worlds load unchanged and no
migration runs. The config schema gains two keys under `[ui]`. Datapacks are unaffected.

### Added

- **Copy a book without giving up the entry.** Every row in the tome now has a **Copy** button
  beside **Extract**. Copy prints one physical book — full original data, exactly as Extract
  would — and *keeps* the library entry, charging `bookCopyCost` vanilla books for it (default one;
  creative-mode players are never charged).

  The copy carries the same never-re-absorb marker an extracted book does, and that is not a
  nicety: the book is still in the library, so an unmarked copy would be identified as an
  already-owned duplicate and destroyed by the next inventory sweep roughly a second later. Marked
  copies are what make a library room full of item frames, lecterns and shelves possible.

  Unlike Extract, Copy leaves the screen open — furnishing a shelf means clicking it several times.

  New config in `[ui]`: `allowBookCopying` (default `true`) and `bookCopyCost` (default `1`,
  range `0`-`64`).

- **A per-player absorption pause.** An **Absorb: On / Off** button at the bottom of the tome
  screen stops the tome taking books that player picks up, crafts, smelts, or is merely carrying,
  so a chest of dungeon loot can be sorted in peace. The setting is per player, is saved, and
  survives death and dimension changes.

  It deliberately does **not** cover deliberate absorption: crafting a book into the tome and
  `/runictome unlock` both still work while paused. This is the same distinction
  `AbsorptionPolicy.isExcludedForExplicitAction` already draws — pausing means "stop taking things
  on your own", not "refuse what I hand you".

  Exposed to integrators as `RunicTomeAPI.isAbsorptionPaused(Player)` and as
  `isAbsorptionPaused` / `setAbsorptionPaused` on `IRunicTomeData`.

### Changed

- The library list widened from 240px to 280px so the second row button does not squeeze book
  names; the name column keeps roughly the width it had. The scrollbar still clears Minecraft's
  320px minimum scaled GUI width.
- `RequestLimits` now keeps an independent token bucket per request *kind* rather than one shared
  favorites bucket, so spamming one request type cannot throttle a player's legitimate use of
  another. Copies and pause toggles are limited at a fixed 10/second; the favorite allowance is
  still `maxFavoriteTogglesPerSecond`.
- Row narration now names the copy and extract buttons. Both are drawn rather than being focusable
  widgets, so a screen-reader user previously had no way to learn they existed.

### Fixed

- The unlocked-book count at the bottom of the tome screen was drawn underneath the **Done**
  button, and was only invisible because widgets happen to render afterwards. It now sits in the
  gap above the button row.

### Internal

- The give-or-drop hand-off used by extraction and the crafting refund is now one shared
  `ItemDelivery.deliver`, rather than three near-identical copies of the same four lines.
  `TomeGrant` keeps its own version, which tries the Curios slot first and answers with a
  three-valued placement.

## [0.8.0] - 2026-08-21

The Runic Tome can now be worn in a Curios slot instead of occupying an inventory slot for the
whole game.

**Upgrading.** Saves, the network protocol (`5`), the public API (`3`), the config schema and
existing datapacks are unchanged. Curios is **optional** — without it nothing changes, the new
datapack files load into nothing, and no Curios class is ever loaded.

### Added

- **A dedicated Curios slot for the Runic Tome.** With [Curios](https://www.curseforge.com/minecraft/mc-mods/curios)
  installed, the tome gets its own `runic_tome` slot with a book-shaped icon drawn to match the
  built-in Curios slot art. The tome is a permanent, never-stacking, never-consumed item, so it is
  exactly what a wearable slot is for.

  The slot is declared entirely in data (`data/runictome/curios/slots/runic_tome.json`, its player
  binding, and the `curios:runic_tome` item tag), so packs can retexture, reorder or remove it
  without touching code.

  Death behaviour is `ALWAYS_KEEP`, declared both on the slot and on the item — an equipped tome
  stays in its slot through death regardless of the `keepInventory` gamerule or Curios' own
  `keepCurios` setting. It therefore never reaches `LivingDropsEvent`, so the existing
  stash-and-reissue path is simply never entered for it; a tome carried in the inventory still goes
  through that path exactly as before.

  Newly granted tomes — first join, `/runictome give`, and the respawn re-issue — now go to the
  curio slot when it is empty, then the inventory, then the ground.

### Fixed

- **The open-library keybinding ignored equipped tomes.** `playerHoldsTome` scanned only the hands
  and `inv.items`, so a tome in any slot outside the main inventory failed the check and the
  keybinding silently did nothing. It now also asks Curios. This was latent before this release
  (nothing could put the tome outside the inventory) but would have been an immediate regression
  with the new slot.

### Changed

- The three places that granted a tome each repeated the same
  `getInventory().add(...)` / else `drop(...)` idiom; they now share one `TomeGrant` helper, so the
  curio-slot preference applies uniformly.
- Dev-environment only: the `client`, `server` and `gameTestServer` run configurations now set
  `mixin.env.remapRefMap`. Curios' mixin refmap is written in SRG names, and a ForgeGradle dev run
  uses named mappings, so without this Curios fails to apply its mixins and the run dies at startup.
  This affects nobody running a built jar.

## [0.7.0] - 2026-08-21

Books can now be added to the tome deliberately, by crafting, instead of only being absorbed
ambiently — and that channel accepts two kinds of book the ambient one never will.

**Upgrading.** Saves, the network protocol (`5`), datapacks and item tags are unchanged and load
as-is. **The public API version moved from `2` to `3`**: `GuideSystemAdapter` gained two stack-aware
default methods, which is source- and binary-compatible, so existing adapters need no changes. Two
new config options default to `true` and add a capability rather than changing existing behaviour.

### Added

- **Add books to the tome by crafting.** Place the Runic Tome plus one or more books anywhere in a
  crafting grid — the 2x2 inventory grid or a 3x3 table — and the output is the Runic Tome with
  those books filed into your library. Gated by `absorption.absorbViaCrafting` (default `true`).

  This is a deliberate act rather than an ambient sweep, so it accepts a book you previously
  **extracted** from the tome; nothing short of `/runictome unmark` could re-file one before. Every
  other protection still applies in full: hard-excluded and blocklisted items are refused, and an
  unrecognized item anywhere in the grid makes the recipe produce nothing at all, so no ingredient
  is ever consumed by surprise.

  Implemented as a `CustomRecipe` (`runictome:tome_absorb`) rather than a datapack shapeless recipe,
  because "is this a book" is decided at runtime by the adapter chain — config, item tags, datapack
  definitions, IMC and reflective Patchouli detection — none of which is knowable at datapack-load
  time. Packs can disable it with the config option, or override
  `data/runictome/recipes/tome_absorb.json` with a `forge:false` condition.

- **Vanilla books can be stored.** The crafting recipe accepts `book`, `writable_book`,
  `written_book`, `enchanted_book` and `knowledge_book`, under
  `absorption.craftingAcceptsVanillaBooks` (default `true`). These are still **never** absorbed
  ambiently — crafting is the only way in, because eating somebody's Mending book off the ground
  would be indefensible.

  Every stack is stored under its own entry: two written books with different titles are two
  entries, as are a Mending book and a Sharpness V book, because the key carries a stable digest of
  the stack's NBT. Written books and books-and-quills open in the vanilla reader from the tome UI,
  and Extract returns the exact original item.

  Note that the tome still keeps exactly **one** copy per distinct book, so crafting in a second
  identical enchanted book destroys it. To allow written books but not enchanted ones, add
  `minecraft:enchanted_book` to the `#runictome:absorb_blocklist` item tag.

### Changed

- `GuideSystemAdapter` gained `displayName(BookKey, ItemStack)` and `displayIcon(BookKey, ItemStack)`
  default methods, mirroring the existing stack-aware `open` overload. The library UI now passes the
  retained stack when naming and drawing a row, so an entry whose key cannot name it — every written
  book shares one item id — shows its real title. Existing adapters inherit the old behaviour
  unchanged. API version `2` → `3`.

### Fixed

- **The Runic Tome itself was absorbable.** `HeuristicBookAdapter` skips this mod's namespace, but
  `TaggedGuideBookAdapter` and the `extraBookItemIds` adapters do not. A datapack that put
  `runictome:runic_tome` into `#runictome:guide_books`, or a pack that listed it in
  `extraBookItemIds`, would have made the tome absorb *itself* on pickup — and would have destroyed
  the output of any craft that produced one. `runictome:runic_tome` is now in the built-in
  `absorbExclusionItems` defaults, which `rebuild()` always unions in, so it cannot be removed by
  config or by a datapack overriding the tag.

- **The extraction marker could mask a hard exclusion.** `AbsorptionPolicy.evaluate` tests the
  marker first and short-circuits, so a stack that was both extracted *and* globally excluded
  reported only `EXTRACTED`. The new explicit-action gate re-evaluates an unmarked copy, so a
  functional book that had been extracted from a legacy library cannot be crafted back in past the
  exclusion list.

## [0.6.0] - 2026-08-13

Two passes ship together: the safety audit (see [`RUNIC-TOME-AUDIT.md`](./RUNIC-TOME-AUDIT.md) for
the full findings table and evidence), and the follow-up work its roadmap deferred for a product
decision.

**Upgrading.** Saves, the config schema, datapacks, item tags and the public API (`2`) are unchanged
and load as-is. **The network protocol moved from `4` to `5`**, so a 0.6.0 client cannot join a
0.5.x server or vice versa — update both sides together. Two config options gained defaults that
preserve existing behaviour (`absorbWholeStack = true`, `maxFavoriteTogglesPerSecond = 20`), and
`showUnlockToast` moved to a new client config with a one-release compatibility gate, so no existing
setting resets.

### Fixed

- **Unknown item ids were silently accepted everywhere.** Forge's item registry is *defaulted*:
  `ForgeRegistries.ITEMS.getValue(id)` returns `minecraft:air` for an unregistered id, never `null`.
  Nine `item == null` guards were therefore dead code. A typo in `extraBookItemIds` or in a datapack
  `item` field registered a live adapter named "Air" instead of logging a warning; orphaned library
  entries rendered as "Air"; and `runictome.unknown_item` never appeared. All registry lookups now
  go through a `containsKey`-based resolver.
- **The use simulator could corrupt global state shared with every other mod.** `ItemStack.copy()`
  returns the shared `ItemStack.EMPTY` *singleton* for an empty stack, so replaying an empty opener
  wrote `{"runictome:virtual":1b}` onto `ItemStack.EMPTY` process-wide. Empty stacks are now rejected
  before any mutation, and the simulator no longer overwrites the hotbar slot when foreign `use()`
  replaced its contents — the original stack is re-homed or dropped instead of destroying whatever
  the other mod put there.
- **Opening a Patchouli book ran an unrelated item's `use()` on the server.** A Patchouli `BookKey`
  carries a *book* id, not an item id, but the inherited `openServer` default resolved it against the
  item registry — so `botania:lexicon` (both a book id and a real item) got a second, server-side
  activation with its sounds, statistics and cooldowns. `PatchouliGuideAdapter` now overrides
  `openServer` to a no-op; its `open` was always fully client-side.
- **A single broken adapter could abort every item pickup.** `identify` had no per-adapter exception
  isolation, so a third-party, IMC or datapack adapter that threw propagated into the pickup event,
  the inventory sweep and the craft/smelt handlers. Each adapter is now isolated, logged once, and
  skipped; if the absorption policy itself throws, identification fails closed and the item is left
  alone.
- **`extraBookItemIds`, `absorbUnknownBooks`, `bookKeywords`, `bookBlocklist` and
  `bookBlocklistMods` now apply on config reload.** They were snapshotted into adapter instances at
  common setup and silently ignored until a full restart, while the `absorbExclusion*` options next
  to them already reloaded live. The config-derived adapter set is rebuilt and swapped wholesale, so
  removing an entry removes its adapter and disabling `absorbUnknownBooks` removes the catch-all.
- **`SyncBookDefsPacket` pre-sized a list from an untrusted length prefix.** A hostile or corrupted
  server could make the client allocate a two-billion-entry list before reading a single element.
  The count and the optional display name are now bounded.
- **The client library cache is cleared on logout.** It previously survived a disconnect, so joining
  a different world could briefly show and act on the previous world's library.
- **Extraction's last-resort fallback is no longer silent.** Materializing a legacy key-only entry by
  looking its book id up in the item registry only makes sense for item-identity systems; the
  fallback now requires a genuinely registered item and logs which key triggered it.
- Capability attachment checks the event's own gathered providers instead of querying the entity
  mid-gather.
- The unrecognized-book log cap can no longer be exceeded by concurrent pickups.
- **A favorite toggle no longer re-serializes the entire library.** Flipping one bit sent back every
  book key *and* every retained `ItemStack` with its NBT, which a client could repeat without limit —
  cheap to request, expensive to answer. The server now replies with a single fixed-size packet
  carrying that one book's authoritative state, and requests are rate limited per player. Rejected
  requests are still answered with the true state, so the UI's optimistic flip cannot drift out of
  sync without a full re-sync.
- **`showUnlockToast` had no effect when set on a dedicated server.** It is a per-player display
  preference, but it lived in the COMMON spec, which Forge never syncs — so a server operator's value
  governed nobody and each player had to edit their own common config. It now lives in
  `runictome-client.toml`.
- **The exclusion policy and the config-derived adapters are now rebuilt as one server-thread task.**
  They were two separate publishes from Forge's config-watcher thread, so an absorption landing
  between them could evaluate the new exclusion lists against the old heuristic snapshot. Each state
  was individually safe; the pair was not atomic.
- **The inventory sweep no longer runs every online player on the same tick.** A full server paid the
  entire cost in one tick of every interval; players are now staggered across it. Each player is
  still swept exactly once per interval.

### Added

- **`/runictome purge`** — lists library entries that the current global exclusion lists would no
  longer absorb, matched both by book id and by retained source stack; `/runictome purge confirm`
  removes them. The `AbsorptionPolicy.findExcluded`/`purgeExcluded` helpers announced in 0.5.1 had no
  caller, leaving save editing as the only recovery path.
- **`/runictome debug scan [<namespace>]`** — audits a pack *before* players lose a functional book
  rather than after. It sweeps every registered item, classifies it through the real absorption path,
  and reports what the current configuration would absorb (grouped by adapter and by mod) alongside
  the items an adapter claimed but a global exclusion protected. The full report is written to
  `runictome-scan.txt` in the server directory; a namespace argument lists one mod's items in chat.
  Every other diagnostic in the mod explains an item after the fact.
- **`/runictome unmark [<target>]`** — clears the extraction exemption from the held book so it can be
  absorbed again. The marker is permanent by design, which previously meant a mistaken extraction was
  unrecoverable without editing the save.
- **An optional `<target>` player on every player-scoped subcommand.** All of them called
  `getPlayerOrException()`, so the command tree was unusable from a server console and an operator
  had no way to inspect or repair another player's library.
- **Pagination and localization for command output.** `list` pages at 20 entries with favorites
  marked, and every message is a translation key rather than a hardcoded literal.
- **`absorbWholeStack`** — controls what happens to the rest of a stack. Defaults to `true`, the
  behaviour of every earlier release; set it to `false` to consume one item per newly-unlocked book
  and leave duplicates physical.
- **`maxFavoriteTogglesPerSecond`** — per-player cap on favorite-toggle requests. `0` disables it.
- **Reproducible builds.** The jar manifest embedded `new Date()`, so identical sources produced
  different jars. Archives now use a fixed file order and no file timestamps, and the timestamp
  attribute is emitted only from `SOURCE_DATE_EPOCH`.
- **CI** — `.github/workflows/build.yml` builds and tests every push and pull request, uploading test
  results and the jar.
- Optional `patchouli` dependency with `ordering="AFTER"` in `mods.toml`, so Patchouli's
  `BookRegistry` is populated before the adapter initializes.
- `IRunicTomeData.setFavorite(key, favorite)` — an idempotent absolute setter, added as a `default`
  method so no existing implementor breaks and the API version is unchanged. Applying a received
  state must converge, not flip.
- 93 new tests (123 total, from 30 before the audit) covering the defaulted-registry behaviour, the
  `ItemStack.EMPTY` hazard, adapter exception isolation, config-adapter replacement, packet bounds,
  purge/recovery, the deduplicating absorption contract, both stack-consumption modes, the favorite
  sync path, the rate limiter, the sweep schedule, the extraction marker, and the pack scan.

### Changed

- `IRunicTomeData.getBooks()` and `getFavorites()` return immutable snapshots rather than live
  unmodifiable views, so callers can iterate while the library changes. IMC static-book adapters
  hand out copies of their icons and book list instead of internal state.
- **Network protocol `4` → `5`.** `SyncFavoritePacket` added a message id; both sides must agree on
  the id-to-class mapping, so a mismatched peer is refused rather than allowed to misread ids.
- Absorption is **deduplicating**, and by default destructive to duplicates — a stack of eight
  identical books stores one and destroys seven. This was previously undocumented (the README claimed
  absorption was "never destructive"); it is now stated explicitly and is configurable via
  `absorbWholeStack`. The consumption decision moved out of the three event handlers into one shared
  policy, so pickup, craft/smelt and the inventory sweep cannot disagree.
- The extraction exemption marker is documented as surviving everything in normal play, with
  `/runictome unmark` as the only thing that clears it.
- README: corrected the `bookBlocklistMods` example (it listed a non-existent `rpglore` default) and
  the sweep rationale (hoppers cannot insert into a player inventory).
- Root-level `*.jar` and `*.zip` are gitignored, so `git add -A` can no longer commit dev jars or a
  multi-hundred-megabyte modpack archive.
- Removed three unused translation keys; added `runictome.open_no_item`.

## [0.5.2] - 2026-08-06

### Added

- Every library row now has an **Extract** button. Extraction returns one physical copy with its
  retained NBT/capability data, removes the virtual entry, and drops the item safely when the
  inventory is full.
- Extracted stacks carry a private exemption marker so the pickup handler and periodic inventory
  sweep cannot immediately consume them again.
- `allowBookExtraction` lets servers disable extraction while leaving it enabled by default.

### Fixed

- Midnight Apocalypse 3.2's `runicskills:leveling_book` is now a built-in hard exclusion. It remains
  physical, so both XP deposit and XP withdrawal behavior work normally.
- A full scan of all 261 exact manifest files hard-excludes functional/consumable books from Runic
  Skills, Legendary Additions, Alex's Caves, Art of Forging, Aylel RPG Drops, CustomNPCs, Dungeons
  and Combat, Unique Accessories, Cthulhu Fishing, Threateningly Mobs, Celestisynth, D&C x Iron's,
  Stargate Journey, JSG/Aunis, Valoria, Goety, Goety: Awaken, and Wesley's Roguelike Dungeons while
  leaving actual read-only guides eligible for absorption.
- Functional-name detection now rejects empty items named for leveling, XP, skills, summoning,
  abilities, upgrades, or necromancy before they acquire stateful NBT.
- Built-in safety exclusions are unioned with the common config, so existing modpacks receive new
  protections without deleting and regenerating `runictome-common.toml`.

### Changed

- Network protocol is now `4`; clients and servers must both use Runic Tome 0.5.2.

## [0.5.1] — 2026-08-05

A compatibility and absorption-safety release. Runic Tome now preserves the original stack for
recognized books while applying one global exclusion policy before any adapter can consume an
item. It combines cross-adapter safety controls with reliable reopening for stateful guide books.

### Added

- **Centralized absorption policy.** `AbsorptionPolicy` runs before every adapter, so global item,
  namespace, tag, and synthetic-stack exclusions cannot be bypassed by Patchouli, IMC, datapack,
  tagged, configured, or heuristic integrations.
- **Global item and namespace exclusions.** `absorbExclusionItems` defaults to
  `epicfight:skillbook`, and `absorbExclusionMods` defaults to `scriptor`. Unlike the heuristic-only
  blocklists, these settings prevent every adapter from absorbing a matching item.
- **Live policy reloads.** Loading or reloading the common config rebuilds immutable exclusion
  snapshots without requiring a game restart.
- **Policy maintenance helpers.** The policy can find and purge already-unlocked entries that now
  match a global item or namespace exclusion.
- **Expanded `/runictome debug identify`.** Diagnostics now report NBT keys, blocklist-tag state,
  the global policy decision and reason, the adapter that would claim the item, and the final
  absorb/no-absorb verdict.

### Fixed

- **Stateful third-party books now reopen correctly.** Absorption retains a one-item copy of the
  identifying stack, including its NBT, and generic/tagged/item-based adapters replay that exact
  stack on both logical sides. Previously only the registry id survived, so books whose identity or
  opening parameters lived in NBT appeared in the list but did nothing when clicked.
- **Legacy entries can be repaired by reacquiring the book.** If an older save contains a book key
  without its source stack, absorbing another copy backfills the missing stack and syncs it to the
  client. Existing key-only saves remain loadable.
- **Opening a stored book no longer mutates its saved data.** The use simulator works on a defensive
  one-item copy before adding its internal virtual marker, preserving the retained stack exactly as
  it was absorbed.
- **NBT-bearing functional items are no longer guessed to be documentation.** The keyword heuristic
  rejects stacks with meaningful NBT while allowing ordinary cosmetic/bookkeeping keys such as
  `Damage`, `RepairCost`, `HideFlags`, and `display`. Explicit and tagged guide integrations remain
  available for legitimate stateful books.

### Changed

- **Book capability data now includes an optional source stack.** New saves serialize one retained
  item per unlocked book; old key-only capability data remains valid and loads without migration.
- **Unlock synchronization now carries the retained stack.** Newly absorbed and repaired books are
  immediately usable on the client without requiring a relog or dimension change.
- **Stack-aware adapter hooks.** Generic, tagged, and item-based adapters replay retained stacks on
  both logical sides, while existing integrations remain source-compatible through default hooks.
- **Network protocol `3`.** Client and server must both use Runic Tome 0.5.1; mixed versions cannot
  connect.

### Config

- `absorbExclusionItems` — base item IDs that no adapter may absorb.
- `absorbExclusionMods` — namespaces whose items no adapter may absorb.
- `bookBlocklist` and `bookBlocklistMods` remain heuristic-only controls; use the new global
  exclusions when an item must remain physical regardless of integration priority.

### Upgrade notes

- Replace Runic Tome on both the client and server with 0.5.1.
- Books absorbed before 0.5.1 cannot have already-lost NBT reconstructed automatically. Acquire
  another copy of an affected book and allow the tome to absorb it once to repair the existing entry.
- Review newly generated `absorbExclusionItems` and `absorbExclusionMods` values when carrying
  forward an older common config.

## [0.4.0] — 2026-06-19

A targeted classification fix. The keyword catch-all no longer mistakes functional modded gear for
documentation, and packmakers gain explicit tag-based override channels in both directions.

### Fixed

- **Scriptor functional tomes were being absorbed and deleted.** The keyword catch-all
  (`HeuristicBookAdapter`) classified any item whose registry path contained a documentation keyword
  (`tome`, `book`, …) as a guide book, so Scriptor's functional `*tome*`/`*book*` items were stored as
  `BookKey`s and the absorption pipeline destroyed the physical item on pickup/craft/smelt/sweep. Fixed
  two ways: `scriptor` is now in the `bookBlocklistMods` default, and the heuristic gained a
  `looksFunctional` safety net that rejects items whose path contains `spell`, `scroll`, `caster`,
  `focus`, `rune`, or `wand`, or that are damageable or enchanted — before the keyword match runs.

### Added

- **Positive `#runictome:guide_books` tag.** A new `runictome:tagged` adapter (priority `50`, between
  concrete adapters and the keyword catch-all) absorbs any item in this tag, letting packmakers
  force-absorb a guide book the heuristic rejects (e.g. a legitimately-named "runic guide" suppressed by
  the `rune` functional signal).
- **`#runictome:absorb_blocklist` tag.** A hard opt-out checked by both the tagged and heuristic
  adapters; it overrides `#runictome:guide_books`, giving precedence **never-absorb > positive tag >
  heuristic**. Both tags ship empty and are append-friendly (`"replace": false`).
- **`/runictome debug identify`** — reports the held item's id, its `absorb_blocklist` membership, the
  first adapter that would match (and, for the heuristic, which keyword matched), and a final
  absorb/no-absorb verdict.
- **Verbose heuristic logging.** With `verboseLogging` enabled, the catch-all logs matched keywords and
  the reason it skipped an item (blocked namespace, item blocklist, `looksFunctional`, or
  `absorb_blocklist` tag).

### Config

- `bookBlocklistMods` default now includes `scriptor` alongside `irons_spellbooks`, `ars_nouveau`,
  and `rpglore`.

### Notes for packmakers

- The `bookBlocklistMods` namespace blocklist and the `#runictome:absorb_blocklist` tag gate **only**
  the keyword catch-all and the positive `guide_books` tag. Explicit/Patchouli/IMC/datapack-defined
  books from a blocked namespace are still absorbed, because those adapters run first.
- To re-include a guide the new `looksFunctional`/`rune` signals reject, add its item id to
  `#runictome:guide_books`. To force-exclude any item, add it to `#runictome:absorb_blocklist` (it wins
  over `guide_books`).

## [0.2.1] — 2026-06-18

A correctness release that stops the keyword catch-all from absorbing functional gear. The
catch-all matches any item whose registry path contains a documentation keyword, which wrongly
swallowed spellbooks (e.g. Apprentice's Codex's "Isekai Travel Guidebook", an Iron's Spells 'n
Spellbooks spellbook) and parallel collection containers (RPG Lore's "Lore Codex").

### Fixed

- **Spellbooks are never absorbed.** Iron's Spells 'n Spellbooks gear is now detected
  *structurally* — any item that extends `io.redspace.ironsspellbooks.item.SpellBook` or is a spell
  container (`ISpellContainer.isSpellContainer`) is excluded, covering spellbooks, scrolls, and
  spell-imbued curios. Because detection is by class/capability rather than namespace, this also
  covers **every Iron's Spellbooks addon** (Apprentice's Codex, GTBC's Spellbooks, Monsters &
  Spellbooks, …) automatically — no per-mod blocklist entry needed. Detection is reflective, so
  there is still no compile-time dependency on Iron's Spellbooks.
- **RPG Lore left to its own system.** `rpglore` is now blocklisted, so the Runic Tome no longer
  absorbs the Lore Codex (RPG Lore's own soul-bound collection container) or RPG Lore's lore books,
  avoiding two collection systems fighting over the same items.

### Added

- **Global "never absorb" exclusion layer.** `AdapterRegistry` now checks a set of exclusion
  predicates *before* any adapter runs, so an excluded item is never identified as a book regardless
  of which adapter (heuristic, Patchouli, config, IMC) would otherwise match it.
- **Tag-based blocklist.** A new `bookBlocklistTags` config (default `["runictome:absorb_blocklist"]`)
  excludes any item carrying a listed item tag. The shipped `#runictome:absorb_blocklist` tag is
  empty and append-friendly — modpacks, servers, and other mods can add functional items to it to
  exclude them without knowing exact item IDs or touching config.

### Config

- `bookBlocklistTags` (default `runictome:absorb_blocklist`) — item tags whose members are never
  absorbed by any adapter.
- `bookBlocklistMods` default now also includes `rpglore`.

## [0.2.0] — 2026-06-18

A reliability and feature release. The absorption pipeline no longer destroys items on a failed
unlock, corrupt data can't break capability loading, and the tome UI is now a searchable,
scrollable library with favorites.

### Added

- **Generic documentation-book catch-all.** A new `HeuristicBookAdapter` absorbs *any* item whose registry path matches a documentation keyword (`book`, `manual`, `guide`, `lexicon`, `tome`, `dictionary`, `codex`, `almanac`, `journal`, `encyclopedia`, `compendium`, `handbook`, `grimoire`, `primer`), so guide books from mods with no explicit support are absorbed automatically. Registered at lowest priority, so every explicit adapter still wins. Excludes block items (bookshelves), this mod's own items (the tome's path contains "tome"), and a configurable blocklist.
- **Minecraft Comes Alive integration.** When `mca` is loaded, its lore books (`mca:book_*`), Family Tree, and registry books are registered automatically by scanning the item registry.
- **Better Animals Plus integration.** `betteranimalsplus:animal_dictionary` is registered automatically when the mod is loaded.
- **Immersive Engineering & Modonomicon integrations.** `immersiveengineering:manual` is registered automatically when IE is loaded; Modonomicon book items are registered by scanning its namespace (like the MCA scan).
- **Datapack-defined books.** Packmakers can declare single-item books in `data/<namespace>/runictome/books/*.json` (`{ "item": "modid:item", "name": "Display Name" }`) — no code or config edits. Definitions are applied server-side on datapack load and synced to clients (`SyncBookDefsPacket`) so they display and open correctly on dedicated servers too.
- **Searchable, scrollable tome UI.** The library is now a scrolling list (`ObjectSelectionList`) with a search box, real item icons, and an unlocked-book count, replacing the paginated vanilla-book layout. **Favorites:** right-click an entry to pin it to the top; favorites are persisted per-player and synced. Left-click opens; a failed open now reports to the player instead of silently doing nothing.
- **Server-side re-open path.** Client→server `OpenBookPacket` plus `GuideSystemAdapter.openServer` let books that open their GUI from the server (e.g. Minecraft Comes Alive's `OpenGuiRequest`) re-open correctly from the tome. `UseSimulator` gained a server-side `simulateServerUse`. Client-driven books (Patchouli, Tinkers) are unaffected — Patchouli overrides `openServer` to a no-op.
- **Openable IMC books.** The `register_book` IMC message now accepts an `ImcBook(BookKey, ItemStack)` payload; the supplied item is used as the list icon and replayed via `UseSimulator` to actually open the book. A plain `BookKey` payload still works (message-only).
- **Explicit adapter precedence.** `GuideSystemAdapter.priority()` (default `100`, heuristic `0`) determines identification order, replacing the previous implicit dependence on registration order.
- **API version constant.** `RunicTomeAPI.API_VERSION` lets integrators detect compatibility.

### Changed

- **Non-destructive absorption.** Unlock now returns an `UnlockResult` (`ADDED` / `ALREADY_HAD` / `FAILED`); the source item is consumed only once the book is confirmed stored. If the capability is missing or an error occurs, the item is left in the world/inventory instead of being silently destroyed (pickup, craft, and sweep paths).
- **Corruption-tolerant data loading.** `BookKey.fromNbt` returns an `Optional` (via `ResourceLocation.tryParse` with blank-rejection) and `RunicTomeData` skips malformed or duplicate entries, so one bad entry can no longer abort capability/data loading.
- **`UseSimulator` failure feedback.** A foreign `use()` that throws now logs a stack trace and shows the player an "open failed" message instead of silently no-opping; the borrowed inventory slot is always restored.
- **Reduced network traffic.** A normal unlock sends only the incremental `UnlockBookPacket`; the full `SyncDataPacket` is reserved for login/respawn/dimension change.
- **Network protocol bumped to `2`** (new packets; clients and servers must both update).

### Config

- `absorbUnknownBooks` (default `true`) — toggle the keyword catch-all.
- `bookKeywords` — the keyword list matched against item registry paths.
- `bookBlocklist` (default: vanilla `book`/`writable_book`/`written_book`/`enchanted_book`/`knowledge_book`) — item IDs the catch-all never absorbs. Add any functional book-like item from your pack here (e.g. spell tomes) so it is not absorbed.
- `bookBlocklistMods` (default: `irons_spellbooks`, `ars_nouveau`) — mod IDs whose items the catch-all never absorbs, for mods whose "book"/"tome" items are functional gear. Documentation books these mods ship via Patchouli (e.g. Ars Nouveau's Worn Notebook) are still absorbed, since the Patchouli adapter runs before the catch-all.

## [0.1.3] — 2026-04-19

### Fixed

- **Unlock toasts showed the book ID instead of its name.** When a book was absorbed, the toast displayed the raw `ResourceLocation` (e.g. `patchouli:book_of_the_dead`) instead of the human-readable title. `ClientDataCache.acceptUnlock` now resolves the display name through `RunicTomeAPI.adapterFor(systemId).displayName(key)` — matching what the tome's library screen already did. Patchouli books render their real localized titles; item-based books render their item display component.

### Added

- **`ui.showUnlockToast`** (bool, default `true`) — mutes the unlock toast per client.
- **`performance.sweepIntervalTicks`** (int, default `20`, range `1..1200`) — how often the server sweeps player inventories for unabsorbed books. Set to `1` to restore the previous per-tick behaviour. Pickup/craft/smelt/container-close events remain handled separately and are unaffected.
- **`items.grantTomeOnFirstJoin`** (bool, default `true`) — disable the auto-grant for servers that distribute the tome via quest rewards or starting kits.
- **`com.otectus.runictome.api.NameFormat`** public helper — `titleCase(String)` extracted from `PatchouliGuideAdapter` so third-party adapters can reuse the same fallback formatter.
- **Nicer default `GuideSystemAdapter.displayName`.** The interface default now returns `titleCase(key.bookId().getPath())` instead of the raw `ResourceLocation` string. Adapters that don't override `displayName` get readable fallbacks for free.

### Changed

- **Per-tick inventory sweep is now throttled to once per second** by default (configurable via `sweepIntervalTicks`). Pickup, craft, smelt, and container-close are still event-driven and absorb books immediately — the timer sweep exists only as a safety net for `/give`, hopper inserts, and other paths that bypass Forge's pickup events. This partially reverts the v0.1.0 change that dropped the interval knob; the trade-off of up-to-one-second latency on the fallback path is worth the ~95% reduction in per-player per-tick work on large servers.

### Removed

- **`CapabilityEvents.onJoinLevel` handler.** It fired a full-NBT sync on every dimension transition alongside the dedicated `onPlayerChangedDimension` / `onPlayerRespawn` / `onPlayerLoggedIn` handlers — a duplicate sync with no behavioural benefit.

## [0.1.2] — 2026-04-15

### Added

- **Custom item texture.** The Runic Tome now uses a dedicated sprite (`assets/runictome/textures/item/runic_tome.png`) instead of the vanilla `written_book` texture. Model `runictome:item/runic_tome` rewired accordingly.

### Changed

- **Soulbound on death.** The Runic Tome is no longer dropped on death. At `LivingDropsEvent` the tome is intercepted, its count stashed on the player's capability, and restored on `PlayerEvent.Clone`. Persists across server crashes between death and respawn. `keepInventory=true` is honored (no double-give). Non-death clones (dimension change, End return) are a no-op.

## [0.1.0] — 2026-04-13

First public release.

### Added

- **Runic Tome item.** Epic rarity, stacks to 1, fire-resistant. Granted automatically the first time a player joins a world (`FirstJoinHandler`).
- **Soulbound behavior.** The Runic Tome is never dropped on death — any tome in the player's inventory at death is intercepted in `LivingDropsEvent`, its count is recorded in a persisted `stashedTomes` field on the player's capability, and the tome is restored to the new player's inventory during `PlayerEvent.Clone`. The stash survives server crashes between death and respawn because it's persisted to disk alongside the rest of `RunicTomeData`. Non-death clones (dimension change, End return) are a no-op. `keepInventory=true` is honored — `LivingDropsEvent` isn't fired and the vanilla path keeps the tome intact with no double-give.
- **Per-player virtual book capability.** Unlocked books are stored in a persistent `IRunicTomeData` capability attached to each player; persisted across death, dimension change, and item loss.
- **Server → client sync.** Custom packet layer (`RunicTomeNetwork`, `UnlockBookPacket`) pushes unlock events to the client-side `ClientDataCache`; full state syncs on login via `CapabilityEvents.syncTo`.
- **Vanilla-book-styled GUI.** `RunicTomeScreen` renders the vanilla `textures/gui/book.png` at 192×192 with paginated clickable entries. Page forward/back via vanilla `PageButton` widgets or ←/→ keys. Entries land directly on page 0; clicking one closes the tome and delegates to the adapter's `open()` method.
- **Automatic absorption pipeline.** Multiple redundant paths ensure books are absorbed the instant they enter an inventory:
  - `EntityItemPickupEvent` — ground pickup (priority `HIGH`, cancels the pickup and discards the ItemEntity).
  - `PlayerEvent.ItemCraftedEvent` / `ItemSmeltedEvent` — crafting and smelting output.
  - `TickEvent.ServerTickEvent` — per-tick inventory sweep on every online player (no interval gating).
  - `PlayerContainerEvent.Close` — sweep on container close, catching quest-reward GUIs and FTB-style popups.
  - Immediate sweep on `PlayerLoggedInEvent` after the tome is granted.
- **Patchouli integration (reflective, zero compile-time dependency).** `PatchouliGuideAdapter` recognises both flavors of Patchouli books:
  - **NBT fast-path** for generic `patchouli:guide_book` stacks tagged with `{patchouli:book: "modid:book_id"}`.
  - **Custom-item path** for books declared with `dont_generate_book: true` (e.g. Ars Nouveau's Worn Notebook, Botania's Lexica Botania). Builds an `Item → BookKey` map by reflectively walking `vazkii.patchouli.common.book.BookRegistry.INSTANCE.books` and calling each Book's `getBookItem()` method.
  - Map is pre-warmed on `OnDatapackSyncEvent` (via `PatchouliReloadHandler`) so the first absorption doesn't pay the build cost inline.
  - Client-side book opening via `IPatchouliAPI.openBookGUI(ResourceLocation)`.
  - Book display names read from `Book.name` (supports both translation keys and literal strings); falls back to a title-cased book ID.
- **Tinkers' Construct integration.** All six standard books registered automatically when `tconstruct` is loaded: Materials and You, Puny Smelting, Mighty Smelting, Fantastic Foundry, Encyclopedia of Tinkering, Tinkers' Gadgetry. Uses the generic `ItemBasedAdapter`.
- **Config-driven book allowlist.** `extraBookItemIds` in `runictome-common.toml` lets modpack authors register any standalone item as a guide book without code changes. Invalid/missing entries are logged and skipped.
- **Public integration API.** `RunicTomeAPI.registerAdapter(GuideSystemAdapter)` and `GuideSystemAdapter` interface allow third-party mods to register their own guide systems.
- **IMC integration.** `InterModComms.sendTo("runictome", "register_adapter", ...)` supports mods that don't want a compile-time dependency on Runic Tome. Handled by `ImcHandler`.
- **Diagnostic logging.** On startup, logs `"Runic Tome: registered N guide-book adapter(s): [...]"` at INFO. On first absorption of an unrecognised book-like item (name contains "book"/"manual"/"guide"/"lexicon"/"tome"), logs a once-per-item hint at INFO telling the user to add it to `extraBookItemIds`.

### Known good with

- **Patchouli** 1.20.1-85-FORGE and later.
- **Tinkers' Construct** 1.20.1.
- **Ars Nouveau** (via Patchouli custom-item path — Worn Notebook).
- **Ice & Fire Delight** (via Patchouli NBT path — Cookbook).
- Tested in the Runecraft modpack environment against a large (100+ mod) instance.

### Fixed during development

The following issues were caught and corrected before release. Captured here for posterity.

- **`IPatchouliAPI` ClassNotFoundException.** The interface is declared as a **nested** interface inside `vazkii.patchouli.api.PatchouliAPI`, not as a top-level class. Early iterations of `PatchouliGuideAdapter.tryInit()` used the top-level name and threw `ClassNotFoundException`, which the outer catch swallowed as "Patchouli not present" — leading to zero adapters being registered even when Patchouli was loaded. Fixed by trying the binary nested-class name (`vazkii.patchouli.api.PatchouliAPI$IPatchouliAPI`) first and falling back to the top-level name for forward compatibility.
- **Custom-item Patchouli books silently unrecognised.** The original `identify()` method only matched items whose registry ID was literally `patchouli:guide_book`, rejecting any book declared with `dont_generate_book: true`. Fixed by adding the reflective `BookRegistry` walk and `Item → BookKey` map.
- **`getBookStack()` returning empty.** An early version of `buildCustomItemMap()` went through `IPatchouliAPI.getBookStack(id)` via a cached `API_INSTANCE`. Under certain setup orderings `PatchouliAPI.get()` returns a stub that returns empty stacks, so the map built with 0 entries. Fixed by switching to `Book.getBookItem()` called directly on each Book object, bypassing the API layer entirely.
- **Per-book errors silently swallowed.** `buildCustomItemMap()` originally logged per-book failures at DEBUG, so a 0-entry map looked like "everything worked, zero custom books exist." Upgraded to WARN with a counts summary `(mapped, total, generic, errors)`.
- **"Open" button did nothing.** Early `PatchouliGuideAdapter.open()` cached only the server-side 2-arg overload (`openBookGUI(ServerPlayer, ResourceLocation)`) and invoked it from the client with a `LocalPlayer`, throwing `IllegalArgumentException` inside a try/catch. Fixed by caching both client and server overloads and preferring the client 1-arg variant when called from the screen.
- **Lowercase book titles.** `displayName()` returned the raw `bookId.getPath()`. Fixed by reflectively reading `Book.name` and wrapping in `Component.translatable(...)`; falls back to a title-cased ID.
- **Slow startup absorption.** Books granted to a player after login (e.g. via KubeJS, FTB Quests, or any path that calls `Inventory.add()` directly) bypass every Forge pickup event and were only caught by the periodic sweep, which ran every 20 ticks (up to 1 second of latency). Removed the `inventorySweepIntervalTicks` config knob entirely and switched to per-tick sweeping plus `PlayerContainerEvent.Close` handling.
- **Inventory sweep sync gap.** `ServerTickHandler.scanContainer` mutated inventory slots directly via `slots.set(i, ItemStack.EMPTY)` without notifying the container menu. Added explicit `inv.setChanged()` and `sp.inventoryMenu.broadcastChanges()` calls after any removal.
- **GUI title-page detour.** The vanilla-book GUI originally opened on a title/count page and required a forward-arrow click to see entries. Dropped the title page; entries render from page 0.
