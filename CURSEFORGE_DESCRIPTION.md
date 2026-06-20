# Runic Tome

### One tome. Every manual.

Tired of inventory chests stuffed with duplicate guide books? Losing your "Materials and You" to a lava pocket? Dragging eight lexicons between bases every time you move? **Runic Tome fixes that.**

Runic Tome gives every player a single, persistent book called the **Runic Tome**. The moment any guide book enters your inventory — pickup, craft, quest reward, command, anything — the tome quietly absorbs it. The physical book vanishes; a new entry appears in your tome's library. One item, forever.

Open the tome to browse every manual you've ever collected. Click an entry. The real book opens, native UI and all.

---

## ✨ Features

- **Absorbs every guide book you touch — without ever eating the wrong thing.** Ground pickup, crafting output, smelting output, quest rewards, KubeJS grants, /give commands, container close — all paths covered, near-instantly. A book is only removed once it's safely stored in your tome; if storage ever fails, the item is left untouched.
- **One item, forever.** The tome is given on your first world join, has Epic rarity, stacks to 1, and is fire-resistant. Your unlocked books persist across death, item loss, and the tome itself — and corrupt entries can't break your save.
- **Searchable library UI.** Opening the tome shows a scrolling list of every book you've collected, with real item icons and a live count. Search by name, **favorite** books (right-click) to pin them to the top, and **left-click** to launch the original book's native GUI.
- **No inventory clutter.** Books live virtually in a per-player data store — not as real ItemStacks. Your inventory stays clean.
- **Works with everything Patchouli.** Out of the box, Runic Tome recognises every Patchouli book in your pack — including mods that use custom book items (Ars Nouveau's Worn Notebook, Botania's Lexica Botania, and so on).
- **Lots of built-in integrations.** Tinkers' Construct (all six books), Minecraft Comes Alive, Better Animals Plus, Immersive Engineering (manual), and Modonomicon are detected automatically — plus a keyword catch-all for guide books from mods with no explicit support.
- **Modpack- and datapack-configurable.** Add any standalone book-like item via a simple config entry, or define books in a datapack (`data/<namespace>/runictome/books/*.json`) that syncs to clients. No code, no patching.
- **Mod-author friendly.** A small public API and IMC hooks let other mods register their own guide systems — or a single openable book.

---

## 🎮 How it works

1. **Join a world.** A Runic Tome appears in your inventory automatically.
2. **Receive a guide book.** Any way. Pickup, craft, quest reward, `/give`, anything. The tome absorbs it near-instantly — you'll see the book disappear and a toast (with the book's name) confirming the unlock.
3. **Right-click the tome.** A searchable, scrolling library opens listing every book you've collected, each with its icon.
4. **Find and open.** Type to search, **left-click** an entry to open its native UI, or **right-click** to favorite it (favorites pin to the top).
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

## ❓ FAQ

**Q: Does this replace my existing books?**
A: It absorbs them into a virtual library on first contact. The original books vanish, but you can still open them through the tome with one click — and the original mod's native UI is preserved.

**Q: What if I lose the tome?**
A: Your unlocked books are stored per-player, not in the tome itself. Craft a new tome (or use `/give` — the mod will re-give it automatically on next login if you don't have one), and your full library is still there.

**Q: Does it work on dedicated servers?**
A: Yes. The mod is server-authoritative — all absorption decisions happen server-side and the unlocked-book list is synced to clients. Install it on both sides.

**Q: Will it absorb something I don't want absorbed?**
A: The keyword catch-all only matches book-like items and skips block items, vanilla books, and a configurable blocklist (including functional "spellbook"/"tome" gear from mods like Iron's Spells, Ars Nouveau, RPG Lore, and Scriptor). It also skips anything that *looks functional* — items whose path contains `spell`, `scroll`, `caster`, `focus`, `rune`, or `wand`, or that are damageable or enchanted — so functional gear with a book-like name is left alone. Packmakers get two override tags, `#runictome:guide_books` (force-absorb) and `#runictome:absorb_blocklist` (never absorb), and can run `/runictome debug identify` to see exactly how a held item would be classified. And nothing is ever destroyed on a failed unlock — if a book can't be stored, the item simply stays in your inventory.

**Q: How do I add support for a book my mod uses?**
A: If your mod uses Patchouli, it already works. If it uses a standalone item, add the item ID to `extraBookItemIds`. If you're building a new guide system, implement `GuideSystemAdapter` and register via IMC — see the GitHub README.

**Q: What's the performance cost?**
A: Negligible. Books are absorbed event-driven (on pickup, craft, smelt, and container close). A timer sweep runs once per second by default (configurable via `sweepIntervalTicks`) as a safety net for inventory inserts that bypass Forge events, e.g. `/give`.

---

## 📦 Installation

1. Install [Minecraft Forge 47.3.0+](https://files.minecraftforge.net/) for Minecraft 1.20.1.
2. Download the latest Runic Tome jar from this CurseForge page.
3. Drop it into your `mods/` folder.
4. Launch Minecraft. You're done.

Runic Tome has **no hard dependencies.** Patchouli and Tinkers' Construct are both detected at runtime if present — the mod will gracefully skip integrations that can't be loaded.

---

## 🔗 Links

- **GitHub:** https://github.com/otectus/runic-tome
- **Issue tracker:** https://github.com/otectus/runic-tome/issues
- **Architecture docs:** see `RUNIC-TOME-ARCHITECTURE.md` in the repo

---

## 🙏 Credits

- **Otectus** — author, code, design.
- **vazkii** — for Patchouli, without which half this mod's value wouldn't exist.
- The Forge MDK, Parchment mappings, and the Minecraft Forge community.

---

**License:** MIT. Free to use in modpacks, streams, videos, and derivative works. Attribution appreciated but not required.
