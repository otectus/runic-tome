package com.otectus.runictome.integration.curios;

import com.otectus.runictome.RunicTome;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

/**
 * The class-load boundary in front of every Curios-typed class in this mod.
 *
 * <p><b>This file must never import anything from {@code top.theillusivec4.curios}.</b> Curios is an
 * optional dependency: its classes are absent at runtime for most players, and a class that names a
 * missing type fails verification the moment it is loaded. Because Java resolves the reference to
 * {@link CuriosIntegration} lazily — at first execution of the branch, not when this class loads —
 * gating every call on {@link #AVAILABLE} keeps {@code CuriosIntegration} from ever being loaded
 * when Curios is not installed.
 *
 * <p>The same reasoning forbids a Curios import in {@code RunicTomeItem}, {@code ModItems},
 * {@code KeyBindings} or any event handler: those load unconditionally on every startup. In
 * particular the tome must <em>not</em> implement {@code ICurioItem} directly — the behaviour is
 * attached from {@link CuriosIntegration} via {@code CuriosApi.registerCurio} instead.
 *
 * <p>This is the discipline {@code PatchouliIntegration.setup()} already follows, tightened: the
 * Patchouli adapter is pure reflection and therefore always safe to load, whereas these classes hold
 * real Curios types in their signatures.
 */
public final class CuriosSupport {

    /** Slot identifier. Curios slot ids are global and merge across mods, so this stays specific. */
    public static final String SLOT = "runic_tome";

    private static final boolean AVAILABLE = detect();

    private CuriosSupport() {}

    /**
     * {@code ModList.get()} is only meaningful inside a running Forge environment — it is null or
     * throws in a plain unit-test JVM — so the probe fails closed and is resolved exactly once.
     */
    private static boolean detect() {
        try {
            ModList list = ModList.get();
            return list != null && list.isLoaded("curios");
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    /** Registers the tome's curio behaviour. Called from {@code ModIntegrations.setupAll()}. */
    public static void setup() {
        if (!AVAILABLE) {
            RunicTome.LOGGER.debug("Curios not loaded — skipping Runic Tome curio slot registration");
            return;
        }
        try {
            CuriosIntegration.register();
            RunicTome.LOGGER.info("Runic Tome: Curios detected, '{}' slot behaviour registered", SLOT);
        } catch (Throwable t) {
            RunicTome.LOGGER.error("Runic Tome: failed to register Curios integration", t);
        }
    }

    /**
     * Whether the player is wearing the tome in its curio slot.
     *
     * <p>Load-bearing for the open-library keybinding: a tome in a curio slot is in neither hand nor
     * {@code inv.items}, so without this the key silently stops working the moment a player equips
     * it.
     */
    public static boolean isTomeEquipped(Player player) {
        if (!AVAILABLE || player == null) return false;
        try {
            return CuriosIntegration.isTomeEquipped(player);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Tries to place {@code tome} in the player's empty curio slot.
     *
     * @return true if it was equipped; false means the caller should fall back to the inventory.
     */
    public static boolean tryEquip(ServerPlayer player, ItemStack tome) {
        if (!AVAILABLE || player == null || tome == null || tome.isEmpty()) return false;
        try {
            return CuriosIntegration.tryEquip(player, tome);
        } catch (Throwable t) {
            RunicTome.LOGGER.warn("Runic Tome: could not equip tome into the curio slot", t);
            return false;
        }
    }
}
