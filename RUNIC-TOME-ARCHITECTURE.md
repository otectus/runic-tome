# Designing the Runic Tome: A Forge 1.20.1 Architecture for Centralized Guide Books

## Executive Summary

This report proposes a concrete Forge 1.20.1 architecture for a mod called **Runic Tome**, whose goal is to act as a virtual, per‑player, centralized library for guide books and manuals from other mods, while preventing physical duplicates from cluttering inventories.

The recommended design is to use a **player‑attached persistent capability** as the single source of truth for unlocked books, a **server‑authoritative filtering layer** that intercepts guide‑book creation and pickup events, and a **client GUI** that opens other mods’ book UIs by constructing appropriate `ItemStack`s or using mod APIs (notably Patchouli’s) on demand. Compatibility is achieved via an **adapter/registration system** with both auto‑discovery heuristics and explicit integration hooks.[^1][^2]

The architecture is intentionally Forge‑native and avoids mixins/coremods except for optional, best‑effort integrations with mods that provide no clean extension points.

***

## 1. Feasibility and High‑Level Architecture

### 1.1 Overall feasibility

Forge 1.20.1 continues to support classic systems such as **capabilities**, **events**, and **networking**, which are sufficient to implement a per‑player virtual library plus item‑pickup filtering without patching vanilla. Mods such as **Akashic Tome** already demonstrate that aggregating other mods’ documentation books into a single item is practical, though they store real book `ItemStack`s rather than a purely virtual registry.[^3][^4][^5][^1]

Runic Tome’s stricter goals (no physical duplicate books; purely virtual storage; cross‑mod GUI launching) are feasible, but **fully automatic discovery of all guide books is not realistic**. Some mods expose books via Patchouli or well‑known items, but others use custom GUIs or soft‑coded triggers. A hybrid approach combining heuristics and explicit integrations is required.

### 1.2 Recommended architectural pillars

Recommended core components in Forge 1.20.1:

- **Player capability for book state**
  - Persistent, attached via `AttachCapabilitiesEvent<Entity>` for players.[^5][^1]
  - Stores list of unlocked guide "entries" keyed by a stable identifier (e.g. `ResourceLocation` or `(modid,itemId,nbtKey)` mapping).
  - Synced to client via custom packets when necessary.

- **Runic Tome item and GUI**
  - A single physical `Item` (the Runic Tome) representing the entry point to the library.
  - On use, opens a custom screen reading from the capability’s unlocked book list.

- **Server‑side acquisition filter**
  - Listens to Forge events (pickup, login, dimension change, loot, etc.) to detect when a guide book is being granted.
  - If the book type is known and the player already has it in the capability, the item is intercepted and either deleted or converted into a tome unlock without entering inventory.

- **Compatibility adapter registry**
  - Central registry where integrations for specific guide systems (Patchouli, Lexica Botania, Immersive Engineering manual, etc.) register book descriptors and open‑book handlers.
  - Exposed as a small public API so other mods can proactively register their books.

- **Optional mixin layer (non‑core)**
  - Used only when a popular guide system does not fire events or provide usable APIs for book opening or granting.
  - Needs careful scoping to avoid conflicts and fragility.

This design keeps Forge’s recommended practice of using **capabilities** for per‑player persistent state, instead of abusing `SynchedEntityData` or mixin‑injected fields, which can cause desync and crashes.[^6][^7][^1]

***

## 2. Core Data Model and Player Storage

### 2.1 Capability vs. alternative approaches

Forge documentation and community guidance for 1.19+ and 1.20.x recommend **capabilities** for attaching mod data to entities, including players, with optional persistence and syncing.[^1][^5]

Alternative options and trade‑offs:

- **Capabilities (recommended)**
  - Built‑in concept, intended for additional data on entities, blocks, and item stacks.[^5][^1]
  - Provide clear serialization via `INBTSerializable` or `ICapabilitySerializable` and integrate with Forge’s lifecycle (clone on death, invalidate, etc.).
  - Avoid the ordering and desync issues that arise when other mods try to add `SynchedEntityData` fields to entities they do not own.[^7][^6]

- **Custom player `SavedData` keyed by UUID**
  - Similar to Fabric `PersistentState`, but in Forge this is primarily intended for world‑level data.[^8][^5]
  - Works, but requires manual save/load and mapping from player UUID to book state, and manual sync to client.

- **NBT on player persistent data (`Player#getPersistentData`)**
  - Vanilla NBT bag associated with the player entity.
  - Simpler but more ad‑hoc; lacks type safety and extension points other mods can hook into; still requires manual sync.

Given the complexity and need for cross‑mod compatibility, **a dedicated player capability is the best choice**, with a simple interface like:

```java
public interface IRunicTomeData extends INBTSerializable<CompoundTag> {
    boolean hasBook(BookKey key);
    void unlockBook(BookKey key);
    Collection<BookKey> getBooks();
}
```

where `BookKey` is a small immutable descriptor (see below).

### 2.2 Book identity model

For duplicate detection and opening GUIs, Runic Tome needs stable identifiers that are not tied to a specific `ItemStack` instance.

Recommended structure:

```java
public record BookKey(ResourceLocation systemId, ResourceLocation bookId) {
    // systemId: e.g. "patchouli", "botania", "modid:custom_system"
    // bookId:   system-specific identifier (e.g. Patchouli book ID or item ID)
}
```

Rationale:

- **System‑scoped IDs** allow different backend systems (Patchouli vs. custom GUIs) to co‑exist without accidental collisions.
- For Patchouli, `systemId = new ResourceLocation("patchouli", "book")`, `bookId = new ResourceLocation(modid, bookName)` leveraging Patchouli’s book IDs like `botania:lexicon`.[^2]
- For custom item based books (e.g. Botania Lexica, Tinkers’ Materials and You, Immersive Engineering manual when not using Patchouli) the `bookId` can be the item’s registry name plus optional NBT key indicating subtype.[^9][^10]

### 2.3 Persistence and cloning

Per Forge capability docs, player capabilities do **not** automatically persist across death; data must be copied during `PlayerEvent.Clone`.[^1][^5]

Implementation pattern:

- Attach capability in `AttachCapabilitiesEvent<Entity>` when `entity instanceof ServerPlayer`.
- Implement `INBTSerializable<CompoundTag>` on the capability impl and rely on Forge’s automatic serialization for world saves.
- In `PlayerEvent.Clone`, if `event.isWasDeath()` then copy the old capability’s book set into the new player entity so book unlocks survive death.
- Sync to client:
  - Either send the entire state on login/dimension change.
  - Or send incremental unlock packets as books are absorbed.

This avoids misuse of `SynchedEntityData`, which Forge warns against for foreign entities; data parameters should only be defined on the entity’s own class.[^6][^7]

***

## 3. Giving the Runic Tome on First Join Only

### 3.1 Detecting first join

Forge exposes several player lifecycle events (e.g. `PlayerEvent.PlayerLoggedInEvent`, `EntityJoinLevelEvent`), commonly used to grant starter items. The key challenge is distinguishing **first join on this world** from subsequent logins.[^11][^12]

Recommended strategy:

- Add a boolean flag to the player capability, e.g. `boolean hasReceivedTome()`.
- On `PlayerLoggedInEvent` (Forge event, server side), check this flag:
  - If `false`, give the Runic Tome item and set `hasReceivedTome = true` in the capability.
  - Save and sync capability.

Alternative approaches sometimes store per‑world flags in `SavedData` or hacky markers such as placing a hidden block at a fixed Y to detect first run, but these are discouraged, as seen in forum discussions around world‑init and first‑time effects.[^13]

### 3.2 Granting the item

Implementation details:

- In `PlayerLoggedInEvent` handler on the logical server, create an `ItemStack` of the Runic Tome.
- Insert into player inventory or, if inventory full, drop at their feet.
- Client side receives the synced capability and can show a toast or hint explaining the tome.

If the player somehow loses the tome, you may want a **configurable recovery** mechanism (e.g. a `/rune tome` command or crafting recipe) rather than re‑granting based on the first‑join flag.

***

## 4. Detecting and Registering Guide Books from Other Mods

### 4.1 What constitutes a "guide book"

Guide books generally fall into these categories:

- **Patchouli books**: generic JSON‑driven books loaded from `/data/_modid_/patchouli_books` and represented by `patchouli:guide_book` items with `"patchouli:book"` NBT specifying the book ID.[^2]
- **Classic custom items**:
  - **Lexica Botania**: Botania’s in‑game manual, historically backed by its own system but nowadays often mirrored by or migratable to Patchouli for some packs.[^10][^2]
  - **Tinkers’ Construct "Materials and You"**: automatically given book describing tools and materials.[^9]
  - **Immersive Engineering manual**: often with a custom in‑mod manual UI; some community ports re‑implement it as Patchouli.[^10]
- **Mod‑specific manuals / GUIs**: Items which on right‑click open custom screens without using Patchouli.
- **Written books or book‑like items**: `minecraft:writable_book`, `minecraft:written_book` or modded subclasses used as guides.

Because there is no standardized Forge registry for "guide books", **automatic detection can only be heuristic** and partial.

### 4.2 Heuristic detection strategies

Recommended heuristics for automatic discovery:

- **Patchouli**: inspect item stacks of item `patchouli:guide_book` with NBT key `"patchouli:book"` and treat the value as the book ID.[^2]
- **Tags**: if Runic Tome defines and documents a convention such as an item tag `runictome:guide_books`, mod authors or datapack makers can tag their guide items. Runic Tome can scan the item registry for items in this tag and register them automatically.
- **Class or interface patterns**: certain guide systems may implement public marker interfaces (e.g., a hypothetical `IGuideBookItem`). When available, Runic Tome can detect via `instanceof`.
- **Item registry names**: heuristics like `modid:guide_book`, `modid:manual`, `modid:lexicon`, `modid:book` are risky but could be behind a config toggle.

However, many mainstream guide mods already use **Patchouli** or can be configured to do so. Focusing on Patchouli and a small set of hardcoded legacy special cases yields most value.[^14][^15][^10][^2]

### 4.3 Explicit compatibility and adapter registration

For robust behavior, Runic Tome should expose a small integration API:

```java
public interface GuideSystemAdapter {
    ResourceLocation systemId();
    Optional<BookKey> identify(ItemStack stack);
    void open(BookKey key, LocalPlayer clientPlayer);
}

public final class RunicTomeAPI {
    public static void registerAdapter(GuideSystemAdapter adapter) { ... }
    public static void registerBook(BookKey key, ItemStack representativeStack); // optional convenience
}
```

Third‑party mods can then register:

- For Patchouli: an adapter that recognizes `patchouli:guide_book` and uses `PatchouliAPI` to open the book.[^16][^2]
- For custom manuals: an adapter that recognizes their guide item and calls their internal API or opens a custom screen.

### 4.4 Concrete examples of book‑providing mods

- **Patchouli** itself: generic guide system with book IDs; provides API calls to open books (e.g. `PatchouliAPI.instance.openBookGUI(...)`) in recent versions.[^16][^2]
- **Akashic Tome**: stores documentation books by actually transforming into them, using NBT tags for bookkeeping.[^4][^17][^3]
- **Tinkers’ Construct**: "Materials and You" book is automatically granted on first world load and used to explain tools.[^9]
- **Immersive Engineering**: Engineer’s Manual, sometimes ported to Patchouli via addons.[^10]
- Many smaller mods ship Patchouli books with IDs as described in Patchouli’s docs.[^15][^2]

Runic Tome’s initial target set should prioritize Patchouli books and high‑visibility manuals like Lexica Botania, Tinkers’ Materials and You, and the Immersive Engineering manual.

***

## 5. Opening Other Mods’ Books from a Custom GUI

### 5.1 General strategies

There are three primary ways to open other mods’ book UIs from Runic Tome’s GUI:

1. **Construct and "use" an `ItemStack`**
   - Create an `ItemStack` of the original guide item with appropriate NBT, then:
     - On client side, invoke the same code path that would run when the player right‑clicks the item (e.g. via calling the item’s `use` method with a fake context or faking a use packet).
   - Pros: close to how the original mod expects to be called, high compatibility for items that just open a screen.
   - Cons: some mods check for the item being in inventory or in hand; this might require temporarily inserting the stack into the player’s hand for the duration of the call.

2. **Call dedicated APIs**
   - For Patchouli, there is an API method `PatchouliAPI.instance.openBookGUI(...)` on both server and client sides; Patchouli issue discussion mentions this existed as of 1.15 and later.[^16]
   - Pros: clean; does not require the player to own the item; easier to call from a central GUI.
   - Cons: only available for mods that expose such APIs.

3. **Open screens directly**
   - Some mods expose public screen classes that can be instantiated with just a `Book` or `ItemStack` object, making it possible to use `Minecraft.getInstance().setScreen(new ModBookScreen(...))` on the client.
   - Pros: works even if item logic is entangled, as long as screens are public and stable.
   - Cons: fragile to internal changes; not future‑proof.

The **recommended approach** is per‑system via the adapter pattern: Patchouli adapter uses `PatchouliAPI`, legacy item‑based manuals can fall back to temporary stack in hand plus calling `use`.

### 5.2 Client–server concerns

Key points:

- **Authority**: book unlocking and duplicate suppression are server‑authoritative; only the server may modify capabilities or destroy items.
- **Screen opening**:
  - For Patchouli, `openBookGUI` has both server and client variants; server variant can send packets to client to open the book.[^16]
  - For item‑use simulation, Runic Tome’s GUI click handler should send a custom packet to the server; the server constructs the appropriate `ItemStack` and triggers the original mod’s server‑side code (if any), then either instructs the client to open the corresponding screen or relies on the item’s logic to sync.
- **Ownership assumptions**: some mods assume the book item is actually in the player’s inventory or main hand.
  - To mitigate, the adapter can temporarily swap the player’s held item to the book stack, call the item’s `use`/`useOn`, then restore the original item.
  - Care must be taken not to allow the fake stack to be dropped, consumed permanently, or used for crafting.

### 5.3 Risks with hardcoded assumptions

Risks when interacting with foreign book systems include:

- **Inventory checks**: code may check `player.getMainHandItem()` or search the inventory for the book stack, failing if it is absent.
- **Persistent usage side effects**: some books may mark internal progress, start quests, or grant advancements based on item NBT.
- **Security checks**: mods might gate book opening behind advancements, dimension, or game stage checks.

Runic Tome should treat opening as a **"bring your own permissions"** operation: it only triggers the same logic the original item/API would, letting those mods enforce their own gating.

***

## 6. Preventing Guide Books from Entering Inventory

### 6.1 Forge events for interception

In Forge, item acquisition channels can be intercepted primarily via:

- **Pickup events**
  - `EntityItemPickupEvent` (or its 1.20.1 equivalent) fires when a player collides with an `ItemEntity`; it is cancellable and can prevent pickup entirely.[^18]
- **Crafting and smelting events**
  - `PlayerEvent.ItemCraftedEvent` and `PlayerEvent.ItemSmeltedEvent` capture items produced via crafting and furnaces.[^11]
- **Loot and chest generation**
  - Vanilla loot tables do not directly emit Forge events per‑item, but mods can hook `LootTableLoadEvent` or patch tables; Runic Tome instead can intercept at pickup time, since chest contents are taken as items.
- **Login / starter items**
  - Mods that grant starter items typically do so on the server side in response to player login or first join; Runic Tome cannot intercept inside their code but can clean up immediately afterward.

Approach per channel:

- **Pickup from ground**: handle `EntityItemPickupEvent` (or 1.20 equivalent) on server:
  - If the `ItemStack` is recognized as a guide book, call the adapter’s `identify` and decide whether to unlock or ignore.
  - If the player already has the book, cancel event and kill the `ItemEntity`.
  - If not unlocked, unlock in capability and kill the entity without inserting into inventory.
- **Crafted guide books**: in `ItemCraftedEvent`, if result is a guide book and player already has it, cancel the event or replace result with air.
- **Quest or reward system grants**: many quest mods give rewards directly into inventory; these may not be interceptable via Forge events if done via direct API calls. A fallback is to run **inventory sanitization** on a short timer or on each server tick for players, scanning for guide books and absorbing them into Runic Tome immediately.

### 6.2 Is Forge alone sufficient?

For most acquisition paths (pickup, crafting, smelting, many modded events), Forge’s standard event system is sufficient: `EntityItemPickupEvent`, crafting events, and login events.[^19][^18][^11]

However, some mods may:

- Grant books directly via internal logic without firing Forge events.
- Store book content in custom containers instead of the player inventory.

For these, Forge events alone cannot guarantee interception. Optional **mixin‑based patches** can be considered for high‑value integrations, but Runic Tome should remain functional and safe even without them, treating such cases as "best effort".

### 6.3 Absorbing books cleanly

When a guide book is detected:

1. Server checks adapter’s `identify` to obtain `BookKey`.
2. If the player’s capability does **not** contain the key:
   - Add to capability.
   - Optionally send a `BOOK_UNLOCKED` packet to client for a toast.
   - Prevent the corresponding `ItemStack` from entering inventory:
     - For pickup: cancel event and remove `ItemEntity`.
     - For crafting: set crafted stack to something else (e.g. Runic Tome, or nothing) and unlock.
3. If the book is already unlocked:
   - Cancel pickup/crafting and discard the stack.

This ensures a strictly virtual storage model while keeping progression semantics intact, since unlocking still requires obtaining the book once.

***

## 7. Duplicate Prevention and Edge Cases

### 7.1 Duplicate identity strategy

Duplicate blocking should be based on **logical book identity**, not raw `ItemStack` equality. The recommended key is `BookKey(systemId, bookId)`:

- **Patchouli**: duplicates are any stacks with the same `"patchouli:book"` ID regardless of item NBT such as read state.[^2]
- **Item‑based manuals**: duplicates are defined by `(modid,itemId,optionalSubtype)`; if some mods provide multiple distinct manuals, they can register separate `BookKey`s through the adapter API.

NBT equality should only be used to distinguish genuinely different books (e.g. a spell grimoire with different contents), not for generic guides.

### 7.2 Edge cases

Edge cases and handling recommendations:

- **Starter items on login**
  - A mod may grant a book on first login via its own event handler. Runic Tome cannot prevent this ahead of time but can run a post‑login inventory sweep:
    - After a short delay (e.g., one tick after `PlayerLoggedInEvent`), scan inventory for known guide books, absorb them, and delete the items.

- **Books given by commands**
  - Operator commands like `/give` are processed server‑side and result in stacks in inventory.
  - Runic Tome’s periodic inventory sweep can treat these the same as any other: absorb and remove.
  - Configuration should allow operators to disable this behavior for debugging or map making.

- **Books dropped on death**
  - When a player dies, their inventory items become drops; these are intercepted at pickup via `EntityItemPickupEvent` and absorbed as usual.[^18]
  - Capability state is cloned via `PlayerEvent.Clone`, so unlocked status persists across death.[^1]

- **Books from loot tables and containers**
  - Loot in chests or rewards from quests become items during pickup or transfer into inventory, where events or the sweep can handle them.

- **Books inserted into containers**
  - If a guide book is in a chest and the player shift‑clicks it into inventory, Forge events for inventory transfer may or may not fire reliably depending on container implementation.
  - The inventory sweep is a safety net: every N ticks, inspect inventory; for any stack recognized as a guide book, absorb and remove.

- **Multiplayer synchronization**
  - Capability state is stored server‑side and synced to each client when they join or when unlock state changes.
  - The client GUI uses its local copy for display; server remains the source of truth.

***

## 8. GUI and UX Design

### 8.1 GUI framework in Forge 1.20.1

Forge 1.20.1 mod GUIs follow the standard Mojang screen system (`Screen`, `AbstractContainerScreen`) with Forge hooks when needed. For Runic Tome, a simple client‑side `Screen` bound to no logical container is sufficient.[^7][^1]

Features:

- Uses the synced capability data to render available books.
- Supports mouse input, scroll, keyboard shortcuts (search).
- Optionally uses texture‑based theming (e.g. runes, tabs) for visual polish.

### 8.2 Layout ideas

Recommended layout concepts:

- **Left sidebar**
  - Mod group filtering: list of mod IDs or system types (Patchouli, Botania, etc.).
  - Category tree if the adapter provides metadata (e.g., Patchouli categories).

- **Main panel**
  - Grid or list of book cards, each card showing:
    - Icon (usually the original item’s icon).
    - Book name, mod name.
    - Status indicators: unlocked, locked, unread.

- **Search bar**
  - Free text search across book titles, mod names, and tags.

- **Favorites/pins**
  - Star or pin icon per book; pinned books appear in a "Favorites" section at the top.

- **Progress indicators**
  - If the underlying guide system exposes progress (e.g., Patchouli advancements for locked entries), show a rough progress bar per book.

### 8.3 Unlocked vs. all compatible books

Trade‑offs:

- **Only unlocked books**
  - Cleaner; avoids showing content the player has never had access to.
  - Less discoverability; players may not realize a mod even has a guide book.

- **All compatible books**
  - Shows the full potential library; some entries may be "locked" or greyed out.
  - Requires a source of the full set of compatible books; for Patchouli, this is the list of all registered books from `BookRegistry` or via Pack‑time scanning.[^20][^2]

Recommended UX:

- Default view shows **unlocked books**, with an optional toggle or separate tab for **"All known books"** discovered via adapters and tags.
- Locked books show a padlock icon and tooltip like "Obtain the [mod] guide book to unlock in the Runic Tome".

### 8.4 Master guide compendium UX patterns

To avoid overwhelming players:

- Provide **mod grouping**: cluster books by mod or category.
- Keep first‑time experience simple: if the player only has one unlocked book, open that book directly instead of showing the full library.
- Add **tooltips** linking to the underlying systems (e.g., mention "Patchouli guide" or "Lexica Botania") so players can connect the Tome with known UIs.

***

## 9. Compatibility and Integration Strategy

### 9.1 Easiest systems to support

- **Patchouli**
  - Books are well‑structured and identified by IDs.[^2]
  - Items use a common item (`patchouli:guide_book`) with consistent NBT.[^2]
  - There is an API to open books and even specific entries programmatically (`PatchouliAPI.instance.openBookGUI(...)`).[^16]

- **Mods that already use Patchouli**
  - Many mods (e.g., some Immersive Engineering ports) ship docs as Patchouli books.[^10]
  - Supporting them is nearly free once Patchouli is supported.

- **Mods willing to integrate**
  - Adding a dependency or optional dependency on Runic Tome and registering adapters is trivial.

### 9.2 Harder systems and why

- **Custom manual systems with no public API**
  - If a mod’s book item opens a deeply custom GUI and uses internal state, then Runic Tome must either:
    - Simulate `ItemStack` use (with the item present), or
    - Reflectively call screen classes.
  - Both approaches are fragile.

- **Systems that tie manuals tightly to player inventory**
  - If the mod checks for the presence of the book item in inventory or uses item NBT as progression storage, virtualizing the book might break expectations.

- **Mods that give starter books via non‑event mechanisms**
  - Without Forge events, only periodic inventory sweeps or mixins can intercept.

### 9.3 Adapter‑based design as default

An adapter system provides:

- **Isolation**: each guide system’s quirks are encapsulated.
- **Extensibility**: new mods can be supported without touching core code; just add a new adapter.
- **Configurability**: problematic adapters can be disabled via config without disabling the whole mod.

Adapters can optionally be loaded via **IMC (InterModComms)** calls during mod init: other mods send messages to Runic Tome with their book metadata and adapter registration, allowing lightweight integration without hard dependencies.

### 9.4 Public API for third‑party support

Runic Tome should provide a small, documented API for modders:

- `RunicTomeAPI.registerAdapter(GuideSystemAdapter adapter)`.
- `RunicTomeAPI.registerBook(BookKey key, ItemStack representativeStack)`.
- `RunicTomeAPI.isBookUnlocked(Player player, BookKey key)` to allow mods to query access.

API should be stable and versioned to avoid breaking integrations with updates.

***

## 10. Technical Risks and Limitations

### 10.1 What cannot be done cleanly without patching

- **Intercepting all possible item grant paths**
  - Some mods may grant books entirely within their own logic without exposing events or using standard Forge hooks.
  - Without mixins, Runic Tome’s interception is limited to what Forge exposes (pickup, crafting, login, etc.).[^19][^18]

- **Forcing all book UIs to open without ownership**
  - If a mod demands that a specific item be present in the player’s hand or inventory for its GUI to function, run‑time simulation might still fail for edge cases.

- **Mod‑specific progression tied directly to item NBT**
  - If a mod stores progression in the book item itself, virtualizing the book into Runic Tome means the original item may never exist, preventing progression.

In such cases, Runic Tome may need config options to **opt out** of virtualizing specific books, instead letting them behave normally.

### 10.2 Fragility points

- **Reliance on other mods’ internal classes**: directly constructing or referencing foreign screen classes can break with updates.
- **Mixin interactions**: if multiple mods patch the same methods for manual behavior, conflicts may arise.
- **Capability misuse**: incorrectly implemented capabilities (e.g., not cloning on death) can lead to lost unlocks.[^5][^1]

### 10.3 Licensing and redistribution concerns

- Patchouli and mods like Akashic Tome, Botania, and others have their own licenses; referencing their APIs or depending on them at runtime is generally permitted, but **copying code or assets** is not.[^17][^3][^4][^2]
- Runic Tome should:
  - Depend on other mods only as runtime/optional dependencies, not bundle their JARs.
  - Use only their documented APIs and not copy their internal implementation.

***

## 11. Existing Precedents and Lessons

### 11.1 Akashic Tome

Akashic Tome aggregates documentation books by **storing the actual `ItemStack`s** inside the tome’s NBT and transforming into them.[^3][^4][^17]

Key features:

- The tome can be combined with documentation books in a crafting grid; each added book becomes an attached stack.[^4][^17][^3]
- Right‑clicking shows a menu of attached books; selecting one transforms the tome into that book.
- The design deliberately uses the real items, not emulations, to ensure 100% compatibility with the books’ behavior.[^3][^4]

Lessons for Runic Tome:

- Using real `ItemStack`s simplifies compatibility but conflicts with the requirement to avoid physical duplication and keep a purely virtual library.
- The idea of a **menu listing multiple books** is solid and can be re‑used, but Runic Tome’s storage should be abstracted as keys rather than raw stacks.

### 11.2 Patchouli

Patchouli is widely used as a data‑driven guide system; it defines book IDs and provides JSON formats for books and entries.[^2]

Notable aspects:

- Books are identified by `ResourceLocation` (e.g., `botania:lexicon`).[^2]
- The core book item is `patchouli:guide_book` with NBT key `"patchouli:book"` referencing the book ID.[^2]
- API requests and issues reference methods for programmatically opening books, such as `PatchouliAPI.instance.openBookGUI(player, bookId, entryId)`.[^16]

Lessons:

- Patchouli provides a **clean integration surface**; Runic Tome should lean heavily on it for automatic discovery and opening.
- Virtualization is easy: players need not own the physical `patchouli:guide_book` item if the API can open books directly.

### 11.3 Starter item and join‑event mods

Various mods and resources show patterns for granting items on first join or world start.[^21][^22]

Lessons:

- Player login events (`PlayerLoggedInEvent`) are the standard place to hook starter logic.[^22]
- Custom mods use similar patterns to manage first‑join state, sometimes with world flags; but a player‑attached capability is more robust.

***

## 12. Recommended Architecture Decision

Summarizing the above analysis, the recommended architecture is:

- **Per‑player capability** `IRunicTomeData` storing `Set<BookKey>` and a `hasReceivedTome` flag.
- **Runic Tome item** as a regular Forge `Item`, granted via `PlayerLoggedInEvent` when `hasReceivedTome` is false.
- **Server‑authoritative logic**:
  - Intercepts pickups via `EntityItemPickupEvent` (or equivalent 1.20 callback) to absorb guide books into the capability and prevent inventory pollution.[^18]
  - Optionally intercepts crafting and other acquisition paths; supplemented by a periodic inventory sweep for safety.
- **Client GUI** implemented as a custom `Screen` reading from the synced capability and listing unlocked and known books with search, grouping, and favorites.
- **Adapter‑based compatibility layer** with Patchouli and major manual systems, pluggable via public API and IMC.
- **Minimal optional mixins** confined to specific, high‑value troublesome mods where Forge events and APIs are insufficient.

This design is Forge‑native, maintainable, and compatible with most guide systems while accepting that some edge cases cannot be fully automated.

***

## 13. Target Mods/Systems to Support First

Priority list for initial support:

1. **Patchouli books** (core system)
   - Supports any mod with Patchouli docs; simplest to integrate using `PatchouliAPI`.[^16][^2]

2. **Botania’s Lexica (Patchouli variants)**
   - Many packs use a Patchouli‑based Botania lexicon; these are automatically covered via Patchouli integration.[^15][^2]

3. **Tinkers’ Construct "Materials and You"**
   - Widely used starter book, automatically granted on world load.[^9]

4. **Immersive Engineering manual** (including Patchouli port where present)
   - Popular in tech packs; some ports already implemented via Patchouli making integration easy.[^10]

5. **Akashic Tome** itself
   - As a meta‑documentation item, Runic Tome should at minimum understand when a book is only accessible via Akashic Tome and avoid fighting its behavior.

Once these are solid, expand to other big mods’ manuals and any mod willing to register an adapter.

***

## 14. Risk Assessment

### 14.1 Technical risks

- **Incomplete interception of book grants**
  - Severity: medium – may leave some duplicates but not crash.
  - Mitigation: multi‑layer interception (pickup events, crafting events, inventory sweeps), and opt‑in mode where some books are exempt.

- **GUI opening incompatibilities**
  - Severity: medium – clicking book may do nothing or error.
  - Mitigation: adapter abstraction with per‑system strategies and robust error handling; fallback message if an adapter fails.

- **Capability state loss or desync**
  - Severity: medium to high – players could lose unlocks.
  - Mitigation: follow Forge capability guidelines for persistence and cloning; add debug logging and commands to repair or inspect capability content.[^5][^1]

- **Mixin conflicts**
  - Severity: high but localized – only if mixins are used for optional integrations.
  - Mitigation: keep core mod mixin‑free; isolate optional mixins into an addon module or config‑gated feature.

### 14.2 Design/UX risks

- **Player confusion**
  - Players may be confused when guide books disappear from the world/inventory.
  - Mitigation: clear onboarding message when first book is absorbed; icons showing book ownership in GUI.

- **Progression interference**
  - Virtualization might break mods that track progression via items.
  - Mitigation: adapter per system; for problematic mods, choose a "reference" mode where the book is not virtualized but just listed.

### 14.3 Maintenance risks

- **API churn in Patchouli and other guide systems**
  - Mitigation: depend only on documented APIs; version‑gate integrations if necessary.

- **New guide systems**
  - Mitigation: adapter architecture allows new integrations without core changes.

***

## 15. Step‑by‑Step Implementation Roadmap

### Phase 1 – Minimum Viable Version (Forge‑native core)

1. **Set up mod skeleton**
   - Forge 1.20.1 mod with standard entry point.
   - Define Runic Tome item and register via Forge’s registry system.

2. **Player capability implementation**
   - Define `IRunicTomeData` interface and implementation with `Set<BookKey>` and `hasReceivedTome` flag.
   - Register capability using `RegisterCapabilitiesEvent` or `@AutoRegisterCapability`.[^1][^5]
   - Attach to players in `AttachCapabilitiesEvent<Entity>` with a persistent provider.
   - Implement `serializeNBT`/`deserializeNBT` to store unlocked book list.
   - Handle `PlayerEvent.Clone` to copy data on death.[^1]

3. **First‑join tome grant**
   - Subscribe to `PlayerLoggedInEvent` on Forge event bus.[^22]
   - If capability’s `hasReceivedTome` is false, give Runic Tome `ItemStack` and set flag.

4. **Manual book unlock and GUI stub**
   - Add a debug command `/runictome unlock <bookId>` that inserts a dummy `BookKey` into capability for testing.
   - Implement a basic GUI listing unlocked `BookKey`s with no integrations yet.

5. **Networking**
   - Implement custom packets to sync capability data to client on login and when unlocks change.

Deliverable: a working Runic Tome item that persists a set of symbolic books per player and displays them in a GUI.

***

### Phase 2 – Robust Compatibility Version

6. **Patchouli integration adapter**
   - Implement `GuideSystemAdapter` for Patchouli.
   - Use `PatchouliAPI.instance.openBookGUI(...)` to open books by ID.[^16]
   - Implement `identify(ItemStack stack)` by matching `patchouli:guide_book` and reading `"patchouli:book"` NBT.[^2]

7. **Pickup interception**
   - Subscribe to `EntityItemPickupEvent` (or its Forge 1.20.1 equivalent).
   - For each pickup, run adapter `identify` on the item stack:
     - If the stack is a known guide book and the book is not unlocked, unlock and consume the item.
     - If the book is already unlocked, cancel pickup and remove item entity.[^18]

8. **Inventory sweep**
   - On a configurable interval (e.g., every second), scan each player’s inventory server‑side for guide books.
   - Apply same unlock/consume logic to ensure books granted by commands or other mods are absorbed.

9. **Core UX**
   - Extend GUI to show mod names, icons, and search.
   - Display a toast or chat message when new books are unlocked.

10. **Configuration**
   - Config options for:
     - Enabling/disabling automatic absorption.
     - Per‑mod overrides (e.g., do not virtualize Tinkers’ manual).

Deliverable: Runic Tome that automatically captures Patchouli books from any mod, prevents duplicates, and opens them via Patchouli API from the Tome GUI.

***

### Phase 3 – Polished Long‑Term Architecture

11. **Adapter API and IMC integration**
   - Publish `RunicTomeAPI` with registration methods and documentation.
   - Listen for IMC messages from other mods to dynamically register adapters and book metadata.

12. **Specialized adapters for major mods**
   - Implement dedicated adapters for:
     - Botania’s Lexica (Patchouli variant or custom system as applicable).
     - Tinkers’ Construct "Materials and You" (item‑based recognition and `ItemStack` use simulation).[^9]
     - Immersive Engineering manual (Patchouli variant or custom GUI).[^10]
   - Provide per‑adapter configs for enabling/disabling them.

13. **Advanced GUI features**
   - Add mod grouping, favorites, and progression indicators.
   - Implement "All books" vs "Unlocked books" tabs.
   - Support keyboard navigation and improved visuals.

14. **Optional mixin/patch module**
   - For particularly stubborn manuals that cannot be opened or intercepted via events/APIs, create an optional addon that uses mixins to:
     - Hook into their grant logic to suppress physical items and instead unlock in Runic Tome.
     - Expose internal APIs for opening screens.
   - Keep this module clearly separated and gated via config.

15. **Testing and hardening**
   - Test in modpacks with popular guide systems and heavy mod counts.
   - Add extensive logging and debug commands (e.g., `/runictome debug scan`, `/runictome list`) to inspect internal state.

Deliverable: a polished, extensible Runic Tome mod with strong Patchouli integration, adapters for major manual systems, robust duplicate prevention, and a high‑quality GUI suitable for large modpacks.

***

## 16. Conclusion

Runic Tome is technically feasible on Forge 1.20.1 using a player capability for per‑player book state, event‑driven interception for book acquisition, and an adapter‑driven compatibility layer for interacting with diverse guide systems. Forge’s capability and event systems, plus widely‑used systems like Patchouli, provide solid hooks for building a maintainable, modpack‑friendly "master guide compendium" with minimal invasive patching.[^5][^1][^16][^2]

---

## References

1. [Capabilities - Minecraft Forge Documentation](https://docs.minecraftforge.net/en/latest/datastorage/capabilities/) - Persisting across Player Deaths. By default, the capability data does not persist on death. In order...

2. [Getting Started | Patchouli - GitHub Pages](https://vazkiimods.github.io/Patchouli/docs/patchouli-basics/getting-started/) - This entry serves as a quick guide of what to do to get started making your own Patchouli books, rea...

3. [Akashic Tome - Minecraft Mod](https://modrinth.com/mod/akashic-tome) - The Book of Books. Store every documentation book in one.

4. [Akashic Tome - Feed The Beast Wiki](https://ftb.fandom.com/wiki/Akashic_Tome) - Akashic Tome is a mod created by Vazkii. It adds a single item of the same name, a book capable of h...

5. [Capabilities - Forge Community Wiki - Gemwire](https://forge.gemwire.uk/wiki/Capabilities) - Persistent Provider: a provider that requires all capabilities to serialize data to disk, in order t...

6. [[  ]: [1.20.1] Mixin injecting SynchedEntityData into entities not owned ...](https://github.com/MehVahdJukaar/Supplementaries/issues/851) - The reason for this is because Entity Data Accessors are order dependent so if multiple mods tries t...

7. [Synchronizing Entities - Minecraft Forge Documentation](https://docs.minecraftforge.net/en/latest/networking/entities/) - Firstly, you need a EntityDataAccessor<T> for the data you wish to keep synchronized. This should be...

8. [Persistent State - Fabric Wiki](https://wiki.fabricmc.net/tutorial:persistent_states) - Let's send a simple packet to the player when the server detects the player breaks a dirt block, and...

9. [Materials and You: Volume 1 | Tinkers' Construct Wiki - Fandom](https://tinkers-construct.fandom.com/wiki/Materials_and_You:_Volume_1) - Materials and You by Skyla is a book that you automatically receive after loading a world for the fi...

10. [Immersive engineering Patchouli guide (Archive) - Minecraft Mod](https://modrinth.com/mod/immersive-engineering-patchouli-guide) - This is a Patchouli port of the Engineer's manual from Immersive Engineering, with it being largely ...

11. [PlayerEvent.ItemPickupEvent (Forge API 1.9.4-12.17.0.2051)](https://skmedix.github.io/ForgeJavaDocs/javadoc/forge/1.9.4-12.17.0.2051/net/minecraftforge/fml/common/gameevent/PlayerEvent.ItemPickupEvent.html) - Nested Class Summary. Nested classes/interfaces inherited from class net.minecraftforge.fml.common.g...

12. [PlayerEvent.PlayerLoggedInEvent (Forge API 1.11.2-13.20.0.2228)](https://skmedix.github.io/ForgeJavaDocs/javadoc/forge/1.10.2-12.18.3.2185/net/minecraftforge/fml/common/gameevent/PlayerEvent.PlayerLoggedInEvent.html) - PlayerEvent.PlayerLoggedInEvent extends PlayerEvent. Nested Class Summary Nested classes/interfaces ...

13. [[1.20.4] starting a world for the first time event, to give a player a ...](https://forums.minecraftforge.net/topic/146304-1204-starting-a-world-for-the-first-time-event-to-give-a-player-a-briefcase-whit-items-once/) - Hello! I'm having an issue: the texture of my animated block is not showing when using GeckoLib in a...

14. [Creating a guide book using Patchouli mod - MCreator](https://mcreator.net/forum/76372/creating-guide-book-using-patchouli-mod) - Open your workspace settings and go to 'External APIs' and enable 'Patchouli'. Say 'Yes' or 'Confirm...

15. [Can you create Patchouli Books without any Knowledge on Coding?](https://www.reddit.com/r/feedthebeast/comments/1ao5g0e/can_you_create_patchouli_books_without_any/) - It is entirely possible to make a usable book without writing any Java code. Writing any of the defa...

16. [Feature Request: API method to open book at a specific entry ID #94](https://github.com/Vazkii/Patchouli/issues/94) - As the subject says: it would be nice to be able to do something like // server version PatchouliAPI...

17. [Akashic Tome Mod (1.21.1, 1.20.1) - Hold Mod Tools Like ...](https://www.9minecraft.net/akashic-tome-mod/) - Akashic Tome Mod is a mod based on Morph-o-Tool. The only item it adds is the Akashic Tome, crafted ...

18. [EntityItemPickupEvent (forge 1.16.5-36.2.39)](https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.16.5/net/minecraftforge/event/entity/player/EntityItemPickupEvent.html) - This event is called when a player collides with a EntityItem on the ground. The event can be cancel...

19. [Events - Forge Community Wiki](https://forge.gemwire.uk/wiki/Events) - Forge exposes three main families of event buses: the main Forge event bus, the mod-specific event b...

20. [I also have problem : r/fabricmc - Reddit](https://www.reddit.com/r/fabricmc/comments/19bo55y/i_also_have_problem/) - The book author should enable this flag and move all book contents clientside to /assets/, leaving t...

21. [Starter Items Mod (1.20.1, 1.19.2) - Custom Kits And ... - 9Minecraft](https://www.9minecraft.net/starter-items-mod/) - Starter Items Mod lets server owners and players configure what items new players receive when they ...

22. [PlayerLoggedInEvent - CraftTweaker Documentation](https://docs.blamejared.com/1.20/en/forge/api/event/entity/player/PlayerLoggedInEvent) - Documentation for the CraftTweaker Minecraft mod, information on how to use the ZenScript language a...

