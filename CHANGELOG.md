# Changelog

All notable changes to Runic Tome are documented here. Format based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/); this project uses [Semantic Versioning](https://semver.org/).

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
