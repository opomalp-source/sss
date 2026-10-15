package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.Clothes;
import com.dbzenith.npc.KiFighter;
import com.dbzenith.npc.ModNpcs;
import com.dbzenith.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** The knocked-out rework: clothes torn in battle stay torn until mended; a gi takes the wear and never wears right out. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public class ClothesTests {
    @GameTest(template = "empty", batch = "clothes")
    public static void tornClothesStayTornUntilMended(GameTestHelper h) {
        KiFighter foe = h.spawnWithNoFreeWill(ModNpcs.KI_SOLDIER.get(), new BlockPos(1, 1, 1));
        Clothes.tear(foe, 0.5f);
        h.assertTrue(Clothes.level(Clothes.wear(foe)) >= 1, "a hard blow tears them: " + Clothes.wear(foe));
        foe.setHealth(foe.getMaxHealth());
        h.assertTrue(Clothes.wear(foe) > 0.3f, "healing doesn't mend them");
        h.assertTrue(Clothes.mend(foe, 0.5f) && Clothes.wear(foe) == 0, "a sewing kit does");

        ItemStack top = new ItemStack(ModItems.GI.get(0).get());
        foe.setItemSlot(EquipmentSlot.CHEST, top);
        for (int i = 0; i < 20; i++) Clothes.tear(foe, 0.9f);
        ItemStack worn = foe.getItemBySlot(EquipmentSlot.CHEST);
        h.assertTrue(!worn.isEmpty() && worn.getDamageValue() == worn.getMaxDamage() - 1, "a gi takes the wear and never wears right out: " + worn.getDamageValue());
        h.assertTrue(Clothes.wear(foe) == 0, "the gi took it, not the clothes under it");
        h.assertTrue(Clothes.shown(foe) == 3, "shown in tatters");
        Clothes.mend(foe, 0.5f);
        h.assertTrue(Clothes.shown(foe) < 3, "patched");
        h.succeed();
    }
}
