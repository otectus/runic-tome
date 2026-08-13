package com.otectus.runictome.integration;

import com.otectus.runictome.RunicTome;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * Item tag keys used by the absorption pipeline as override channels.
 *
 * <ul>
 *   <li>{@link Items#GUIDE_BOOKS} — a positive opt-in: an item tagged here is treated as a guide
 *       book by {@link TaggedGuideBookAdapter} even if the keyword heuristic would reject it.</li>
 *   <li>{@link Items#ABSORB_BLOCKLIST} — a global hard opt-out: an item tagged here is never
 *       absorbed by any adapter (it overrides {@code GUIDE_BOOKS}).</li>
 * </ul>
 *
 * <p>The positive tag affects only the tagged adapter. The blocklist is enforced centrally before
 * adapter resolution, including for Patchouli, Tinkers, IMC, and datapack-defined books.
 */
public final class ModTags {

    private ModTags() {}

    public static final class Items {

        private Items() {}

        public static final TagKey<Item> GUIDE_BOOKS =
                ItemTags.create(new ResourceLocation(RunicTome.MOD_ID, "guide_books"));

        public static final TagKey<Item> ABSORB_BLOCKLIST =
                ItemTags.create(new ResourceLocation(RunicTome.MOD_ID, "absorb_blocklist"));
    }
}
