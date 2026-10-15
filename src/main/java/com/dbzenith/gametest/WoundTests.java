package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.Limbs;
import com.dbzenith.combat.Limbs.Limb;
import com.dbzenith.combat.Scars;
import com.dbzenith.npc.KiFighter;
import com.dbzenith.npc.ModNpcs;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** The limbs rework: crawling on a lost leg, wounds that stay open until closed, the left hand taking over, scars. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public class WoundTests {
    @GameTest(template = "empty", batch = "wounds", timeoutTicks = 80)
    public static void aLostLegMeansCrawling(GameTestHelper h) {
        KiFighter foe = h.spawnWithNoFreeWill(ModNpcs.KI_SOLDIER.get(), new BlockPos(1, 1, 1));
        double before = foe.getAttributeValue(Attributes.MOVEMENT_SPEED);
        Limbs.sever(foe, Limb.RIGHT_LEG);
        h.assertTrue(Limbs.of(foe).mustCrawl(), "one leg gone: crawling");
        h.succeedWhen(() -> {
            double after = foe.getAttributeValue(Attributes.MOVEMENT_SPEED);
            h.assertTrue(after < before * 0.3, "crawling is slow " + before + " -> " + after);
        });
    }

    @GameTest(template = "empty", batch = "wounds", timeoutTicks = 60)
    public static void aProstheticLegStandsYouUp(GameTestHelper h) {
        ServerPlayer p = TestPlayers.create(h);
        p.setGameMode(GameType.SURVIVAL);
        Limbs.sever(p, Limb.LEFT_LEG);
        h.assertTrue(Limbs.of(p).mustCrawl(), "crawling without it");
        h.assertTrue(Limbs.fit(p, false) && !Limbs.of(p).mustCrawl(), "up again on a prosthetic");
        TestPlayers.remove(h, p);
        h.succeed();
    }

    @GameTest(template = "empty", batch = "wounds", timeoutTicks = 60)
    public static void anOpenWoundStaysOpenUntilClosed(GameTestHelper h) {
        ServerPlayer p = TestPlayers.create(h);
        p.setGameMode(GameType.SURVIVAL);
        Limbs.State s = Limbs.of(p);
        Limbs.sever(p, Limb.LEFT_ARM);
        h.assertTrue(s.stump(Limb.LEFT_ARM) == Limbs.OPEN && Limbs.openWound(p), "a human's cut stays open");
        h.assertTrue(Limbs.sear(p) && s.stump(Limb.LEFT_ARM) == Limbs.SEARED && !Limbs.openWound(p), "seared shut with ki");
        Limbs.sever(p, Limb.RIGHT_LEG);
        h.assertTrue(Limbs.stopBleeding(p) && s.stump(Limb.RIGHT_LEG) == Limbs.BANDAGED, "or bandaged");
        Limbs.sever(p, Limb.LEFT_LEG, true);
        h.assertTrue(s.stump(Limb.LEFT_LEG) == Limbs.SEARED, "a ki cut is seared already");
        Limbs.mend(p);
        for (Limb l : new Limb[]{Limb.LEFT_ARM, Limb.RIGHT_LEG, Limb.LEFT_LEG})
            h.assertTrue(s.stump(l) == Limbs.HEALED && !s.bleeding(l), "a Senzu Bean heals the stumps over: " + l);
        TestPlayers.remove(h, p);
        h.succeed();
    }

    @GameTest(template = "empty", batch = "wounds", timeoutTicks = 60)
    public static void theLeftHandTakesOver(GameTestHelper h) {
        ServerPlayer p = TestPlayers.create(h);
        p.setGameMode(GameType.SURVIVAL);
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.BREAD));
        Limbs.sever(p, Limb.RIGHT_ARM);
        h.assertTrue(Limbs.of(p).leftHanded(), "right arm gone, left good");
        h.runAfterDelay(12, () -> {
            h.assertTrue(p.getMainArm() == HumanoidArm.LEFT, "the left is the main arm now");
            h.assertTrue(p.getOffhandItem().isEmpty() && p.getInventory().contains(new ItemStack(Items.BREAD)), "nothing held on the lost side");
            Limbs.regrow(p, Limb.RIGHT_ARM, false);
            h.runAfterDelay(12, () -> {
                h.assertTrue(p.getMainArm() == HumanoidArm.RIGHT, "back to the right when it is back");
                TestPlayers.remove(h, p);
                h.succeed();
            });
        });
    }

    @GameTest(template = "empty", batch = "wounds", timeoutTicks = 60)
    public static void regeneratorsRegrowAndCloseOnTheirOwn(GameTestHelper h) {
        KiFighter namek = h.spawnWithNoFreeWill(ModNpcs.NAMEKIAN_WARRIOR.get(), new BlockPos(3, 1, 3));
        Limbs.State s = Limbs.of(namek);
        Limbs.sever(namek, Limb.RIGHT_ARM);
        Limbs.sever(namek, Limb.LEFT_LEG);
        h.assertTrue(s.severed(Limb.RIGHT_ARM) && s.severed(Limb.LEFT_LEG), "cut");
        Limbs.regrowAll(namek);
        h.assertTrue(s.severed == 0 && s.bleeding == 0 && s.works(Limb.RIGHT_ARM), "a Namekian's regeneration grows them back at once");
        h.succeed();
    }

    @GameTest(template = "empty", batch = "wounds", timeoutTicks = 60)
    public static void hardFightsLeaveScars(GameTestHelper h) {
        ServerPlayer p = TestPlayers.create(h);
        p.setGameMode(GameType.SURVIVAL);
        h.assertTrue(!Scars.earn(p, p.damageSources().generic(), 0.1f), "a light blow leaves no scar");
        h.assertTrue(Scars.earn(p, p.damageSources().generic(), 0.95f), "a terrible one does");
        h.assertTrue(!Scars.earn(p, p.damageSources().generic(), 0.95f), "but not twice in a minute");
        Scars.Scar s = Scars.of(p).get(0);
        h.assertTrue(s.kind() == Scars.SPLIT && s.length() > 0, "a blow splits the skin " + s);
        for (int i = 0; i < Scars.MAX + 4; i++)
            Scars.add(p, new Scars.Scar(Scars.CHEST, 0, 0.5f, 0.5f, 0, 0.3f + i * 0.01f, Scars.CUT, i, 0));
        h.assertTrue(Scars.of(p).size() == Scars.MAX, "only so many: " + Scars.of(p).size());
        Scars.clear(p);
        h.assertTrue(Scars.of(p).isEmpty(), "a wish clears them");
        TestPlayers.remove(h, p);
        h.succeed();
    }
}
