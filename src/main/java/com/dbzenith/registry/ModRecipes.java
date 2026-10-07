package com.dbzenith.registry;

import com.dbzenith.DBZenith;
import com.dbzenith.item.GiPlating;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Recipe types of our own: plating a gi piece with metal (12e). */
public final class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, DBZenith.MOD_ID);

    public static final RegistryObject<RecipeSerializer<GiPlating>> GI_PLATING = SERIALIZERS.register("gi_plating",
            () -> new SimpleCraftingRecipeSerializer<>(GiPlating::new));

    private ModRecipes() {}

    public static void register(IEventBus modBus) {
        SERIALIZERS.register(modBus);
    }
}
