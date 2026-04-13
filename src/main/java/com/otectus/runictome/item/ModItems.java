package com.otectus.runictome.item;

import com.otectus.runictome.RunicTome;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, RunicTome.MOD_ID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, RunicTome.MOD_ID);

    public static final RegistryObject<Item> RUNIC_TOME =
            ITEMS.register("runic_tome", RunicTomeItem::new);

    public static final RegistryObject<CreativeModeTab> TAB = CREATIVE_TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(net.minecraft.network.chat.Component.translatable("itemGroup.runictome"))
                    .icon(() -> RUNIC_TOME.get().getDefaultInstance())
                    .displayItems((params, output) -> output.accept(RUNIC_TOME.get()))
                    .build());

    private ModItems() {}

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        CREATIVE_TABS.register(bus);
    }

    @Mod.EventBusSubscriber(modid = RunicTome.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class TabInjection {
        @SubscribeEvent
        public static void addCreative(BuildCreativeModeTabContentsEvent event) {
            if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
                event.accept(RUNIC_TOME);
            }
        }
    }
}
