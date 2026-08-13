package com.otectus.runictome.integration;

import com.otectus.runictome.RunicTome;
import com.otectus.runictome.RunicTomeConfig;
import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.api.GuideSystemAdapter;
import com.otectus.runictome.impl.ItemRefs;
import com.otectus.runictome.impl.UseSimulator;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Generic catch-all: treats any item whose registry path contains a documentation
 * keyword as a guide book, unless it is excluded. Registered last so explicit
 * adapters (Patchouli, Tinkers, config, IMC, mod-specific) always take priority.
 *
 * <p>Exclusions: {@link BlockItem}s (bookshelves and the like), this mod's own items
 * (the Runic Tome's path contains "tome"), the configurable blocklist (vanilla and
 * functional books), the {@code #runictome:absorb_blocklist} tag, synthetic stacks
 * produced by {@link UseSimulator}, and items that {@link #looksFunctional} (spell tomes,
 * caster foci, rune scrolls, wands, and damageable/enchanted gear).
 */
public class HeuristicBookAdapter implements GuideSystemAdapter {

    /** Path substrings that mark an item as functional gear rather than documentation. */
    private static final List<String> FUNCTIONAL_MARKERS =
            List.of("spell", "scroll", "caster", "focus", "rune", "wand", "skill",
                    "level", "experience", "xp", "summon", "ability", "upgrade",
                    "necromancer");

    /** Cosmetic/vanilla bookkeeping that does not normally distinguish a functional item. */
    private static final Set<String> BENIGN_NBT_KEYS =
            Set.of("Damage", "RepairCost", "HideFlags", "display", UseSimulator.VIRTUAL_TAG);

    private final ResourceLocation systemId;
    private final List<String> keywords;
    private final Set<ResourceLocation> blocklist;
    private final Set<String> blockedNamespaces;

    public HeuristicBookAdapter(ResourceLocation systemId, List<String> keywords,
                                Set<ResourceLocation> blocklist, Set<String> blockedNamespaces) {
        this.systemId = systemId;
        this.keywords = keywords;
        this.blocklist = blocklist;
        this.blockedNamespaces = blockedNamespaces;
    }

    @Override
    public ResourceLocation systemId() {
        return systemId;
    }

    @Override
    public int priority() {
        // Lowest precedence: only matches when no concrete adapter claimed the stack.
        return 0;
    }

    @Override
    public Optional<BookKey> identify(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        if (UseSimulator.isVirtual(stack)) return Optional.empty();
        Item item = stack.getItem();
        if (item instanceof BlockItem) return Optional.empty();
        // Hard opt-out: a pack/datapack can blocklist any item by tag (overrides everything below).
        if (stack.is(ModTags.Items.ABSORB_BLOCKLIST)) {
            logSkip(stack, "absorb_blocklist tag");
            return Optional.empty();
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id == null) return Optional.empty();
        if (RunicTome.MOD_ID.equals(id.getNamespace())) return Optional.empty();
        return classify(id, stack);
    }

    /**
     * Core string-based classification, split out from {@link #identify} so it can be unit-tested
     * with fabricated modded ids without registering items or loading tag bindings. Applies the
     * namespace blocklist, item blocklist, and the {@link #looksFunctional} safety net before the
     * keyword loop.
     */
    public Optional<BookKey> classify(ResourceLocation id, ItemStack stack) {
        if (blockedNamespaces.contains(id.getNamespace())) {
            logSkip(stack, "blocked namespace " + id.getNamespace());
            return Optional.empty();
        }
        if (blocklist.contains(id)) {
            logSkip(stack, "item blocklist");
            return Optional.empty();
        }
        if (looksFunctional(id, stack)) {
            logSkip(stack, "looksFunctional");
            return Optional.empty();
        }
        String path = id.getPath().toLowerCase(Locale.ROOT);
        for (String kw : keywords) {
            if (!kw.isEmpty() && path.contains(kw)) {
                if (verbose()) {
                    RunicTome.LOGGER.info("Heuristic matched {} on keyword '{}'", id, kw);
                }
                return Optional.of(new BookKey(systemId, id));
            }
        }
        return Optional.empty();
    }

    /**
     * Distinguishes functional gear that happens to be named like a book (spell tomes, caster foci,
     * rune scrolls, wands) from real documentation. A damageable or enchanted stack is also treated
     * as functional. A legitimately-named guide that trips one of these signals can be re-included by
     * tagging it into {@code #runictome:guide_books}.
     */
    private boolean looksFunctional(ResourceLocation id, ItemStack stack) {
        String path = id.getPath().toLowerCase(Locale.ROOT);
        for (String marker : FUNCTIONAL_MARKERS) {
            if (path.contains(marker)) return true;
        }
        return stack.isDamageableItem() || stack.isEnchanted() || hasMeaningfulNbt(stack);
    }

    private static boolean hasMeaningfulNbt(ItemStack stack) {
        var tag = stack.getTag();
        if (tag == null || tag.isEmpty()) return false;
        for (String key : tag.getAllKeys()) {
            if (!BENIGN_NBT_KEYS.contains(key)) return true;
        }
        return false;
    }

    /**
     * First keyword whose substring matches the item's path, ignoring blocklists and the functional
     * check. Used by {@code /runictome debug identify} to explain a match.
     */
    public Optional<String> matchedKeyword(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return Optional.empty();
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null) return Optional.empty();
        String path = id.getPath().toLowerCase(Locale.ROOT);
        for (String kw : keywords) {
            if (!kw.isEmpty() && path.contains(kw)) return Optional.of(kw);
        }
        return Optional.empty();
    }

    private static void logSkip(ItemStack stack, String reason) {
        if (verbose()) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            RunicTome.LOGGER.info("Heuristic skipped {} ({})", id, reason);
        }
    }

    /** Verbose-logging gate that tolerates an unloaded config (e.g. in unit tests). */
    private static boolean verbose() {
        return RunicTomeConfig.COMMON_SPEC.isLoaded() && RunicTomeConfig.COMMON.verboseLogging.get();
    }

    @Override
    public void open(BookKey key, Player clientPlayer) {
        open(key, clientPlayer, ItemStack.EMPTY);
    }

    @Override
    public void open(BookKey key, Player clientPlayer, ItemStack sourceStack) {
        if (!clientPlayer.level().isClientSide) return;
        ItemStack opener = ItemRefs.openerFor(key.bookId(), sourceStack);
        if (opener.isEmpty()) {
            clientPlayer.displayClientMessage(
                    Component.translatable("runictome.unknown_item", key.bookId().toString()), false);
            return;
        }
        UseSimulator.simulateClientUse(opener, clientPlayer);
    }

    @Override
    public void openServer(BookKey key, net.minecraft.server.level.ServerPlayer serverPlayer,
                           ItemStack sourceStack) {
        ItemStack opener = ItemRefs.openerFor(key.bookId(), sourceStack);
        if (opener.isEmpty()) return;
        UseSimulator.simulateServerUse(opener, serverPlayer);
    }

    @Override
    public Component displayName(BookKey key) {
        Item item = ItemRefs.resolve(key.bookId());
        return item == null ? Component.literal(key.bookId().toString()) : item.getDescription().copy();
    }

    @Override
    public ItemStack displayIcon(BookKey key) {
        return ItemRefs.stackOf(key.bookId());
    }
}
