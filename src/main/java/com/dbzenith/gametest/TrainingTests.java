package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.ki.KiTicker;
import com.dbzenith.registry.ModBlocks;
import com.dbzenith.registry.ModItems;
import com.dbzenith.stats.StatCalculator;
import com.dbzenith.world.GravityChamberBlockEntity;
import com.dbzenith.world.TimeChamber;
import com.dbzenith.world.TrainingBlocks;
import com.dbzenith.world.TrainingTicker;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Phase 4 slice 1: gravity, training multiplier, punching bag, meditation, weights, Time Chamber. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TrainingTests {
    private static final String EMPTY = "empty";

    private TrainingTests() {}

    private static ServerPlayer player(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.create(helper);
        p.setGameMode(GameType.SURVIVAL);
        return p;
    }

    @GameTest(template = EMPTY, timeoutTicks = 40)
    public static void gravityChamberAppliesGravityAndStrain(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.GRAVITY_CHAMBER.get());
        GravityChamberBlockEntity be = (GravityChamberBlockEntity) helper.getBlockEntity(pos);
        be.setGravity(100);
        ServerPlayer p = player(helper);
        BlockPos abs = helper.absolutePos(pos);
        p.teleportTo(abs.getX() + 2.5, abs.getY(), abs.getZ() + 0.5);
        PlayerData d = ModCapabilities.getOrThrow(p);
        helper.runAfterDelay(15, () -> {
            long now = helper.getLevel().getGameTime();
            helper.assertTrue(d.getGravity(now) == 100, "player in range should feel 100g, felt " + d.getGravity(now));
            double body = d.getBody();
            for (int i = 0; i < 20; i++) KiTicker.tick(p, d);
            helper.assertTrue(d.getBody() < body, "100g strains a weak body");
            helper.assertTrue(d.getTrainingMultiplier() > 5, "heavy gravity multiplies training: " + d.getTrainingMultiplier());
            helper.assertTrue(StatCalculator.scaleTpGain(d, 10) > 50, "TP gains use the training multiplier");
            TestPlayers.remove(helper, p);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void toleranceGrowsWithStrength(GameTestHelper helper) {
        PlayerData weak = new PlayerData();
        weak.initDefaultsIfNeeded();
        PlayerData strong = new PlayerData();
        strong.initDefaultsIfNeeded();
        strong.setAttribute(com.dbzenith.stats.Attribute.CONSTITUTION, 2000);
        helper.assertTrue(TrainingTicker.tolerance(strong) > TrainingTicker.tolerance(weak) + 30, "CON raises gravity tolerance");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void punchingBagGivesTpWithCooldown(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        PlayerData d = ModCapabilities.getOrThrow(p);
        d.setTrainingMultiplier(10); // e.g. under gravity
        long tp = d.getTrainingPoints();
        helper.assertTrue(TrainingBlocks.PunchingBag.punch(p), "first punch counts");
        helper.assertTrue(!TrainingBlocks.PunchingBag.punch(p), "spamming is on cooldown");
        helper.assertTrue(d.getTrainingPoints() > tp, "punching gives TP");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void meditationStartsWhenStill(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        PlayerData d = ModCapabilities.getOrThrow(p);
        p.setShiftKeyDown(true);
        p.setOnGround(true);
        p.setDeltaMovement(0, 0, 0);
        for (int i = 0; i < 70; i++) KiTicker.tick(p, d);
        helper.assertTrue(d.isMeditating(), "sneaking still for 3 s starts meditation");
        p.setShiftKeyDown(false);
        KiTicker.tick(p, d);
        helper.assertTrue(!d.isMeditating(), "standing up ends meditation");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void weightsMultiplyTraining(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        PlayerData d = ModCapabilities.getOrThrow(p);
        KiTicker.tick(p, d);
        double bare = d.getTrainingMultiplier();
        p.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.HEAVY_TRAINING_WEIGHTS.get()));
        KiTicker.tick(p, d);
        helper.assertTrue(d.getTrainingMultiplier() >= bare * 2.4, "heavy weights multiply training: " + d.getTrainingMultiplier());
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void timeChamberRoundTrip(GameTestHelper helper) {
        ServerPlayer p = player(helper);
        PlayerData d = ModCapabilities.getOrThrow(p);
        double x = p.getX();
        double z = p.getZ();
        helper.assertTrue(helper.getLevel().getServer().getLevel(TimeChamber.KEY) != null, "the time chamber dimension exists");
        helper.assertTrue(TimeChamber.enter(p), "enter the chamber");
        helper.assertTrue(TimeChamber.isIn(p), "now inside");
        KiTicker.tick(p, d);
        helper.assertTrue(d.getTrainingMultiplier() >= 4, "chamber multiplies training: " + d.getTrainingMultiplier());
        helper.assertTrue(p.serverLevel().getBlockState(TimeChamber.EXIT_DOOR).is(ModBlocks.TIME_CHAMBER_DOOR.get()), "exit door placed");
        helper.assertTrue(TimeChamber.exit(p), "leave the chamber");
        helper.assertTrue(!TimeChamber.isIn(p) && Math.abs(p.getX() - x) < 0.01 && Math.abs(p.getZ() - z) < 0.01, "returned to where we left");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }
}
