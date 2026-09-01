# Runic Tome

### One tome. Every manual.

Tired of inventory chests stuffed with duplicate guide books? Losing your "Materials and You" to a lava pocket? Dragging eight lexicons between bases every time you move? **Runic Tome fixes that.**

Runic Tome gives every player a single, persistent book called the **Runic Tome**. The moment any guide book enters your inventory — pickup, craft, quest reward, command, anything — the tome quietly absorbs it. The physical book vanishes; a new entry appears in your tome's library. One item, forever.

Open the tome to browse every manual you've ever collected. Click an entry. The real book opens, native UI and all.

---

## ✨ Features

- **Absorbs every guide book you touch — without ever eating the wrong thing.** Ground pickup, crafting output, smelting output, quest rewards, KubeJS grants, /give commands, container close — all paths covered, near-instantly. A book is only removed once it's safely stored in your tome; if storage ever fails, the item is left untouched.
- **One item, forever.** The tome is given on your first world join, has Epic rarity, stacks to 1, and is fire-resistant. Your unlocked books persist across death, item loss, and the loss of the tome itself — and corrupt entries can't break your save.
- **Searchable library UI.** Opening the tome shows a scrolling list of every book you've collected, with real item icons and a live count. Search by name, **favorite** books (right-click) to pin them to the top, and **left-click** to launch the original book's native GUI.
- **Take a book back out.** Every row has an **Extract** button that returns one physical copy with its original data and removes the virtual entry. Extracted copies are permanently exempt from re-absorption, so they stay physical — for you and for anyone else who picks them up.
- **Copy a book for your library room.** Next to Extract is a **Copy** button: it prints a spare physical book for one vanilla book (free in creative) and *keeps* the entry in your tome. Copies carry the same never-re-absorb mark extracted books do, so they stay put in item frames, lecterns and shelves instead of being swallowed again.
- **Pause the eating.** An **Absorb: On / Off** toggle at the bottom of the tome stops it taking books you pick up, craft or carry, so you can sort a chest of dungeon loot in peace. It is per-player, sticks through death, and never blocks books you deliberately craft into the tome.
- **Add books on purpose, by crafting.** Put the tome and one or more books in any crafting grid -- the 2x2 inventory grid or a full table -- and take the tome back out; the books are now in your library. This is also the only way to store **vanilla** books (written, enchanted, knowledge, book and quill), which are never absorbed automatically -- each one becomes its own entry, written books open in the vanilla reader, and Extract gives back the exact item. Put something that isn't a book in the grid and the recipe simply produces nothing.
- **Wear it instead of carrying it.** Install [Curios](https://www.curseforge.com/minecraft/mc-mods/curios) and the tome gets its own slot — with a book icon drawn to match the built-in Curios slots — so it stops taking up an inventory square forever. It survives death in the slot even without keepInventory. Curios is optional; skip it and nothing changes.
- **No inventory clutter.** Books live virtually in a per-player data store — not as real ItemStacks. Your inventory stays clean.
- **Works with everything Patchouli.** Out of the box, Runic Tome recognises every Patchouli book in your pack — including mods that use custom book items (Ars Nouveau's Worn Notebook, Botania's Lexica Botania, and so on).
- **Lots of built-in integrations.** Tinkers' Construct (all six books), Minecraft Comes Alive, Better Animals Plus, Immersive Engineering (manual), and Modonomicon are detected automatically — plus a keyword catch-all for guide books from mods with no explicit support.
- **Modpack- and datapack-configurable.** Add any standalone book-like item via a simple config entry, or define books in a datapack (`data/<namespace>/runictome/books/*.json`) that syncs to clients. No code, no patching.
- **Built for packmakers who don't want surprises.** `/runictome debug scan` tells you exactly which items in *your* pack your configuration would absorb — before a player loses something that mattered. More on this below.
- **Mod-author friendly.** A small public API and IMC hooks let other mods register their own guide systems — or a single openable book.

---

## 🎮 How it works

1. **Join a world.** A Runic Tome appears in your inventory automatically.
2. **Receive a guide book.** Any way. Pickup, craft, quest reward, `/give`, anything. The tome absorbs it near-instantly — you'll see the book disappear and a toast (with the book's name) confirming the unlock.
3. **Right-click the tome.** A searchable, scrolling library opens listing every book you've collected, each with its icon.
4. **Find and open.** Type to search, **left-click** an entry to open its native UI, **right-click** to favorite it (favorites pin to the top), hit **Copy** for a spare, or hit **Extract** to take the physical copy back out.
5. **Close with Done or Escape.** Your tome goes back in your hotbar.

That's it. No crafting recipe, no ritual, no GUI to learn. The mod is effectively invisible until you want to read.

---

## 🔌 Compatibility

**Confirmed supported guide systems:**

- Patchouli (and every mod that uses it — Botania, Ars Nouveau, Ice & Fire Delight, Create Addons, and many more)
- Tinkers' Construct (all six books)
- Minecraft Comes Alive, Better Animals Plus, Immersive Engineering (manual), Modonomicon
- A keyword catch-all that absorbs guide books from mods with no explicit support

**Add more via config or datapack:** drop any item ID into `extraBookItemIds` in `config/runictome-common.toml`, or define books in `data/<namespace>/runictome/books/*.json`. Example config:

```toml
[integrations]
extraBookItemIds = [
    "immersiveengineering:manual",
    "occultism:dictionary_of_spirits",
    "farmersdelight:cookbook",
]
```

**For mod authors:** Runic Tome exposes a public `GuideSystemAdapter` API and an IMC registration path. If you ship a mod with its own guide system, hooking in takes about 20 lines of code. See the [GitHub repository](https://github.com/otectus/runic-tome) for details.

---

## 🧰 For modpack authors

Runic Tome works out of the box, but a big pack deserves a five-minute audit. The catch-all is deliberately broad, so the question worth answering is *"which items in **my** pack does my configuration actually absorb?"*

Run **`/runictome debug scan`** in-game. It classifies every registered item through the same code path absorption uses and reports:

- how many items would be absorbed, broken down by adapter and by mod;
- the full list, grouped by mod, written to `runictome-scan.txt` in the server directory;
- the items an adapter *wanted* but a global exclusion protected — i.e. what your exclusions are currently buying you.

Anything in that first list that's functional gear rather than documentation goes into `absorbExclusionItems` / `absorbExclusionMods`, or the `#runictome:absorb_blocklist` tag. Config changes apply on reload, so you can iterate without restarting. Two more tools:

- **`/runictome debug identify`** — hold an item and get a full explanation of how it's classified and whether it would be absorbed.
- **`/runictome purge`** — if a functional book was absorbed before you excluded it, this lists the affected entries and `purge confirm` removes them. No save editing required.

Every player-scoped command takes an optional target player, so you can inspect and repair a player's library from the server console.

---

## ❓ FAQ

**Q: Does this replace my existing books?**
A: It absorbs them into a virtual library on first contact. The original books vanish, but you can still open them through the tome with one click — and the original mod's native UI is preserved. You can also pull a physical copy back out at any time with the **Extract** button.

**Q: What happens to duplicates?**
A: The tome stores exactly one copy of each book. By default the rest of the stack is consumed — absorbing a stack of eight identical lexicons keeps one and destroys the other seven, and picking up a book you already own consumes it. If you'd rather keep the extras, set `absorbWholeStack = false` in the config: the tome then takes one copy of each *new* book and never touches duplicates.

**Q: I want to decorate a library room with real books. Can I?**
A: Yes — that's what **Copy** is for. A copied (or extracted) book is permanently marked as hands-off, so it will sit in an item frame, lectern or shelf without the tome eating it again. For a temporary reprieve rather than a permanent one, flip **Absorb: Off** in the tome screen.

**Q: What if I lose the tome?**
A: Your unlocked books are stored per-player, not in the tome itself, and they survive death and item loss. The tome is also kept through death and is fire-resistant, so losing it takes effort. If you do destroy it, an operator can hand you another with `/runictome give` — the automatic grant only fires on your first join, and there is no crafting recipe.

**Q: Does it work on dedicated servers?**
A: Yes. The mod is server-authoritative — all absorption decisions happen server-side and the unlocked-book list is synced to clients. Install **the same version** on both sides: Runic Tome checks its network protocol version at login and refuses a mismatch rather than misbehaving.

**Q: Will it absorb something I don't want absorbed?**
A: The keyword catch-all only matches book-like items and skips block items, vanilla books, and a configurable blocklist (including functional "spellbook"/"tome" gear from mods like Iron's Spells 'n Spellbooks, Ars Nouveau, and Scriptor). It also skips anything that *looks functional* — items whose path contains `spell`, `scroll`, `caster`, `focus`, `rune`, `wand`, `skill`, `level`, `xp`, `summon`, or `upgrade`, or that are damageable, enchanted, or carry meaningful NBT — so functional gear with a book-like name is left alone. On top of that, a global exclusion list of known functional books from large packs is always active, even if your config predates it. Packmakers get two override tags, `#runictome:guide_books` (force-absorb) and `#runictome:absorb_blocklist` (never absorb), plus `/runictome debug scan` to audit the whole pack up front. And nothing is ever destroyed on a failed unlock — if a book can't be stored, the item simply stays where it is.

**Q: How do I add support for a book my mod uses?**
A: If your mod uses Patchouli, it already works. If it uses a standalone item, add the item ID to `extraBookItemIds`. If you're building a new guide system, implement `GuideSystemAdapter` and register via IMC — see the GitHub README.

**Q: What's the performance cost?**
A: Negligible. Books are absorbed event-driven (on pickup, craft, smelt, and container close). A timer sweep runs once per second by default (configurable via `sweepIntervalTicks`) as a safety net for inventory inserts that bypass Forge events, e.g. `/give` — and players are staggered across the interval, so a busy server never pays for everyone on the same tick.

---

## 📦 Installation

1. Install [Minecraft Forge 47.3.0+](https://files.minecraftforge.net/) for Minecraft 1.20.1.
2. Download the latest Runic Tome jar from this CurseForge page.
3. Drop it into your `mods/` folder.
4. Launch Minecraft. You're done.

Runic Tome has **no hard dependencies.** Patchouli and Tinkers' Construct are both detected at runtime if present — the mod will gracefully skip integrations that can't be loaded.

On a server, install the same version on client and server.

---

## 🔗 Links

- **GitHub:** https://github.com/otectus/runic-tome
- **Issue tracker:** https://github.com/otectus/runic-tome/issues
- **Changelog:** see `CHANGELOG.md` in the repo
- **Architecture docs:** see `RUNIC-TOME-ARCHITECTURE.md` in the repo

---

## 🙏 Credits

- **Otectus** — author, code, design.
- **vazkii** — for Patchouli, without which half this mod's value wouldn't exist.
- The Forge MDK, Parchment mappings, and the Minecraft Forge community.

---

**License:** MIT. Free to use in modpacks, streams, videos, and derivative works. Attribution appreciated but not required.
