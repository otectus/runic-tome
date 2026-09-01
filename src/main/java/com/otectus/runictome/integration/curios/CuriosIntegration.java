package com.otectus.runictome.integration.curios;

import com.otectus.runictome.item.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurio;
import top.theillusivec4.curios.api.type.capability.ICurioItem;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import javax.annotation.Nonnull;

/**
 * The only class in this mod that names a Curios type.
 *
 * <p>Reached exclusively through {@link CuriosSupport}, which checks {@code ModList} first. Nothing
 * here may be referenced from a class that loads unconditionally — see the note on
 * {@link CuriosSupport} for why.
 *
 * <p>The slot itself is declared entirely in data ({@code data/runictome/curios/slots/runic_tome.json}
 * plus the entity binding and the {@code curios:runic_tome} item tag). This class only supplies the
 * behaviour that data cannot express.
 */
final class CuriosIntegration {

    private CuriosIntegration() {}

    /**
     * Attaches the curio behaviour to the tome item.
     *
     * <p>{@code registerCurio} rather than {@code RunicTomeItem implements ICurioItem}: the item
     * class is loaded during registration on every startup, and implementing a missing interface
     * would fail class linking on any install without Curios.
     */
    static void register() {
        CuriosApi.registerCurio(ModItems.RUNIC_TOME.get(), new RunicTomeCurio());
    }

    static boolean isTomeEquipped(Player player) {
        // resolve() rather than LazyOptional.map so the result is an unambiguous Optional.
        return CuriosApi.getCuriosInventory(player)
                .resolve()
                .map(inventory -> inventory.isEquipped(ModItems.RUNIC_TOME.get()))
                .orElse(false);
    }

    /**
     * Places the tome in the curio slot if that slot exists and is empty.
     *
     * <p>No {@code onEquip} call is needed: Curios' own living-tick handler diffs each slot against
     * its previous-stack shadow copy on the server and drives the whole equip lifecycle — attribute
     * modifiers, the change event, and the client sync packet — from that.
     */
    static boolean tryEquip(ServerPlayer player, ItemStack tome) {
        return CuriosApi.getCuriosInventory(player)
                .resolve()
                .flatMap(inventory -> inventory.getStacksHandler(CuriosSupport.SLOT))
                .map(handler -> {
                    IDynamicStackHandler stacks = handler.getStacks();
                    if (stacks.getSlots() < 1 || !stacks.getStackInSlot(0).isEmpty()) return false;
                    stacks.setStackInSlot(0, tome.copy());
                    return true;
                })
                .orElse(false);
    }

    /**
     * Keeps the tome in its slot through death.
     *
     * <p>Belt-and-braces over the slot's own {@code "drop_rule": "ALWAYS_KEEP"}: the slot rule is
     * only consulted when the item-level rule is {@code DEFAULT}, so declaring it here too means the
     * tome survives even if the slot definition is overridden by a datapack. {@code ALWAYS_KEEP} is
     * the only rule that leaves the stack in place — every other outcome clears the slot, including
     * the destroy path — and it bypasses both the {@code keepInventory} gamerule and Curios' own
     * {@code keepCurios} config.
     *
     * <p>This also means an equipped tome never reaches {@code LivingDropsEvent}, so
     * {@code SoulboundHandler}'s stash-and-reissue path is simply never entered for it. A tome
     * carried in the inventory still goes through that path exactly as before.
     */
    private static final class RunicTomeCurio implements ICurioItem {

        @Nonnull
        @Override
        public ICurio.DropRule getDropRule(SlotContext slotContext, DamageSource source,
                                           int lootingLevel, boolean recentlyHit, ItemStack stack) {
            return ICurio.DropRule.ALWAYS_KEEP;
        }
    }
}
