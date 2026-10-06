package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.npc.TrainingCricket;
import com.dbzenith.npc.TrainingMonkey;
import com.dbzenith.quest.QuestManager;
import com.dbzenith.transform.GodKi;
import com.dbzenith.world.Otherworld;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** CX-12: the other world. Souls, judgement, the Kai's training, the springs, the revive wish. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class OtherworldTests {
    private static final String EMPTY = "empty";

    private OtherworldTests() {}

    @GameTest(template = EMPTY)
    public static void kaiTrainingTeachesKaiokenAndTheSphere(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.create(helper);
        PlayerData d = ModCapabilities.getOrThrow(p);
        helper.assertTrue(!QuestManager.accept(p, "strike_the_cricket"), "the cricket comes after the monkey");
        helper.assertTrue(QuestManager.accept(p, "catch_the_monkey"), "the monkey first");
        QuestManager.event(p, TrainingCricket.STRUCK);                                        // the wrong event does not count
        helper.assertTrue(!QuestManager.turnIn(p, "catch_the_monkey"), "not caught yet");
        QuestManager.event(p, TrainingMonkey.CAUGHT);
        helper.assertTrue(QuestManager.turnIn(p, "catch_the_monkey"), "caught: turned in");
        helper.assertTrue(d.getSkillLevel("kaioken") >= 1, "the Kai teaches the Kaioken: " + d.getSkillLevel("kaioken"));
        helper.assertTrue(QuestManager.accept(p, "strike_the_cricket"), "now the cricket");
        QuestManager.event(p, TrainingCricket.STRUCK);
        helper.assertTrue(QuestManager.turnIn(p, "strike_the_cricket"), "struck: turned in");
        helper.assertTrue(d.knows("gathering_sphere"), "the Kai teaches the Gathering Sphere");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void soulsServeTheirTimeAndTheWishBringsThemBack(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.create(helper);
        PlayerData d = ModCapabilities.getOrThrow(p);
        long now = p.level().getGameTime();
        d.setDead(true, now, false);
        helper.assertTrue(d.isDead() && !d.isEvilSoul(), "a soul in the other world");
        helper.assertTrue(Otherworld.secondsLeft(p, d) == Otherworld.deathSeconds(), "the full time to serve: " + Otherworld.secondsLeft(p, d));
        helper.assertTrue(!Otherworld.returnToLife(p), "no going back from outside the other world");
        d.setDead(true, now - Otherworld.deathSeconds() * 20L, true);
        helper.assertTrue(Otherworld.secondsLeft(p, d) == 0 && d.isEvilSoul(), "time served; an evil soul");
        helper.assertTrue(Otherworld.reviveAll(p.server) >= 1 && !d.isDead(), "the wish brings every soul back");
        helper.assertTrue(!d.isEvilSoul(), "and forgets where it was");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theSpringsAwakenGodKi(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.create(helper);
        PlayerData d = ModCapabilities.getOrThrow(p);
        helper.assertTrue(QuestManager.accept(p, "springs_of_paradise"), "the Grand Kai's first task");
        d.setFlag("spring_soaked", true);
        double xp = d.getGodKiXp();
        helper.assertTrue(QuestManager.turnIn(p, "springs_of_paradise"), "soaked: turned in");
        helper.assertTrue(d.hasFlag(GodKi.FLAG) && d.getGodKiXp() > xp, "godly ki awakened and grown");
        helper.assertTrue(!Otherworld.nearKaiPlanet(p), "ten times gravity only round the Kai's planet");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }
}
