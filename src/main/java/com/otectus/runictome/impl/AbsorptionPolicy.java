package com.otectus.runictome.impl;

import com.otectus.runictome.RunicTome;
import com.otectus.runictome.RunicTomeConfig;
import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.api.IRunicTomeData;
import com.otectus.runictome.api.UnlockResult;
import com.otectus.runictome.integration.ModTags;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Global, adapter-independent safety gate for items that must never be absorbed. */
public final class AbsorptionPolicy {

    /** Persistent marker placed only on a book explicitly extracted by its owner. */
    public static final String EXTRACTED_MARKER = "runictome:extracted";

    private static volatile Set<ResourceLocation> exclusionItems =
            parseItems(RunicTomeConfig.DEFAULT_ABSORB_EXCLUSION_ITEMS);
    private static volatile Set<String> exclusionNamespaces =
            parseNamespaces(RunicTomeConfig.DEFAULT_ABSORB_EXCLUSION_MODS);

    private AbsorptionPolicy() {}

    public enum Reason {
        NOT_EXCLUDED,
        EXTRACTED,
        VIRTUAL,
        GLOBAL_ITEM,
        GLOBAL_NAMESPACE,
        BLOCKLIST_TAG
    }

    public record Decision(boolean excluded, Reason reason) {
        public static final Decision ALLOWED = new Decision(false, Reason.NOT_EXCLUDED);

        public boolean allowed() {
            return !excluded;
        }
    }

    public static Decision evaluate(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Decision.ALLOWED;
        if (isExtracted(stack)) return new Decision(true, Reason.EXTRACTED);
        if (UseSimulator.isVirtual(stack)) return new Decision(true, Reason.VIRTUAL);

        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id != null) {
            if (exclusionItems.contains(id)) return new Decision(true, Reason.GLOBAL_ITEM);
            if (exclusionNamespaces.contains(id.getNamespace())) {
                return new Decision(true, Reason.GLOBAL_NAMESPACE);
            }
        }
        if (stack.is(ModTags.Items.ABSORB_BLOCKLIST)) {
            return new Decision(true, Reason.BLOCKLIST_TAG);
        }
        return Decision.ALLOWED;
    }

    public static boolean isExcluded(ItemStack stack) {
        return evaluate(stack).excluded();
    }

    /**
     * The exclusion gate for a <em>deliberate</em> player action -- today, crafting a book into the
     * tome. Identical to {@link #isExcluded} except that an extraction marker does not exclude.
     *
     * <p>The marker means "the periodic sweep must not take this back", not "the owner may never
     * re-file it". Every other exclusion (virtual stacks, the global item and namespace lists, the
     * {@code #runictome:absorb_blocklist} tag) still applies in full: those protect functional books
     * that must stay physical no matter how deliberately a player asks.
     */
    public static boolean isExcludedForExplicitAction(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Decision decision = evaluate(stack);
        if (!decision.excluded()) return false;
        if (decision.reason() != Reason.EXTRACTED) return true;
        // Do NOT stop here. evaluate() tests the marker first and short-circuits, so a stack that is
        // both extracted and hard-excluded reports EXTRACTED and would otherwise slip through this
        // gate -- handing the crafting path exactly the functional books the exclusion list exists
        // to protect. Re-evaluate an unmarked copy so every other reason is still consulted.
        ItemStack unmarked = stack.copy();
        clearExtracted(unmarked);
        return evaluate(unmarked).excluded();
    }

    /**
     * How many items to consume from a source stack after an unlock attempt — the single decision
     * every absorption site (pickup, craft, smelt, inventory sweep) must agree on.
     *
     * <p>Two invariants hold regardless of configuration:
     * <ul>
     *   <li>Nothing is ever consumed unless the book was reliably stored ({@link UnlockResult#isStored()}).
     *       No storage, no consumption.</li>
     *   <li>Never more than the stack actually holds.</li>
     * </ul>
     *
     * <p>With {@code wholeStack} (the default and the behaviour of every release before 0.6.0) the
     * entire stack is consumed, because the tome deduplicates to a single retained copy: absorbing
     * eight identical manuals yields one extractable copy and destroys the rest.
     *
     * <p>With {@code wholeStack} disabled exactly one item is consumed, and only for a
     * <em>newly</em> unlocked book. A duplicate of an already-owned book is deliberately left
     * physical: {@link UnlockResult#ALREADY_HAD} must not consume here, or the periodic inventory
     * sweep would drain a stack of duplicates one item per sweep — silently reproducing the very
     * behaviour the option exists to switch off.
     *
     * @param available the source stack's current count.
     * @return the number of items to remove, in {@code [0, available]}.
     */
    public static int consumptionFor(UnlockResult result, int available, boolean wholeStack) {
        if (result == null || !result.isStored() || available <= 0) return 0;
        if (wholeStack) return available;
        return result == UnlockResult.ADDED ? 1 : 0;
    }

    /** {@link #consumptionFor(UnlockResult, int, boolean)} using the configured stack policy. */
    public static int consumptionFor(UnlockResult result, int available) {
        return consumptionFor(result, available, RunicTomeConfig.absorbWholeStack());
    }

    /**
     * Clears the extraction exemption from a stack, so a book extracted by mistake can be absorbed
     * again. The marker is otherwise permanent by design; only the operator command exposes this.
     *
     * @return true if the stack carried the marker and it was removed.
     */
    public static boolean clearExtracted(ItemStack stack) {
        if (!isExtracted(stack)) return false;
        net.minecraft.nbt.CompoundTag tag = stack.getOrCreateTag();
        tag.remove(EXTRACTED_MARKER);
        // Leave no empty tag behind: an empty compound is not the same as no compound to the
        // heuristic's "does this stack carry meaningful NBT" check, nor to stack merging.
        if (tag.isEmpty()) {
            stack.setTag(null);
        }
        return true;
    }

    public static boolean isExtracted(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.hasTag()
                && stack.getTag().getBoolean(EXTRACTED_MARKER);
    }

    public static void markExtracted(ItemStack stack) {
        if (stack != null && !stack.isEmpty()) {
            stack.getOrCreateTag().putBoolean(EXTRACTED_MARKER, true);
        }
    }

    public static boolean isExcludedId(ResourceLocation id) {
        return id != null && (exclusionItems.contains(id)
                || exclusionNamespaces.contains(id.getNamespace()));
    }

    /** One matched library entry and why it matched, so a purge can explain itself before acting. */
    public record ExcludedEntry(BookKey key, Reason reason) {}

    /**
     * Library entries that the current exclusion rules would no longer allow to be absorbed — the
     * recovery path for a save that absorbed a functional book before it was hard-excluded.
     *
     * <p>Two signals are checked. The entry's {@code bookId} is meaningful for item-identity systems
     * (heuristic, tagged, config, datapack, {@code ItemBasedAdapter}) where it <em>is</em> the item
     * id. Otherwise the retained source stack is used, evaluated through the full {@link #evaluate}
     * gate so the {@code #runictome:absorb_blocklist} tag counts too — a {@code runictome:patchouli}
     * entry's {@code bookId} is a <em>book</em> id and can only be matched that way.
     *
     * <p><b>Caution for callers:</b> many entries can share one retained item. Every generic
     * Patchouli book retains a {@code patchouli:guide_book} stack, so excluding that single item
     * matches a player's entire Patchouli library. Always show this list before removing anything.
     */
    public static List<ExcludedEntry> findExcluded(IRunicTomeData data) {
        List<ExcludedEntry> result = new ArrayList<>();
        for (BookKey key : data.getBooks()) {
            if (exclusionItems.contains(key.bookId())) {
                result.add(new ExcludedEntry(key, Reason.GLOBAL_ITEM));
                continue;
            }
            if (exclusionNamespaces.contains(key.bookId().getNamespace())) {
                result.add(new ExcludedEntry(key, Reason.GLOBAL_NAMESPACE));
                continue;
            }
            ItemStack retained = data.getBookStack(key);
            if (retained.isEmpty()) continue;
            Decision decision = evaluate(retained);
            // EXTRACTED / VIRTUAL are never purge-worthy: a retained stack cannot legitimately carry
            // either marker, and treating them as exclusions would delete entries for the wrong reason.
            if (decision.excluded() && decision.reason() != Reason.EXTRACTED
                    && decision.reason() != Reason.VIRTUAL) {
                result.add(new ExcludedEntry(key, decision.reason()));
            }
        }
        return result;
    }

    /**
     * Removes every entry reported by {@link #findExcluded}. The list is materialized first, so the
     * removals never mutate a collection under iteration.
     *
     * @return the number of entries removed.
     */
    public static int purgeExcluded(IRunicTomeData data) {
        return purge(data, findExcluded(data));
    }

    /**
     * Removes a previously-computed set of entries. Preferred over {@link #purgeExcluded} when the
     * caller already ran {@link #findExcluded} (for a confirmation prompt), so the scan — which
     * copies each retained stack — is not repeated, and so the player confirms exactly what is
     * removed rather than a freshly-recomputed set.
     */
    public static int purge(IRunicTomeData data, List<ExcludedEntry> entries) {
        int removed = 0;
        for (ExcludedEntry entry : entries) {
            if (data.lockBook(entry.key())) removed++;
        }
        return removed;
    }

    public static void rebuild(Collection<? extends String> items,
                               Collection<? extends String> namespaces) {
        // Always retain the built-in safety set. Forge does not rewrite an existing TOML when a
        // later release gains new defaults, so replacing these sets with the config values would
        // leave upgraded modpacks vulnerable to newly-discovered functional books.
        Set<ResourceLocation> rebuiltItems = new HashSet<>(
                parseItems(RunicTomeConfig.DEFAULT_ABSORB_EXCLUSION_ITEMS));
        rebuiltItems.addAll(parseItems(items));
        exclusionItems = Set.copyOf(rebuiltItems);

        Set<String> rebuiltNamespaces = new HashSet<>(
                parseNamespaces(RunicTomeConfig.DEFAULT_ABSORB_EXCLUSION_MODS));
        rebuiltNamespaces.addAll(parseNamespaces(namespaces));
        exclusionNamespaces = Set.copyOf(rebuiltNamespaces);
        RunicTome.LOGGER.info(
                "Runic Tome: absorption policy active ({} item(s) + {} namespace(s) globally excluded)",
                exclusionItems.size(), exclusionNamespaces.size());
    }

    public static void rebuildFromConfig() {
        if (RunicTomeConfig.COMMON_SPEC.isLoaded()) {
            rebuild(RunicTomeConfig.COMMON.absorbExclusionItems.get(),
                    RunicTomeConfig.COMMON.absorbExclusionMods.get());
        } else {
            rebuild(RunicTomeConfig.DEFAULT_ABSORB_EXCLUSION_ITEMS,
                    RunicTomeConfig.DEFAULT_ABSORB_EXCLUSION_MODS);
        }
    }

    private static Set<ResourceLocation> parseItems(Collection<? extends String> values) {
        Set<ResourceLocation> parsed = new HashSet<>();
        if (values != null) {
            for (String raw : values) {
                ResourceLocation id = raw == null ? null : ResourceLocation.tryParse(raw);
                if (id == null) {
                    RunicTome.LOGGER.warn("absorbExclusionItems: invalid item id '{}', skipping", raw);
                } else {
                    parsed.add(id);
                }
            }
        }
        return Set.copyOf(parsed);
    }

    private static Set<String> parseNamespaces(Collection<? extends String> values) {
        Set<String> parsed = new HashSet<>();
        if (values != null) {
            for (String raw : values) {
                if (raw == null || raw.isBlank() || ResourceLocation.tryBuild(raw, "check") == null) {
                    RunicTome.LOGGER.warn("absorbExclusionMods: invalid namespace '{}', skipping", raw);
                } else {
                    parsed.add(raw);
                }
            }
        }
        return Set.copyOf(parsed);
    }
}
