package com.otectus.runictome.integration.datapack;

import net.minecraft.resources.ResourceLocation;

/**
 * A datapack-defined guide book: an item that should be treated as a single-item book.
 *
 * @param systemId adapter system id (assigned by the loader under {@code runictome:datapack/...})
 * @param itemId   the book item's registry id
 * @param name     optional display name; {@code null}/blank falls back to the item's own name
 */
public record BookDef(ResourceLocation systemId, ResourceLocation itemId, String name) {
}
