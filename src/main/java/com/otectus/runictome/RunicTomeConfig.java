package com.otectus.runictome;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public final class RunicTomeConfig {

    public static final List<String> DEFAULT_ABSORB_EXCLUSION_ITEMS =
            List.of(
                    // Never absorbable by any adapter at any priority: absorbing the library itself
                    // would destroy it. The heuristic already skips this mod's namespace, but the
                    // tagged and extraBookItemIds adapters do not.
                    "runictome:runic_tome",
                    "epicfight:skillbook",
                    "runicskills:leveling_book",
                    "legendary_additions:experience_book",
                    "alexscaves:cave_codex",
                    "art_of_forging:esoteric_codex",
                    "aylelrpgdrops:mark_grimoire",
                    "aylelrpgdrops:recall_grimoire",
                    "customnpcs:nbt_book",
                    "dungeons_and_combat:combat_style_manual",
                    "uniqueaccessories:ninjutsu_manual",
                    "cthulhufishing:cthulhu_grimoire",
                    "threateningly_mobs:skeleton_book",
                    "celestisynth:celestial_spell_book",
                    "dacxirons:divine_manuscript_spell_book",
                    "sgjourney:archeologist_notebook",
                    "jsg:notebook",
                    "jsg:page_notebook_empty",
                    "jsg:page_notebook_filled",
                    "valoria:summon_book",
                    "valoria:crystal_summon_book",
                    "valoria:necromancer_grimoire",
                    "goety:infernal_tome",
                    "goety:grimoire_of_goodwill",
                    "goety:grimoire_of_grounding",
                    "goety:grimoire_of_grudges");
    public static final List<String> DEFAULT_ABSORB_EXCLUSION_MODS =
            List.of("scriptor", "goetyawaken", "wrd");

    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        COMMON = new Common(b);
        COMMON_SPEC = b.build();

        ForgeConfigSpec.Builder cb = new ForgeConfigSpec.Builder();
        CLIENT = new Client(cb);
        CLIENT_SPEC = cb.build();
    }

    private RunicTomeConfig() {}

    /**
     * Whether the unlock toast should be shown, resolved across both specs.
     *
     * <p>{@code showUnlockToast} is a purely client-side preference that historically lived in the
     * COMMON spec, where a dedicated server's copy of the file has no effect on any player: Forge
     * does not sync COMMON configs. It now lives in {@code runictome-client.toml}.
     *
     * <p>The old COMMON option is retained as an AND-gate for one deprecation cycle so that a player
     * who had already turned toasts off does not silently get them back on update. Both default to
     * {@code true}, so a fresh install is governed by the client option alone. The COMMON option is
     * scheduled for removal; until then, an explicit {@code false} in either file wins.
     */
    public static boolean showUnlockToast() {
        boolean client = !CLIENT_SPEC.isLoaded() || CLIENT.showUnlockToast.get();
        boolean legacyCommon = !COMMON_SPEC.isLoaded() || COMMON.showUnlockToast.get();
        return client && legacyCommon;
    }

    /**
     * Whether absorbing a book consumes the entire source stack. Tolerates an unloaded spec so unit
     * tests and early-startup callers see the documented default.
     */
    public static boolean absorbWholeStack() {
        return !COMMON_SPEC.isLoaded() || COMMON.absorbWholeStack.get();
    }

    /**
     * Whether the tome-plus-books crafting recipe is enabled. Tolerates an unloaded spec because
     * {@link com.otectus.runictome.impl.TomeCraftingMatcher} is unit-tested without one, and because
     * recipe matching can be reached before the config file is read.
     */
    public static boolean absorbViaCrafting() {
        return !COMMON_SPEC.isLoaded() || COMMON.absorbViaCrafting.get();
    }

    /** Whether that recipe accepts the vanilla book family. Spec-tolerant for the same reasons. */
    public static boolean craftingAcceptsVanillaBooks() {
        return !COMMON_SPEC.isLoaded() || COMMON.craftingAcceptsVanillaBooks.get();
    }

    /** Whether players may copy an unlocked book. Spec-tolerant; see {@link #absorbWholeStack()}. */
    public static boolean allowBookCopying() {
        return !COMMON_SPEC.isLoaded() || COMMON.allowBookCopying.get();
    }

    /** Vanilla books charged per copy. Spec-tolerant; see {@link #absorbWholeStack()}. */
    public static int bookCopyCost() {
        return COMMON_SPEC.isLoaded() ? COMMON.bookCopyCost.get() : 1;
    }

    public static final class Common {
        public final ForgeConfigSpec.BooleanValue absorbOnPickup;
        public final ForgeConfigSpec.BooleanValue absorbOnCraft;
        public final ForgeConfigSpec.BooleanValue absorbViaCrafting;
        public final ForgeConfigSpec.BooleanValue craftingAcceptsVanillaBooks;
        public final ForgeConfigSpec.BooleanValue verboseLogging;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> extraBookItemIds;
        public final ForgeConfigSpec.BooleanValue absorbUnknownBooks;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> bookKeywords;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> bookBlocklist;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> bookBlocklistMods;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> absorbExclusionItems;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> absorbExclusionMods;
        public final ForgeConfigSpec.BooleanValue showUnlockToast;
        public final ForgeConfigSpec.BooleanValue allowBookExtraction;
        public final ForgeConfigSpec.BooleanValue allowBookCopying;
        public final ForgeConfigSpec.IntValue bookCopyCost;
        public final ForgeConfigSpec.BooleanValue absorbWholeStack;
        public final ForgeConfigSpec.IntValue sweepIntervalTicks;
        public final ForgeConfigSpec.IntValue maxFavoriteTogglesPerSecond;
        public final ForgeConfigSpec.BooleanValue grantTomeOnFirstJoin;

        Common(ForgeConfigSpec.Builder b) {
            b.push("absorption");
            absorbOnPickup = b.comment("Absorb guide books when picked up from the ground.")
                    .define("absorbOnPickup", true);
            absorbOnCraft = b.comment("Absorb guide books produced by crafting or smelting.")
                    .define("absorbOnCraft", true);
            absorbWholeStack = b.comment(
                    "Whether absorbing a book consumes the ENTIRE source stack (default, and the",
                    "behavior of every release before 0.6.0). The Runic Tome only ever stores one",
                    "copy of a book, so with this enabled a stack of 8 identical manuals yields one",
                    "extractable copy and the other 7 are destroyed; picking up a book you already",
                    "own destroys it outright.",
                    "Set to false to consume exactly ONE item per newly-unlocked book and leave the",
                    "rest physical. With false, a duplicate of a book you already own is never",
                    "consumed at all -- extras stay in your inventory, on the ground, or in the",
                    "crafting output.")
                    .define("absorbWholeStack", true);
            absorbViaCrafting = b.comment(
                    "Enable the crafting recipe that files books into the tome: place the Runic Tome",
                    "plus one or more books in any crafting grid (the 2x2 inventory grid or a crafting",
                    "table) and the output is the Runic Tome with those books added.",
                    "Unlike the ambient absorption above this is a deliberate player action, so it also",
                    "accepts a book that was previously extracted from the tome. Books that are hard",
                    "excluded (absorbExclusionItems/Mods, #runictome:absorb_blocklist) are still refused,",
                    "and an unrecognized item anywhere in the grid makes the recipe produce nothing.")
                    .define("absorbViaCrafting", true);
            craftingAcceptsVanillaBooks = b.comment(
                    "Let the crafting recipe accept the vanilla book family: book, writable_book,",
                    "written_book, enchanted_book and knowledge_book. These are never absorbed",
                    "ambiently -- crafting is the only way in, because eating somebody's Mending book",
                    "off the ground would be indefensible.",
                    "Each stack is stored under its own entry (two written books with different titles",
                    "are two entries) and can be extracted again to recover the exact item.",
                    "NOTE: the tome keeps exactly ONE copy per distinct book, so crafting in a second",
                    "identical enchanted book destroys it. To allow written books but not enchanted",
                    "ones, leave this enabled and add minecraft:enchanted_book to the",
                    "#runictome:absorb_blocklist item tag.")
                    .define("craftingAcceptsVanillaBooks", true);
            b.pop();

            b.push("integrations");
            extraBookItemIds = b.comment(
                    "Extra item IDs to treat as single-item guide books (item is absorbed and tracked).",
                    "Use \"modid:item_path\" form. Unknown or malformed IDs are logged and skipped.",
                    "Example: [\"immersiveengineering:manual\", \"occultism:dictionary_of_spirits\"]")
                    .defineListAllowEmpty("extraBookItemIds", List.of(),
                            o -> o instanceof String s && s.contains(":"));

            absorbUnknownBooks = b.comment(
                    "Catch-all: absorb ANY item whose registry path matches a documentation",
                    "keyword (see bookKeywords), unless it is a block item, one of this mod's",
                    "own items, or listed in bookBlocklist. This is what captures guide books",
                    "from mods that have no explicit Runic Tome support. Disable to absorb only",
                    "explicitly-supported books (Patchouli, Tinkers, configured, IMC).")
                    .define("absorbUnknownBooks", true);

            bookKeywords = b.comment(
                    "Keywords matched as a case-insensitive substring against an item's registry",
                    "path to treat it as a documentation book when absorbUnknownBooks is enabled.")
                    .defineListAllowEmpty("bookKeywords",
                            List.of("book", "manual", "guide", "guidebook", "lexicon", "tome",
                                    "dictionary", "codex", "almanac", "journal", "encyclopedia",
                                    "compendium", "handbook", "grimoire", "primer"),
                            o -> o instanceof String);

            bookBlocklist = b.comment(
                    "HEURISTIC-ONLY: item IDs the keyword catch-all skips (vanilla and functional books).",
                    "This does NOT stop an explicit adapter (Patchouli, config, IMC, datapack) from absorbing",
                    "the item -- for a hard, cross-adapter block use absorbExclusionItems instead.",
                    "Use \"modid:item_path\" form. Block items and this mod's items are excluded already.")
                    .defineListAllowEmpty("bookBlocklist",
                            List.of("minecraft:book", "minecraft:writable_book", "minecraft:written_book",
                                    "minecraft:enchanted_book", "minecraft:knowledge_book"),
                            o -> o instanceof String s && s.contains(":"));

            bookBlocklistMods = b.comment(
                    "HEURISTIC-ONLY: mod IDs the keyword catch-all skips. Use this for mods whose",
                    "'book'/'tome' items are functional gear rather than documentation. This does NOT",
                    "stop an explicit adapter from absorbing them -- for a hard, cross-adapter block use",
                    "absorbExclusionMods instead. Defaults exclude Iron's Spells 'n Spellbooks, Ars Nouveau,",
                    "and Scriptor. Note: documentation books these mods ship via Patchouli, IMC, datapacks,",
                    "or the positive #runictome:guide_books tag are still absorbed, because those adapters",
                    "run before this catch-all (e.g. Ars Nouveau's Worn Notebook).")
                    .defineListAllowEmpty("bookBlocklistMods",
                            List.of("irons_spellbooks", "ars_nouveau", "scriptor"),
                            o -> o instanceof String);

            absorbExclusionItems = b.comment(
                    "EXTRA GLOBAL HARD EXCLUSIONS: item IDs NEVER absorbed by ANY adapter (Patchouli, explicit,",
                    "config, datapack, IMC, tagged, and the heuristic all honor this). Matches the base item",
                    "id, so every NBT/data variant of a shared item is excluded. Built-in safety exclusions",
                    "for known functional books are always active even when an older config omits them.",
                    "Use \"modid:item_path\" form. Malformed IDs are logged and skipped.")
                    .defineListAllowEmpty("absorbExclusionItems", DEFAULT_ABSORB_EXCLUSION_ITEMS,
                            o -> o instanceof String s && s.contains(":"));

            absorbExclusionMods = b.comment(
                    "EXTRA GLOBAL HARD EXCLUSIONS: namespaces NEVER absorbed by ANY adapter. Use for mods whose",
                    "'book'/'tome' items are functional/stateful gear that must remain physical.",
                    "Built-in safety namespaces are always active even when an older config omits them.",
                    "Malformed namespaces are logged and skipped.")
                    .defineListAllowEmpty("absorbExclusionMods", DEFAULT_ABSORB_EXCLUSION_MODS,
                            o -> o instanceof String);
            b.pop();

            b.push("ui");
            showUnlockToast = b.comment(
                    "DEPRECATED -- moved to runictome-client.toml (client.showUnlockToast).",
                    "This is a per-player display preference, and Forge does not sync COMMON configs,",
                    "so setting it on a dedicated server never affected any player. It is still read",
                    "for one release so an existing 'false' is not silently reset: toasts appear only",
                    "when BOTH this and the client option are true. Set the client option instead;",
                    "this key will be removed in a future release.")
                    .define("showUnlockToast", true);
            allowBookExtraction = b.comment(
                    "Allow players to extract an unlocked book back into a physical item.",
                    "Extracted stacks retain their data and are marked so inventory sweeps do not re-absorb them.")
                    .define("allowBookExtraction", true);
            allowBookCopying = b.comment(
                    "Allow players to copy an unlocked book without removing it from their tome.",
                    "Unlike extraction the library entry is kept, so the copy costs bookCopyCost",
                    "vanilla books. Copies carry the same no-re-absorb mark extracted books do, which",
                    "is what makes them usable as decorative shelf and item-frame books.")
                    .define("allowBookCopying", true);
            bookCopyCost = b.comment(
                    "Vanilla minecraft:book items consumed per copy. 0 makes copying free.",
                    "Creative-mode players are never charged regardless of this value.")
                    .defineInRange("bookCopyCost", 1, 0, 64);
            b.pop();

            b.push("performance");
            sweepIntervalTicks = b.comment(
                    "How often (in server ticks) to sweep player inventories for unabsorbed books.",
                    "Pickup, craft, and container-close events are handled separately and are unaffected.",
                    "20 = once per second. Set to 1 to restore legacy per-tick behavior.",
                    "Players are staggered across the interval, so each player is still swept once per",
                    "interval but the cost is spread over it rather than landing on a single tick.")
                    .defineInRange("sweepIntervalTicks", 20, 1, 1200);
            maxFavoriteTogglesPerSecond = b.comment(
                    "Server-side cap on how many favorite toggles one player may apply per second.",
                    "Requests over the cap are rejected and answered with the authoritative state, so a",
                    "client cannot use them to force repeated work. Raise only if legitimate rapid",
                    "clicking is being rejected; 0 disables the limit.")
                    .defineInRange("maxFavoriteTogglesPerSecond", 20, 0, 200);
            b.pop();

            b.push("items");
            grantTomeOnFirstJoin = b.comment(
                    "Give a Runic Tome to each player on their first login.",
                    "Disable this on servers that distribute the tome via quests or starting kits.")
                    .define("grantTomeOnFirstJoin", true);
            b.pop();

            b.push("debug");
            verboseLogging = b.comment("Extra logging for capability sync and adapter resolution.")
                    .define("verboseLogging", false);
            b.pop();
        }
    }

    /**
     * Per-player display preferences. These belong in a CLIENT spec because Forge writes it per
     * installation and never syncs it: a value here governs only the player who set it, which is
     * exactly what a display preference should do.
     */
    public static final class Client {
        public final ForgeConfigSpec.BooleanValue showUnlockToast;

        Client(ForgeConfigSpec.Builder b) {
            b.push("ui");
            showUnlockToast = b.comment(
                    "Show a toast notification when a new book is absorbed.",
                    "Replaces the deprecated common-config option of the same name. While that option",
                    "still exists, a 'false' in either file suppresses the toast.")
                    .define("showUnlockToast", true);
            b.pop();
        }
    }
}
