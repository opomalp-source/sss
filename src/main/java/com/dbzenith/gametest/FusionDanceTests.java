package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.fusion.Fusion;
import com.dbzenith.fusion.FusionDance;
import com.dbzenith.registry.ModItems;
import com.dbzenith.stats.Attribute;
import com.dbzenith.stats.StatCalculator;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** 12c: the Fusion Dance (on the beat, and botched) and the Potara. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FusionDanceTests {
    private static final String EMPTY = "empty";

    private FusionDanceTests() {}

    @GameTest(template = EMPTY)
    public static void theDanceOnTheBeatFusesTwoIntoOne(GameTestHelper helper) {
        ServerPlayer a = TestPlayers.create(helper), b = TestPlayers.create(helper);
        b.teleportTo(a.getX() + 1, a.getY(), a.getZ());                        // spawn spread can put them out of reach
        PlayerData ad = ModCapabilities.getOrThrow(a), bd = ModCapabilities.getOrThrow(b);
        b.setGameMode(GameType.SURVIVAL);
        double before = StatCalculator.effective(ad, Attribute.STRENGTH);
        FusionDance.Session s = FusionDance.start(a, b, FusionDance.DANCE);
        helper.assertTrue(s != null && FusionDance.session(b) == s, "the dance begins: " + Fusion.problem(a, b) + " " + a.distanceToSqr(b) + " " + a.gameMode.getGameModeForPlayer() + b.gameMode.getGameModeForPlayer());
        helper.assertTrue(FusionDance.start(a, b, FusionDance.DANCE) == null, "one dance at a time");
        for (int i = 0; i < 3; i++) {
            FusionDance.press(s, a, FusionDance.BEATS[i] - 2);
            FusionDance.press(s, b, FusionDance.BEATS[i] + 3);
        }
        helper.assertTrue(s.allHit(), "six presses on the beat: " + Integer.toBinaryString(s.marks()));
        FusionDance.finish(s);
        helper.assertTrue(FusionDance.session(a) == null, "the dance is over");
        helper.assertTrue(ad.getFusionKind() == Fusion.DANCE && ad.isFusionHost() && !bd.isFusionHost(), "a true fusion, a in control");
        helper.assertTrue(Math.abs(ad.getFusionPower() - DBZConfig.SERVER.fusionDanceBonus.get() * 2) < 1e-6, "equal halves: " + ad.getFusionPower());
        helper.assertTrue(StatCalculator.effective(ad, Attribute.STRENGTH) > before * 2, "far stronger together");
        helper.assertTrue(b.isSpectator() && bd.getFusionPower() == 1.0, "the partner rides along inside");
        helper.assertTrue(!ad.getFusedName().isEmpty() && ad.getFusedName().equals(bd.getFusedName()), "one name: " + ad.getFusedName());
        helper.assertTrue(ad.getFusionUntil() - a.level().getGameTime() == Fusion.duration(Fusion.DANCE), "half an hour");
        Fusion.unfuse(b);
        helper.assertTrue(!ad.isFused() && !bd.isFused(), "two again");
        helper.assertTrue(b.gameMode.getGameModeForPlayer() == GameType.SURVIVAL, "the partner gets their own body back");
        TestPlayers.remove(helper, a);
        TestPlayers.remove(helper, b);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aMissedBeatBotchesTheFusion(GameTestHelper helper) {
        ServerPlayer a = TestPlayers.create(helper), b = TestPlayers.create(helper);
        b.teleportTo(a.getX() + 1, a.getY(), a.getZ());                        // spawn spread can put them out of reach
        PlayerData ad = ModCapabilities.getOrThrow(a);
        FusionDance.Session s = FusionDance.start(a, b, FusionDance.DANCE);
        for (int i = 0; i < 3; i++) FusionDance.press(s, a, FusionDance.BEATS[i]);
        FusionDance.press(s, b, FusionDance.BEATS[0]);
        FusionDance.press(s, b, FusionDance.BEATS[1] - FusionDance.EARLY - 2);   // too early: the beat is spent and missed
        FusionDance.press(s, b, FusionDance.BEATS[2]);
        helper.assertTrue(!s.allHit(), "one beat missed");
        FusionDance.finish(s);
        helper.assertTrue(Fusion.failed(ad.getFusionKind()), "a botched fusion: " + ad.getFusionKind());
        helper.assertTrue(ad.getFusionPower() < 1.0, "weaker than either alone: " + ad.getFusionPower());
        Fusion.unfuse(a);
        helper.assertTrue(!ad.isFused(), "it wears off");
        TestPlayers.remove(helper, a);
        TestPlayers.remove(helper, b);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void thePotaraFusesWithoutADance(GameTestHelper helper) {
        ServerPlayer a = TestPlayers.create(helper), b = TestPlayers.create(helper);
        b.teleportTo(a.getX() + 1, a.getY(), a.getZ());                        // spawn spread can put them out of reach
        PlayerData ad = ModCapabilities.getOrThrow(a);
        a.setGameMode(GameType.SURVIVAL);
        helper.assertTrue(!FusionDance.accept(b, FusionDance.POTARA), "nothing offered yet");
        a.getInventory().add(new ItemStack(ModItems.POTARA_EARRINGS.get()));
        helper.assertTrue(FusionDance.request(a, b, FusionDance.POTARA), "an earring offered: " + Fusion.problem(a, b) + " " + a.distanceToSqr(b));
        helper.assertTrue(!FusionDance.accept(b, FusionDance.DANCE), "not a dance invitation");
        helper.assertTrue(FusionDance.accept(b, FusionDance.POTARA), "put on");
        helper.assertTrue(!a.getInventory().contains(new ItemStack(ModItems.POTARA_EARRINGS.get())), "the pair is spent");
        FusionDance.Session s = FusionDance.session(a);
        helper.assertTrue(s != null && s.kind == FusionDance.POTARA, "pulled together");
        FusionDance.finish(s);
        helper.assertTrue(ad.getFusionKind() == Fusion.POTARA && ad.getFusionPower() > DBZConfig.SERVER.fusionDanceBonus.get() * 2 - 1e-6,
                "the Potara is the stronger fusion: " + ad.getFusionPower());
        helper.assertTrue(ad.getFusionUntil() - a.level().getGameTime() == Fusion.duration(Fusion.POTARA), "an hour");
        helper.assertTrue(Fusion.fusedName("Kakaro", "Vegito", Fusion.DANCE).equals("Kakito"), Fusion.fusedName("Kakaro", "Vegito", Fusion.DANCE));
        Fusion.unfuse(a);
        TestPlayers.remove(helper, a);
        TestPlayers.remove(helper, b);
        helper.succeed();
    }
}
