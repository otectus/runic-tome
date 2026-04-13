package com.otectus.runictome.event;

import com.otectus.runictome.RunicTome;
import com.otectus.runictome.capability.RunicTomeCapabilities;
import com.otectus.runictome.item.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Iterator;

/**
 * Makes the Runic Tome behave as a soulbound item: any tome in a player's
 * drop list at death is removed and its count is stashed in the player's
 * {@link com.otectus.runictome.api.IRunicTomeData} capability. The stash is
 * drained back into the new player's inventory by
 * {@link CapabilityEvents#onPlayerClone} during respawn.
 */
@Mod.EventBusSubscriber(modid = RunicTome.MOD_ID)
public final class SoulboundHandler {

    private SoulboundHandler() {}

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        Item tome = ModItems.RUNIC_TOME.get();
        int stashed = 0;
        Iterator<ItemEntity> it = event.getDrops().iterator();
        while (it.hasNext()) {
            ItemEntity ie = it.next();
            ItemStack stack = ie.getItem();
            if (stack.is(tome)) {
                stashed += stack.getCount();
                it.remove();
            }
        }
        if (stashed > 0) {
            final int finalStashed = stashed;
            sp.getCapability(RunicTomeCapabilities.PLAYER_DATA)
                    .ifPresent(data -> data.setStashedTomes(data.getStashedTomes() + finalStashed));
        }
    }
}
