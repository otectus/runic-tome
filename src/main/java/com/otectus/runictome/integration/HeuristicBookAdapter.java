package com.otectus.runictome.integration;

import com.otectus.runictome.RunicTome;
import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.api.GuideSystemAdapter;
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
 * functional books), and synthetic stacks produced by {@link UseSimulator}.
 */
public class HeuristicBookAdapter implements GuideSystemAdapter {

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
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id == null) return Optional.empty();
        if (RunicTome.MOD_ID.equals(id.getNamespace())) return Optional.empty();
        if (blockedNamespaces.contains(id.getNamespace())) return Optional.empty();
        if (blocklist.contains(id)) return Optional.empty();
        String path = id.getPath().toLowerCase(Locale.ROOT);
        for (String kw : keywords) {
            if (!kw.isEmpty() && path.contains(kw)) {
                return Optional.of(new BookKey(systemId, id));
            }
        }
        return Optional.empty();
    }

    @Override
    public void open(BookKey key, Player clientPlayer) {
        if (!clientPlayer.level().isClientSide) return;
        Item item = ForgeRegistries.ITEMS.getValue(key.bookId());
        if (item == null) return;
        UseSimulator.simulateClientUse(new ItemStack(item), clientPlayer);
    }

    @Override
    public Component displayName(BookKey key) {
        Item item = ForgeRegistries.ITEMS.getValue(key.bookId());
        return item == null ? Component.literal(key.bookId().toString()) : item.getDescription().copy();
    }

    @Override
    public ItemStack displayIcon(BookKey key) {
        Item item = ForgeRegistries.ITEMS.getValue(key.bookId());
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }
}
