package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.registry.ModItems;
import com.dbzenith.stats.Attribute;
import com.dbzenith.stats.FightingPath;
import com.dbzenith.stats.Race;
import com.dbzenith.stats.StatCalculator;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Phase 0 checks for the player-data layer. Run with {@code gradlew runGameTestServer}
 * (exits non-zero on failure) or in-game with {@code /test runall}.
 */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlayerDataTests {
    private static final String EMPTY = "empty";

    private PlayerDataTests() {}

    @GameTest(template = EMPTY)
    public static void attachesAndInitializesOnLogin(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper);
        PlayerData data = ModCapabilities.get(player).orElse(null);
        helper.assertTrue(data != null, "player data capability missing");
        int start = DBZConfig.SERVER.startingAttribute.get();
        for (Attribute a : Attribute.values()) {
            helper.assertTrue(data.getAttribute(a) == start, a.id() + " should start at " + start + " but was " + data.getAttribute(a));
        }
        helper.assertTrue(data.getKi() > 0 && data.getKi() == data.getDerived().maxKi(), "ki should start full");
        TestPlayers.remove(helper, player);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void nbtRoundTrip(GameTestHelper helper) {
        PlayerData a = new PlayerData();
        a.initDefaultsIfNeeded();
        a.setAttribute(Attribute.STRENGTH, 321);
        a.setAttribute(Attribute.SPIRIT, 77);
        a.setTrainingPoints(9001);
        a.setRace(Race.NAMEKIAN);
        a.setPath(FightingPath.SPIRITUALIST);
        a.setReleasePercent(85);
        a.setAlignment(-40);
        a.setCharacterCreated(true);
        a.setKi(12.5);

        PlayerData b = new PlayerData();
        b.load(a.save());
        helper.assertTrue(a.save().equals(b.save()), "round trip mismatch:\n" + a.save() + "\n" + b.save());
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void derivedStatsScaleAndCap(GameTestHelper helper) {
        PlayerData low = new PlayerData();
        low.initDefaultsIfNeeded();
        PlayerData high = new PlayerData();
        high.initDefaultsIfNeeded();
        high.setAttribute(Attribute.CONSTITUTION, 500);
        high.setAttribute(Attribute.DEXTERITY, 1_000_000);
        high.recomputeIfStale();

        helper.assertTrue(high.getDerived().maxBody() > low.getDerived().maxBody(), "CON should raise max body");
        helper.assertTrue(high.getDerived().maxStamina() > low.getDerived().maxStamina(), "CON should raise max stamina");
        helper.assertTrue(high.getDerived().evasion() <= DBZConfig.SERVER.evasionCap.get(), "evasion must respect its cap");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void tpCostFollowsPath(GameTestHelper helper) {
        PlayerData fighter = new PlayerData();
        fighter.initDefaultsIfNeeded();
        fighter.setPath(FightingPath.FIGHTER);
        PlayerData spiritualist = new PlayerData();
        spiritualist.initDefaultsIfNeeded();
        spiritualist.setPath(FightingPath.SPIRITUALIST);

        helper.assertTrue(StatCalculator.tpCost(fighter, Attribute.STRENGTH) < StatCalculator.tpCost(spiritualist, Attribute.STRENGTH),
                "fighters should raise STR more cheaply");
        helper.assertTrue(StatCalculator.tpCost(fighter, Attribute.KI_POWER) > StatCalculator.tpCost(spiritualist, Attribute.KI_POWER),
                "spiritualists should raise KI_POWER more cheaply");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void survivesDeathRespawn(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper);
        PlayerData before = ModCapabilities.getOrThrow(player);
        before.setAttribute(Attribute.STRENGTH, 250);
        before.setTrainingPoints(1234);
        before.setKi(1);

        // keepEverything=false is the death path: fires PlayerEvent.Clone with isWasDeath() = true.
        ServerPlayer respawned = server(helper).getPlayerList().respawn(player, false);
        PlayerData after = ModCapabilities.getOrThrow(respawned);
        helper.assertTrue(respawned != player, "respawn should create a new player entity");
        helper.assertTrue(after.getAttribute(Attribute.STRENGTH) == 250, "STR lost on death");
        helper.assertTrue(after.getTrainingPoints() == 1234, "TP lost on death");
        after.recomputeIfStale();
        helper.assertTrue(after.getKi() == after.getDerived().maxKi(), "ki should refill on death");
        TestPlayers.remove(helper, respawned);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void commandSetsStats(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper);
        MinecraftServer server = server(helper);
        String target = player.getGameProfile().getName();
        run(server, "dbz set " + target + " strength 123");
        run(server, "dbz tp add " + target + " 500");
        run(server, "dbz race " + target + " saiyan");
        run(server, "dbz set " + target + " release 250");

        PlayerData d = ModCapabilities.getOrThrow(player);
        helper.assertTrue(d.getAttribute(Attribute.STRENGTH) == 123, "set strength failed: " + d.getAttribute(Attribute.STRENGTH));
        helper.assertTrue(d.getTrainingPoints() == 500, "tp add failed: " + d.getTrainingPoints());
        helper.assertTrue(d.getRace() == Race.SAIYAN, "race failed: " + d.getRace());
        helper.assertTrue(d.getReleasePercent() == 100, "release should clamp to 100 but was " + d.getReleasePercent());

        run(server, "dbz reset " + target);
        helper.assertTrue(d.getAttribute(Attribute.STRENGTH) == DBZConfig.SERVER.startingAttribute.get(), "reset failed");
        helper.assertTrue(d.getRace() == Race.HUMAN, "reset should restore race");
        TestPlayers.remove(helper, player);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void senzuBeanRefills(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper);
        PlayerData d = ModCapabilities.getOrThrow(player);
        d.setKi(0);
        d.setStamina(0);
        player.setHealth(1);
        ModItems.SENZU_BEAN.get().finishUsingItem(new ItemStack(ModItems.SENZU_BEAN.get()), helper.getLevel(), player);
        helper.assertTrue(d.getKi() == d.getDerived().maxKi(), "senzu should refill ki");
        helper.assertTrue(d.getStamina() == d.getDerived().maxStamina(), "senzu should refill stamina");
        helper.assertTrue(player.getHealth() == player.getMaxHealth(), "senzu should heal fully");
        TestPlayers.remove(helper, player);
        helper.succeed();
    }

    private static MinecraftServer server(GameTestHelper helper) {
        return helper.getLevel().getServer();
    }

    private static void run(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }
}
