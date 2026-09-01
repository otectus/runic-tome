package com.otectus.runictome.recipe;

import com.otectus.runictome.RunicTome;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Recipe serializer registration. Mirrors {@link com.otectus.runictome.item.ModItems}: the register
 * is created here and hooked to the mod event bus from the mod constructor.
 *
 * <p>The serializer alone is not enough to make the recipe exist — a special recipe still needs a
 * JSON stub for the recipe manager to load. See {@code data/runictome/recipes/tome_absorb.json}.
 */
public final class ModRecipes {

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, RunicTome.MOD_ID);

    public static final RegistryObject<RecipeSerializer<TomeAbsorbRecipe>> TOME_ABSORB =
            RECIPE_SERIALIZERS.register("tome_absorb",
                    () -> new SimpleCraftingRecipeSerializer<>(TomeAbsorbRecipe::new));

    private ModRecipes() {}

    public static void register(IEventBus bus) {
        RECIPE_SERIALIZERS.register(bus);
    }
}
