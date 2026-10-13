package com.dbzenith.nimbus;

import com.dbzenith.DBZenith;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.*;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;

/** Self-contained registration avoids replacing newer baseline registry classes. */
@Mod.EventBusSubscriber(modid=DBZenith.MOD_ID,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class NimbusContent {
    public static EntityType<NimbusEntity> TYPE;
    public static Item ITEM;
    @SubscribeEvent public static void register(RegisterEvent e) {
        e.register(ForgeRegistries.Keys.ENTITY_TYPES,h->{
            TYPE=EntityType.Builder.<NimbusEntity>of(NimbusEntity::new,MobCategory.MISC)
                .sized(1.65f,.65f).clientTrackingRange(12).updateInterval(1).fireImmune().build("dbzenith:flying_nimbus");
            h.register(new ResourceLocation(DBZenith.MOD_ID,"flying_nimbus"),TYPE);
        });
        e.register(ForgeRegistries.Keys.ITEMS,h->{
            ITEM=new NimbusItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE));
            h.register(new ResourceLocation(DBZenith.MOD_ID,"flying_nimbus"),ITEM);
        });
    }
    @SubscribeEvent public static void setup(FMLCommonSetupEvent e) { e.enqueueWork(NimbusNetwork::register); }
    @SubscribeEvent public static void creative(BuildCreativeModeTabContentsEvent e) {
        if(e.getTabKey()==CreativeModeTabs.TOOLS_AND_UTILITIES) e.accept(ITEM);
    }
}
