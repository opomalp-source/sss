package com.dbzenith.registry;

import com.dbzenith.DBZenith;
import com.dbzenith.item.MoonOrbItem;
import com.dbzenith.item.SenzuBeanItem;
import com.dbzenith.item.TechniqueScrollItem;
import com.dbzenith.item.TrainingWeightsItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, DBZenith.MOD_ID);

    public static final RegistryObject<Item> SENZU_BEAN = ITEMS.register("senzu_bean",
            () -> new SenzuBeanItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));

    public static final RegistryObject<Item> TECHNIQUE_SCROLL = ITEMS.register("technique_scroll",
            () -> new TechniqueScrollItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    public static final RegistryObject<Item> TRAINING_WEIGHTS = ITEMS.register("training_weights",
            () -> new TrainingWeightsItem(1.5, 0.1, new Item.Properties()));
    public static final RegistryObject<Item> HEAVY_TRAINING_WEIGHTS = ITEMS.register("heavy_training_weights",
            () -> new TrainingWeightsItem(2.5, 0.25, new Item.Properties().rarity(Rarity.UNCOMMON)));

    public static final RegistryObject<Item> DRAGON_RADAR = ITEMS.register("dragon_radar",
            () -> new Item(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    public static final RegistryObject<Item> MOON_ORB = ITEMS.register("moon_orb",
            () -> new MoonOrbItem(new Item.Properties().stacksTo(4).rarity(Rarity.RARE)));

    private ModItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
