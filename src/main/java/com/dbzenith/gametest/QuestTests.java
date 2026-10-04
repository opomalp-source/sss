package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.npc.ModNpcs;
import com.dbzenith.quest.QuestManager;
import com.dbzenith.quest.Quests;
import com.dbzenith.stats.Attribute;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Phase 4 slice 5: quests, kill tracking, rewards, Galactic Patrol ranks. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class QuestTests {
    private static final String EMPTY = "empty";

    private QuestTests() {}

    @GameTest(template = EMPTY)
    public static void storyQuestAcceptCompleteReward(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.create(helper);
        PlayerData d = ModCapabilities.getOrThrow(p);
        helper.assertTrue(!QuestManager.accept(p, "energy_within"), "later story quests are locked");
        helper.assertTrue(QuestManager.accept(p, "first_steps"), "first quest can be accepted");
        helper.assertTrue(!QuestManager.turnIn(p, "first_steps"), "not complete yet");
        d.setAttribute(Attribute.STRENGTH, d.getAttribute(Attribute.STRENGTH) + 12);
        long tp = d.getTrainingPoints();
        helper.assertTrue(QuestManager.turnIn(p, "first_steps"), "level 10 completes it");
        helper.assertTrue(d.getTrainingPoints() == tp + Quests.byId("first_steps").reward().tp(), "TP reward paid");
        helper.assertTrue(p.getInventory().contains(new net.minecraft.world.item.ItemStack(com.dbzenith.registry.ModItems.GI.get(0).get())), "item reward given");
        helper.assertTrue(!QuestManager.accept(p, "first_steps"), "story quests are one-time");
        helper.assertTrue(QuestManager.accept(p, "energy_within"), "next story quest unlocked");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void killsCountAndBountiesBuildRank(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.create(helper);
        PlayerData d = ModCapabilities.getOrThrow(p);
        helper.assertTrue(QuestManager.accept(p, "bounty_soldiers"), "rank 0 bounty available");
        helper.assertTrue(!QuestManager.accept(p, "bounty_tyrant"), "high-rank bounty locked");
        for (int i = 0; i < 10; i++) {
            var soldier = helper.spawnWithNoFreeWill(ModNpcs.KI_SOLDIER.get(), new BlockPos(1, 1, 1));
            soldier.hurt(p.damageSources().playerAttack(p), 1_000_000f);
        }
        Zombie z = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        z.hurt(p.damageSources().playerAttack(p), 1_000_000f); // wrong type: must not count
        helper.assertTrue(d.questProgress("bounty_soldiers")[0] == 10, "ten soldier kills counted: " + d.questProgress("bounty_soldiers")[0]);
        helper.assertTrue(QuestManager.turnIn(p, "bounty_soldiers"), "bounty turned in");
        helper.assertTrue(d.getPatrolRep() == 20, "reputation earned");
        helper.assertTrue(QuestManager.accept(p, "bounty_soldiers"), "bounties repeat");
        d.addPatrolRep(200);
        helper.assertTrue(QuestManager.patrolRank(d) >= 2, "rank rises with reputation");
        helper.assertTrue(QuestManager.accept(p, "bounty_tyrant"), "higher rank unlocks bigger bounties");
        d.setAlignment(-50);
        helper.assertTrue(!QuestManager.accept(p, "bounty_brute"), "the Patrol refuses evil fighters");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void divineRitualGrantsGodKi(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.create(helper);
        PlayerData d = ModCapabilities.getOrThrow(p);
        for (String q : java.util.List.of("first_steps", "energy_within", "trial_by_combat", "weight_of_the_world", "beyond_limits", "the_wish")) {
            d.startQuest(q, 1);
            d.finishQuest(q);
        }
        helper.assertTrue(QuestManager.accept(p, "divine_ritual"), "ritual unlocked after the story");
        d.setAttribute(Attribute.STRENGTH, 1200);
        d.setFlag("visited:dbzenith:time_chamber", true);
        helper.assertTrue(QuestManager.turnIn(p, "divine_ritual"), "ritual complete");
        helper.assertTrue(d.hasFlag("god_ki"), "godly ki awakened");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }
}
