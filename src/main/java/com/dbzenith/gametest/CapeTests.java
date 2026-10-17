package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.item.CapeItem;
import com.dbzenith.network.PublicStatePacket;
import com.dbzenith.registry.ModItems;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CapeTests {
    private static ItemStack cape(String name) {
        var stack = new ItemStack(ModItems.PICCOLO_CAPE.get()); stack.setHoverName(Component.literal(name)); return stack;
    }
    @GameTest(template="empty", batch="wardrobe")
    public static void independentSlotAndAtomicExchange(GameTestHelper h) {
        var p=TestPlayers.create(h); var data=ModCapabilities.getOrThrow(p);
        var armor=new ItemStack(Items.DIAMOND_CHESTPLATE); armor.setDamageValue(17);
        p.setItemSlot(EquipmentSlot.CHEST,armor);
        p.setItemInHand(InteractionHand.MAIN_HAND,cape("First"));
        h.assertTrue(CapeItem.equip(p,InteractionHand.MAIN_HAND),"equip actual accessory");
        h.assertTrue(p.getMainHandItem().isEmpty() && data.hasCapeAccessory(),"exactly one item moved into slot");
        h.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST)==armor && armor.getDamageValue()==17,"chest armor untouched");
        p.setItemInHand(InteractionHand.OFF_HAND,cape("Second"));
        h.assertTrue(CapeItem.equip(p,InteractionHand.OFF_HAND),"offhand swap");
        h.assertTrue(data.capeAccessory().getHoverName().getString().equals("Second")
                && p.getOffhandItem().getHoverName().getString().equals("First"),"swap preserves both item tags");
        h.assertTrue(CapeItem.remove(p) && !CapeItem.remove(p),"repeated remove cannot duplicate");
        h.assertTrue(p.getInventory().countItem(ModItems.PICCOLO_CAPE.get())==2,"both capes returned exactly once");
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));
        h.assertTrue(!CapeItem.equip(p,InteractionHand.MAIN_HAND) && !data.hasCapeAccessory(),"ordinary item cannot occupy back slot");
        for (int i=0;i<p.getInventory().items.size();i++) p.getInventory().items.set(i,new ItemStack(Items.STONE,64));
        data.setCapeAccessory(cape("Overflow cloak"));
        h.assertTrue(CapeItem.remove(p) && !CapeItem.remove(p),"full inventory removal clears slot once");
        var drops=p.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,p.getBoundingBox().inflate(3),
                e -> e.getItem().is(ModItems.PICCOLO_CAPE.get()) && e.getItem().getHoverName().getString().equals("Overflow cloak"));
        h.assertTrue(drops.size()==1 && drops.get(0).getItem().getCount()==1,"full inventory drops exactly one tagged cape");
        drops.forEach(net.minecraft.world.entity.Entity::discard);
        TestPlayers.remove(h,p); h.succeed();
    }
    @GameTest(template="empty", batch="wardrobe")
    public static void capeSurvivesSaveDeathAndSync(GameTestHelper h) {
        var p=TestPlayers.create(h); var data=ModCapabilities.getOrThrow(p);
        data.setCapeAccessory(cape("Travel cloak"));
        var copy=new PlayerData(); copy.load(data.save());
        h.assertTrue(copy.capeAccessory().getHoverName().getString().equals("Travel cloak"),"NBT persists");
        var leaked=copy.capeAccessory(); leaked.shrink(1);
        h.assertTrue(copy.hasCapeAccessory(),"callers cannot mutate slot by alias");
        var buf=new FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            PublicStatePacket.encode(PublicStatePacket.of(p,data),buf);
            h.assertTrue(PublicStatePacket.decode(buf).has(PublicStatePacket.CAPE),"trackers receive accessory independent of chest equipment");
        } finally { buf.release(); }
        var respawned=h.getLevel().getServer().getPlayerList().respawn(p,false);
        var after=ModCapabilities.getOrThrow(respawned);
        h.assertTrue(after.hasCapeAccessory() && after.capeAccessory().getHoverName().getString().equals("Travel cloak"),"real death clone retains cosmetic slot");
        after.setCapeAccessory(new ItemStack(Items.DIAMOND));
        h.assertTrue(!after.hasCapeAccessory(),"invalid saved slot item rejected");
        TestPlayers.remove(h,respawned); h.succeed();
    }
    @GameTest(template="empty", batch="wardrobe")
    public static void legacyPiccoloCapeExtractedOnce(GameTestHelper h) {
        var p=TestPlayers.create(h); var data=ModCapabilities.getOrThrow(p);
        var old=new CompoundTag(); old.putInt("DataVersion",2); data.load(old);
        var top=new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(DBZenith.MOD_ID,"namekian_top")));
        top.setDamageValue(11); p.setItemSlot(EquipmentSlot.CHEST,top);
        CapeItem.migrate(p);
        h.assertTrue(data.hasCapeAccessory() && data.capeMigrationDone(),"legacy wearer receives detached cape");
        h.assertTrue(p.getItemBySlot(EquipmentSlot.CHEST)==top && top.getDamageValue()==11,"original top retained unchanged");
        CapeItem.remove(p); CapeItem.migrate(p);
        h.assertTrue(!data.hasCapeAccessory() && p.getInventory().countItem(ModItems.PICCOLO_CAPE.get())==1,"never grant again after removal");
        var restored=new PlayerData(); restored.load(data.save());
        h.assertTrue(restored.capeMigrationDone(),"migration completion persists");
        var fresh=new PlayerData(); h.assertTrue(fresh.capeMigrationDone(),"new characters do not receive free capes");
        h.assertTrue(h.getLevel().getRecipeManager().byKey(new ResourceLocation(DBZenith.MOD_ID,"piccolo_cape")).isPresent(),"separate crafting recipe");
        TestPlayers.remove(h,p);h.succeed();
    }
}
