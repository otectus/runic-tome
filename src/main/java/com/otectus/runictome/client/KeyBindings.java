package com.otectus.runictome.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.otectus.runictome.RunicTome;
import com.otectus.runictome.item.ModItems;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RunicTome.MOD_ID, value = Dist.CLIENT)
public final class KeyBindings {

    public static final String CATEGORY = "key.categories." + RunicTome.MOD_ID;

    public static final KeyMapping OPEN_TOME = new KeyMapping(
            "key." + RunicTome.MOD_ID + ".open_tome",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_R,
            CATEGORY);

    private KeyBindings() {}

    @Mod.EventBusSubscriber(modid = RunicTome.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModBusEvents {
        @SubscribeEvent
        public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
            event.register(OPEN_TOME);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.screen != null) return;
        while (OPEN_TOME.consumeClick()) {
            if (playerHoldsTome(player)) {
                ClientHooks.openRunicTomeScreen();
            }
        }
    }

    private static boolean playerHoldsTome(LocalPlayer player) {
        Inventory inv = player.getInventory();
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        if (main.is(ModItems.RUNIC_TOME.get()) || off.is(ModItems.RUNIC_TOME.get())) return true;
        // Also check the full inventory so the keybinding works even when the
        // tome isn't actively selected — matches the UX of Akashic Tome.
        for (ItemStack s : inv.items) {
            if (s.is(ModItems.RUNIC_TOME.get())) return true;
        }
        return false;
    }
}
