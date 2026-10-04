package com.dbzenith.registry;

import com.dbzenith.DBZenith;
import com.dbzenith.item.BossSummonItem;
import com.dbzenith.item.CapsuleItem;
import com.dbzenith.item.GiArmorItem;
import com.dbzenith.item.MoonOrbItem;
import com.dbzenith.item.ScouterItem;
import com.dbzenith.item.SenzuBeanItem;
import com.dbzenith.item.TechniqueScrollItem;
import com.dbzenith.item.TrainingWeightsItem;
import net.minecraft.world.item.ArmorItem;
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

    public static final RegistryObject<Item> SCOUTER = ITEMS.register("scouter",
            () -> new ScouterItem(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> CAPSULE = ITEMS.register("capsule",
            () -> new CapsuleItem(new Item.Properties().stacksTo(1)));
    public static final java.util.List<RegistryObject<Item>> GI = gi();

    private static java.util.List<RegistryObject<Item>> gi() {
        java.util.List<RegistryObject<Item>> out = new java.util.ArrayList<>();
        for (GiArmorItem.Set set : GiArmorItem.Set.values()) {
            out.add(ITEMS.register(set.id() + "_top", () -> new GiArmorItem(set, ArmorItem.Type.CHESTPLATE, new Item.Properties())));
            out.add(ITEMS.register(set.id() + "_pants", () -> new GiArmorItem(set, ArmorItem.Type.LEGGINGS, new Item.Properties())));
            out.add(ITEMS.register(set.id() + "_boots", () -> new GiArmorItem(set, ArmorItem.Type.BOOTS, new Item.Properties())));
        }
        return out;
    }

    public static final RegistryObject<Item> SPROUTLING_EGG = ITEMS.register("sproutling_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(com.dbzenith.npc.ModNpcs.SPROUTLING, 0x4CA03A, 0x203818, new Item.Properties()));
    public static final RegistryObject<Item> KI_SOLDIER_EGG = ITEMS.register("ki_soldier_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(com.dbzenith.npc.ModNpcs.KI_SOLDIER, 0xE8E8E8, 0x3A3A4A, new Item.Properties()));
    public static final RegistryObject<Item> ANDROID_UNIT_EGG = ITEMS.register("android_unit_spawn_egg",
            () -> new net.minecraftforge.common.ForgeSpawnEggItem(com.dbzenith.npc.ModNpcs.ANDROID_UNIT, 0x808890, 0xC02020, new Item.Properties()));
    public static final RegistryObject<Item> TYRANT_SIGIL = ITEMS.register("tyrant_sigil",
            () -> new BossSummonItem(com.dbzenith.npc.ModNpcs.TYRANT_LORD, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> RAGE_TOTEM = ITEMS.register("rage_totem",
            () -> new BossSummonItem(com.dbzenith.npc.ModNpcs.RAMPAGE_BRUTE, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));

    public static final RegistryObject<Item> MOON_ORB = ITEMS.register("moon_orb",
            () -> new MoonOrbItem(new Item.Properties().stacksTo(4).rarity(Rarity.RARE)));

    private ModItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
