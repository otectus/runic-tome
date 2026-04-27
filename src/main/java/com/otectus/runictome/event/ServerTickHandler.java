package com.otectus.runictome.event;

import com.otectus.runictome.RunicTome;
import com.otectus.runictome.RunicTomeConfig;
import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.api.RunicTomeAPI;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Optional;

@Mod.EventBusSubscriber(modid = RunicTome.MOD_ID)
public final class ServerTickHandler {

    private ServerTickHandler() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.getServer() == null) return;
        int interval = RunicTomeConfig.COMMON.sweepIntervalTicks.get();
        if (interval <= 0) interval = 20;
        if (event.getServer().getTickCount() % interval != 0) return;
        for (ServerPlayer sp : event.getServer().getPlayerList().getPlayers()) {
            sweep(sp);
        }
    }

    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            sweep(sp);
        }
    }

    public static void sweep(ServerPlayer sp) {
        Inventory inv = sp.getInventory();
        boolean changed = false;
        changed |= scanContainer(sp, inv.items);
        changed |= scanContainer(sp, inv.offhand);
        if (changed) {
            inv.setChanged();
            sp.inventoryMenu.broadcastChanges();
            if (sp.containerMenu != null && sp.containerMenu != sp.inventoryMenu) {
                sp.containerMenu.broadcastChanges();
            }
        }
    }

    private static boolean scanContainer(ServerPlayer sp, java.util.List<ItemStack> slots) {
        boolean changed = false;
        for (int i = 0; i < slots.size(); i++) {
            ItemStack stack = slots.get(i);
            if (stack.isEmpty()) continue;
            Optional<BookKey> maybe = RunicTomeAPI.identify(stack);
            if (maybe.isEmpty()) continue;
            BookKey key = maybe.get();
            RunicTomeAPI.unlockBook(sp, key);
            slots.set(i, ItemStack.EMPTY);
            changed = true;
            if (RunicTomeConfig.COMMON.verboseLogging.get()) {
                RunicTome.LOGGER.info("Swept {} from {}", key, sp.getName().getString());
            }
        }
        return changed;
    }
}
