package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.ki.KiTicker;
import com.dbzenith.world.Planet;
import com.dbzenith.world.TimeChamber;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Phase 4 slice 6: planets and space travel. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlanetTests {
    private static final String EMPTY = "empty";

    private PlanetTests() {}

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void podTravelGravityAndCooldown(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        for (Planet p : Planet.values()) helper.assertTrue(server.getLevel(p.dimension()) != null, p + " dimension exists");
        ServerPlayer p = TestPlayers.create(helper);
        p.setGameMode(GameType.SURVIVAL);
        PlayerData d = ModCapabilities.getOrThrow(p);
        helper.assertTrue(Planet.travel(p, Planet.NORTHERN_PLANET), "fly to the Northern Planet");
        helper.assertTrue(Planet.of(p.level()) == Planet.NORTHERN_PLANET, "arrived");
        helper.assertTrue(p.level().getBlockState(p.blockPosition().below()).isSolidRender(p.level(), p.blockPosition().below()), "landed on solid ground");
        KiTicker.tick(p, d);
        helper.assertTrue(d.getGravity(p.level().getGameTime()) == 10, "10x gravity there: " + d.getGravity(p.level().getGameTime()));
        helper.assertTrue(!Planet.travel(p, Planet.EARTH), "the pod must refuel first");
        d.setCooldown("space_travel", 0);
        helper.assertTrue(Planet.travel(p, Planet.NAMEK), "then fly on to Namek");
        helper.assertTrue(Planet.of(p.level()) == Planet.NAMEK, "arrived on Namek");
        helper.assertTrue(p.level().getFluidState(p.blockPosition().below()).isEmpty(), "landed on dry ground, not at sea: " + p.blockPosition());
        d.setCooldown("space_travel", 0);
        TimeChamber.enter(p);
        helper.assertTrue(!Planet.travel(p, Planet.EARTH), "no pod flights out of the Time Chamber");
        TimeChamber.exit(p);
        TestPlayers.remove(helper, p);
        helper.succeed();
    }
}
