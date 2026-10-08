package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.npc.ModNpcs;
import com.dbzenith.stats.Attribute;
import com.dbzenith.style.MasterRoster;
import com.dbzenith.style.StyleLogic;
import com.dbzenith.style.StyleMaster;
import com.dbzenith.style.StyleSlot;
import com.dbzenith.style.Styles;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** CX-20 phase 7: fighting styles, the masters who teach them, and choosing each animation slot. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StyleTests {
    private static final String EMPTY = "empty";

    private StyleTests() {}

    private static StyleMaster master(GameTestHelper helper, MasterRoster m, ServerPlayer p) {
        StyleMaster e = helper.spawn(ModNpcs.MASTERS.get(m).get(), new BlockPos(1, 2, 1));
        Vec3 at = e.position().add(1.5, 0, 0);
        p.teleportTo(at.x, at.y, at.z);
        return e;
    }

    private static ServerPlayer student(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.create(helper);
        p.setGameMode(GameType.SURVIVAL);
        PlayerData d = ModCapabilities.getOrThrow(p);
        for (String s : d.learnedStylesView().toArray(new String[0])) d.learnStyle(s, false);
        for (MasterRoster m : MasterRoster.values()) {
            d.setAffinity(m.id(), 0);
            d.addTrainedSeconds(m.id(), -d.getTrainedSeconds(m.id()));
            d.setMasterDay(m.id(), -1);
        }
        d.setTrainingWith("");
        return p;
    }

    @GameTest(template = EMPTY)
    public static void everyMasterTeachesAndMoves(GameTestHelper helper) {
        helper.assertTrue(Styles.all().size() >= 16 && Styles.masters().size() >= 16, "the styles and masters load: " + Styles.all().size() + "/" + Styles.masters().size());
        for (MasterRoster m : MasterRoster.values()) {
            Styles.Master def = Styles.master(m.id());
            helper.assertTrue(def != null, "data for " + m.id());
            helper.assertTrue(!Styles.taughtBy(m.id()).isEmpty(), m.id() + " teaches something");
            helper.assertTrue(Styles.style(def.uses()) != null, m.id() + " moves in a style that exists");
            helper.assertTrue(Styles.npcStyle(DBZenith.MOD_ID + ":" + m.id()) != null, m.id() + " moves in their style");
        }
        for (Styles.Style s : Styles.all()) {
            helper.assertTrue(MasterRoster.byId(s.master()) != null, s.id() + " has a master");
            helper.assertTrue(s.clips().size() >= 4, s.id() + " changes at least four slots");
            helper.assertTrue(s.has(StyleSlot.FIGHT_STANCE), s.id() + " has a fighting stance");
        }
        helper.assertTrue(Styles.npcStyle("dbzenith:tyrant_lord") != null, "other NPCs move in styles too");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void learningTakesEveryRequirement(GameTestHelper helper) {
        ServerPlayer p = student(helper);
        PlayerData d = ModCapabilities.getOrThrow(p);
        StyleMaster roshi = master(helper, MasterRoster.MASTER_ROSHI, p);
        Styles.Style s = Styles.style("turtle_hermit");
        for (Attribute a : Attribute.values()) d.setAttribute(a, 5);
        d.recomputeIfStale();
        helper.assertTrue(!StyleLogic.meets(p, d, s) && !StyleLogic.learn(p, d, roshi.getId(), s.id()), "a beginner can't learn it");
        for (Attribute a : Attribute.values()) d.setAttribute(a, 200);
        d.recomputeIfStale();
        d.setAffinity("master_roshi", s.requirements().affinity());
        d.addTrainedSeconds("master_roshi", s.requirements().trainingMinutes() * 60);
        helper.assertTrue(!StyleLogic.meets(p, d, s), "the fish is still missing");
        p.getInventory().add(new ItemStack(Items.COOKED_COD, 10));
        helper.assertTrue(StyleLogic.meets(p, d, s), "everything met: " + StyleLogic.check(p, d, s).stream().filter(l -> !l.met()).map(l -> l.text().getString()).toList());
        helper.assertTrue(!StyleLogic.learn(p, d, roshi.getId() + 9999, s.id()), "only from the master in person");
        helper.assertTrue(StyleLogic.learn(p, d, roshi.getId(), s.id()) && d.hasStyle(s.id()), "learned");
        helper.assertTrue(p.getInventory().countItem(Items.COOKED_COD) == 4, "six fish handed over");
        helper.assertTrue(d.getStyleSlot(StyleSlot.FIGHT_STANCE.id).equals(s.id()) && d.getStyleSlot(StyleSlot.IDLE.id).equals(s.id()), "put in the default slots");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void eachSlotIsChosenOnItsOwn(GameTestHelper helper) {
        ServerPlayer p = student(helper);
        PlayerData d = ModCapabilities.getOrThrow(p);
        StyleLogic.grant(d, "saiyan_prince");
        helper.assertTrue(d.getStyleSlot("walk").equals("saiyan_prince") && d.getStyleSlot("fight_stance").equals("saiyan_prince"), "a first style fills its slots");
        StyleLogic.grant(d, "wild_saiyan");
        helper.assertTrue(d.getStyleSlot("walk").equals("saiyan_prince"), "a second leaves chosen slots alone");
        helper.assertTrue(d.getStyleSlot("sprint").equals("wild_saiyan"), "and fills the ones still on the default");
        helper.assertTrue(StyleLogic.equip(p, d, "walk", "wild_saiyan") && d.getStyleSlot("walk").equals("wild_saiyan"), "Goku's walk with Vegeta's stance");
        helper.assertTrue(d.getStyleSlot("fight_stance").equals("saiyan_prince"), "the other slots unchanged");
        helper.assertTrue(!StyleLogic.equip(p, d, "walk", "pride_trooper"), "not a style you haven't learned");
        helper.assertTrue(!StyleLogic.equip(p, d, "sprint", "saiyan_prince"), "not a style without that slot");
        helper.assertTrue(StyleLogic.equip(p, d, "walk", "") && d.getStyleSlot("walk").isEmpty(), "back to the default");
        d.learnStyle("saiyan_prince", false);
        helper.assertTrue(d.getStyleSlot("fight_stance").isEmpty(), "forgetting a style empties its slots");
        PlayerData copy = new PlayerData();
        copy.load(d.save());
        helper.assertTrue(copy.hasStyle("wild_saiyan") && copy.getStyleSlot("sprint").equals("wild_saiyan") && copy.styleSlotsCode().equals(d.styleSlotsCode()), "saved and loaded");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void affinityGiftsAndTraining(GameTestHelper helper) {
        ServerPlayer p = student(helper);
        PlayerData d = ModCapabilities.getOrThrow(p);
        StyleMaster goku = master(helper, MasterRoster.GOKU, p);
        StyleLogic.talk(p, d, goku, ItemStack.EMPTY);
        StyleLogic.talk(p, d, goku, ItemStack.EMPTY);
        helper.assertTrue(d.getAffinity("goku") == 1, "one point for the first talk of the day: " + d.getAffinity("goku"));
        ItemStack beef = new ItemStack(Items.COOKED_BEEF, 5);
        for (int i = 0; i < 4; i++) StyleLogic.talk(p, d, goku, beef);
        helper.assertTrue(d.getAffinity("goku") == 1 + 3 * StyleLogic.GIFT_AFFINITY && beef.getCount() == 2, "three gifts a day: " + d.getAffinity("goku"));
        ItemStack dirt = new ItemStack(Items.DIRT, 1);
        StyleLogic.talk(p, d, goku, dirt);
        helper.assertTrue(dirt.getCount() == 1, "he doesn't want dirt");

        StyleLogic.train(p, d, goku.getId());
        helper.assertTrue(d.getTrainingWith().equals("goku"), "training starts");
        for (int t = 1; t <= 60; t++) StyleLogic.tick(p, d, t * 20L);
        helper.assertTrue(d.getTrainedSeconds("goku") == 60, "a minute together: " + d.getTrainedSeconds("goku"));
        p.teleportTo(p.getX() + 40, p.getY(), p.getZ());
        StyleLogic.tick(p, d, 61 * 20L);
        helper.assertTrue(d.getTrainingWith().isEmpty() && d.getTrainedSeconds("goku") == 60, "walking off ends it");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void stylesArePurelyCosmetic(GameTestHelper helper) {
        ServerPlayer p = student(helper);
        PlayerData d = ModCapabilities.getOrThrow(p);
        for (Attribute a : Attribute.values()) d.setAttribute(a, 300);
        d.invalidateDerived();
        d.recomputeIfStale();
        String before = d.getDerived().save().toString();
        long bp = com.dbzenith.stats.StatCalculator.battlePower(d);
        StyleLogic.grant(d, "pride_trooper");
        for (Styles.Style s : Styles.all()) {
            StyleLogic.grant(d, s.id());
            for (StyleSlot slot : s.clips().keySet()) StyleLogic.equip(p, d, slot.id, s.id());
        }
        d.invalidateDerived();
        d.recomputeIfStale();
        helper.assertTrue(d.getDerived().save().toString().equals(before), "every style learned and worn: the same stats");
        helper.assertTrue(com.dbzenith.stats.StatCalculator.battlePower(d) == bp, "and the same battle power");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void everyStyleHasItsClipsAndNames(GameTestHelper helper) {
        com.google.gson.JsonObject lang;
        try (var in = StyleTests.class.getResourceAsStream("/assets/dbzenith/lang/en_us.json")) {
            lang = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            helper.fail("no lang file: " + e);
            return;
        }
        for (Styles.Style s : Styles.all()) {
            helper.assertTrue(lang.has(s.nameKey()) && lang.has(s.descKey()), s.id() + " has a name and a description");
            for (String clip : s.clips().values()) {
                helper.assertTrue(StyleTests.class.getResource("/assets/dbzenith/motion/clips/" + clip + ".json") != null, s.id() + ": the clip " + clip + " exists");
            }
        }
        for (Styles.Master m : Styles.masters()) {
            helper.assertTrue(lang.has(m.nameKey()) && lang.has(m.greetKey()) && lang.has("item.dbzenith." + m.id() + "_spawn_egg"), m.id() + " has a name, a greeting and an egg name");
            helper.assertTrue(StyleTests.class.getResource("/assets/dbzenith/textures/entity/fighter/" + m.id() + ".png") != null, m.id() + " has a skin");
        }
        for (StyleSlot slot : StyleSlot.ALL) helper.assertTrue(lang.has(slot.nameKey()), "the slot " + slot.id + " has a name");
        for (var k : com.dbzenith.combat.meter.MeterRules.get().kaioken()) helper.assertTrue(lang.has("meter.dbzenith.kaioken_" + k.stage()), "Kaioken x" + k.stage() + " has a name");
        helper.succeed();
    }
}
