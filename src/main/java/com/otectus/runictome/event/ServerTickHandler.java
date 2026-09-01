package com.otectus.runictome.event;

import com.otectus.runictome.RunicTome;
import com.otectus.runictome.RunicTomeConfig;
import com.otectus.runictome.api.BookKey;
import com.otectus.runictome.api.RunicTomeAPI;
import com.otectus.runictome.api.UnlockResult;
import com.otectus.runictome.impl.AbsorptionPolicy;
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
        int tick = event.getServer().getTickCount();
        for (ServerPlayer sp : event.getServer().getPlayerList().getPlayers()) {
            if (isDueThisTick(tick, sp.getId(), interval)) {
                sweep(sp);
            }
        }
    }

    /**
     * Spreads the sweep across the interval instead of running every online player on the same tick.
     * Each player is still swept exactly once per interval; only the tick it lands on differs, so a
     * 200-player server does 200/interval sweeps per tick rather than 200 on one tick and none on
     * the rest. At {@code interval == 1} every player is due every tick, as before.
     *
     * <p>Package-private and pure so the distribution can be unit-tested without a server.
     */
    static boolean isDueThisTick(int tick, int entityId, int interval) {
        if (interval <= 1) return true;
        // Math.floorMod: entity ids are non-negative in practice, but '%' on a negative id would
        // yield a negative offset and skew the distribution.
        return Math.floorMod(tick - entityId, interval) == 0;
    }

    @SubscribeEvent
    public static void onContainerClose(PlayerContainerEvent.Close event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            sweep(sp);
        }
    }

    public static void sweep(ServerPlayer sp) {
        // Gated here rather than per-slot so the pause also covers the container-close sweep above,
        // and so a paused player costs nothing to skip.
        if (RunicTomeAPI.isAbsorptionPaused(sp)) return;
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
            UnlockResult result = RunicTomeAPI.unlockBook(sp, key, stack);
            // Leave the item in place if it could not be stored; a later sweep retries.
            int consumed = AbsorptionPolicy.consumptionFor(result, stack.getCount());
            if (consumed <= 0) continue;
            if (consumed >= stack.getCount()) {
                slots.set(i, ItemStack.EMPTY);
            } else {
                stack.shrink(consumed);
            }
            changed = true;
            if (RunicTomeConfig.COMMON.verboseLogging.get()) {
                RunicTome.LOGGER.info("Swept {} from {}", key, sp.getName().getString());
            }
        }
        return changed;
    }
}
