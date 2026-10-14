package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.Limbs;
import com.dbzenith.combat.Limbs.Limb;
import com.dbzenith.npc.KiFighter;
import com.dbzenith.npc.ModNpcs;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** CX-53: limbs take damage, break, are cut off, and cost what they should. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public class LimbsTests {
    @GameTest(template = "empty", batch = "knockout", timeoutTicks = 60)
    public static void limbsBreakAndAreCutOff(GameTestHelper h) {
        ServerPlayer p = TestPlayers.create(h);
        p.setGameMode(GameType.SURVIVAL);
        Limbs.State s = Limbs.of(p);
        h.assertTrue(s.whole() && s.armsOut() == 0 && s.legsOut() == 0, "whole to begin with");
        Limbs.hit(p, Limb.RIGHT_ARM, 40, false, false);
        h.assertTrue(s.health(Limb.RIGHT_ARM) == 60 && s.works(Limb.RIGHT_ARM), "hurt but still working");
        Limbs.hit(p, Limb.RIGHT_ARM, 40, false, false);
        h.assertTrue(s.broken(Limb.RIGHT_ARM) && !s.works(Limb.RIGHT_ARM) && s.armsOut() == 1, "broken: hangs, can't be used");
        h.assertTrue(Limbs.canUseKi(p), "one hand is enough for ki");
        Limbs.hit(p, Limb.RIGHT_ARM, 10, false, false);
        h.assertTrue(!s.severed(Limb.RIGHT_ARM), "an ordinary blow doesn't cut it off");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND));
        Limbs.hit(p, Limb.RIGHT_ARM, 10, true, false);
        h.assertTrue(s.severed(Limb.RIGHT_ARM), "a cutting blow takes the broken arm off");
        h.assertTrue(p.getMainHandItem().isEmpty(), "and what it held falls");
        Limbs.hit(p, Limb.LEFT_ARM, 1000, false, false);
        h.assertTrue(!Limbs.canUseKi(p), "no hands: no ki");
        Limbs.sever(p, Limb.LEFT_LEG);
        h.assertTrue(s.legsOut() == 1, "a leg lost");
        TestPlayers.remove(h, p);
        h.succeed();
    }

    @GameTest(template = "empty", batch = "knockout", timeoutTicks = 60)
    public static void legsOutSlowAFighter(GameTestHelper h) {
        KiFighter foe = h.spawnWithNoFreeWill(ModNpcs.KI_SOLDIER.get(), new BlockPos(1, 1, 1));
        double before = foe.getAttributeValue(Attributes.MOVEMENT_SPEED);
        Limbs.hit(foe, Limb.RIGHT_LEG, 1000, false, false);
        Limbs.hit(foe, Limb.LEFT_LEG, 1000, false, false);
        h.assertTrue(Limbs.of(foe).legsOut() == 2, "both legs broken");
        h.succeedWhen(() -> {
            double after = foe.getAttributeValue(Attributes.MOVEMENT_SPEED);
            h.assertTrue(after < before * 0.3, "both legs out: barely moving " + before + " -> " + after);
        });
    }
}
