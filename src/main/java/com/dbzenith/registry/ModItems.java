package com.dbzenith.registry;

import com.dbzenith.DBZenith;
import com.dbzenith.item.SenzuBeanItem;
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

    private ModItems() {}

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
