package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.Destruction;
import com.dbzenith.combat.PvpZones;
import com.dbzenith.combat.engine.CombatEngine;
import com.dbzenith.combat.engine.Fighter;
import com.dbzenith.combat.engine.Move;
import com.dbzenith.combat.engine.Moves;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.skill.CustomTechniques;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** CX-20: blows that send fighters flying, and the craters they (and ki attacks) leave. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DestructionTests {
    private static final String EMPTY = "empty";
    private static final Moves.Input SMASH = new Moves.Input(Move.Button.HEAVY, Move.Dir.NEUTRAL, false, false, true);
    /** The test block: 11 x 6 x 11 of stone, floating well above the test plots. */
    private static final int SIDE = 11, HEIGHT = 6, LIFT = 14;

    private DestructionTests() {}

    @GameTest(template = EMPTY)
    public static void harderBlowsHitHarder(GameTestHelper helper) {
        ServerPlayer v = TestPlayers.create(helper);
        PlayerData d = ModCapabilities.getOrThrow(v);
        double max = d.getDerived().maxBody();
        double tap = CombatEngine.force(v, max * 0.02, false, false, 1), heavy = CombatEngine.force(v, max * 0.25, true, false, 1);
        helper.assertTrue(tap < 1 && heavy > 1.6 && heavy <= 3, "a tap " + tap + ", a heavy taking a quarter " + heavy);
        helper.assertTrue(CombatEngine.force(v, max * 0.02, false, true, 8) > tap, "a critical deep in a combo lands harder");
        helper.assertTrue(!CombatEngine.blowsAway(Move.Launch.NONE, 3, tap), "a tap in a short combo: no flight");
        helper.assertTrue(CombatEngine.blowsAway(Move.Launch.NONE, DBZConfig.SERVER.blowAwayComboHits.get(), tap), "the combo's fifth hit sends them flying");
        helper.assertTrue(CombatEngine.blowsAway(Move.Launch.NONE, 1, heavy), "so does one hard enough blow");
        helper.assertTrue(CombatEngine.blowsAway(Move.Launch.AWAY, 1, 0), "and every knock-away move");
        helper.assertTrue(!CombatEngine.blowsAway(Move.Launch.UP, 5, 3), "launchers and spikes keep their own path");
        TestPlayers.remove(helper, v);
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void aSmashSendsTheFoeFlying(GameTestHelper helper) {
        ServerPlayer a = TestPlayers.create(helper), b = TestPlayers.create(helper);
        a.setGameMode(GameType.CREATIVE);
        b.setGameMode(GameType.SURVIVAL);
        ModCapabilities.getOrThrow(b).refill();
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
        a.teleportTo(at.x, at.y, at.z);
        b.teleportTo(at.x + 2, at.y, at.z);
        a.lookAt(EntityAnchorArgument.Anchor.EYES, b.getEyePosition());
        helper.assertTrue(CombatEngine.press(a, SMASH), "the smash comes");
        helper.succeedWhen(() -> {
            long now = helper.getLevel().getGameTime();
            Fighter g = CombatEngine.peek(b);
            helper.assertTrue(g != null && g.blownAway(now) && g.flightForce() > 0.5, "flying, with a force for its crater");
            TestPlayers.remove(helper, a);
            TestPlayers.remove(helper, b);
        });
    }

    private static BlockPos fill(GameTestHelper helper) {
        BlockPos o = helper.absolutePos(new BlockPos(-4, LIFT, -4));
        ServerLevel level = helper.getLevel();
        for (int x = 0; x < SIDE; x++) for (int y = 0; y < HEIGHT; y++) for (int z = 0; z < SIDE; z++) {
            level.setBlock(o.offset(x, y, z), Blocks.STONE.defaultBlockState(), 2);
        }
        for (int x = -2; x < SIDE + 2; x++) for (int y = HEIGHT; y < HEIGHT + 6; y++) for (int z = -2; z < SIDE + 2; z++) {
            level.setBlock(o.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        }
        return o;
    }

    private static void clear(GameTestHelper helper, BlockPos o) {
        for (int x = -2; x < SIDE + 2; x++) for (int y = -2; y < HEIGHT + 6; y++) for (int z = -2; z < SIDE + 2; z++) {
            helper.getLevel().setBlock(o.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        }
    }

    private static Vec3 top(BlockPos o) {
        return new Vec3(o.getX() + SIDE / 2.0, o.getY() + HEIGHT, o.getZ() + SIDE / 2.0);
    }

    private static int count(GameTestHelper helper, BlockPos o, net.minecraft.world.level.block.Block b) {
        int n = 0;
        for (int x = 0; x < SIDE; x++) for (int y = 0; y < HEIGHT; y++) for (int z = 0; z < SIDE; z++) {
            if (helper.getLevel().getBlockState(o.offset(x, y, z)).is(b)) n++;
        }
        return n;
    }

    @GameTest(template = EMPTY)
    public static void craterSizeFollowsTheForce(GameTestHelper helper) {
        DBZConfig.Server c = DBZConfig.SERVER;
        int spawnSafe = c.pvpSpawnSafeRadius.get();
        c.pvpSpawnSafeRadius.set(0);                                             // the test plots may sit near the world spawn
        ServerPlayer p = TestPlayers.create(helper);
        ServerLevel level = helper.getLevel();
        Vec3 up = new Vec3(0, 1, 0);
        try {
            BlockPos o = fill(helper);
            helper.assertTrue(Destruction.crater(level, top(o), up, 0.2, p) == 0 && count(helper, o, Blocks.COBBLESTONE) == 0, "a tap leaves nothing");
            helper.assertTrue(Destruction.crater(level, top(o), up, 0.6, p) == 0 && count(helper, o, Blocks.COBBLESTONE) > 0, "a light impact only cracks the surface");
            o = fill(helper);
            int small = Destruction.crater(level, top(o), up, 1.2, p);
            o = fill(helper);
            int big = Destruction.crater(level, top(o), up, 2.2, p);
            helper.assertTrue(small > 0 && big > small * 2, "a harder impact, a far bigger crater: " + small + " then " + big);
            helper.assertTrue(level.getBlockState(BlockPos.containing(top(o)).below()).isAir(), "dug where it struck");

            o = fill(helper);
            BlockPos chest = BlockPos.containing(top(o)).below();
            level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 2);
            level.setBlock(chest.east(), Blocks.OBSIDIAN.defaultBlockState(), 2);
            Destruction.crater(level, top(o), up, 1.0, p);
            helper.assertTrue(level.getBlockState(chest).is(Blocks.CHEST), "a chest is never torn up");
            helper.assertTrue(level.getBlockState(chest.east()).is(Blocks.OBSIDIAN), "obsidian outlasts a middling impact");

            o = fill(helper);
            PvpZones zones = PvpZones.of(level.getServer());
            zones.add("crater_test", level.dimension().location().toString(), o.offset(-1, -1, -1), o.offset(SIDE, HEIGHT + 2, SIDE));
            int inZone = Destruction.crater(level, top(o), up, 2.5, p);
            zones.remove("crater_test");
            helper.assertTrue(inZone == 0 && count(helper, o, Blocks.STONE) == SIDE * HEIGHT * SIDE, "nothing in a safe zone");

            boolean was = c.destructionEnabled.get();
            c.destructionEnabled.set(false);
            int off = Destruction.crater(level, top(o), up, 2.5, p);
            c.destructionEnabled.set(was);
            helper.assertTrue(off == 0, "nothing with destruction switched off");
            clear(helper, o);
        } finally {
            c.pvpSpawnSafeRadius.set(spawnSafe);
        }
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void kiCreatorTechniquesCanBeCalm(GameTestHelper helper) {
        var loud = new CustomTechniques.Spec("Loud", CustomTechniques.Kind.BLAST, 3, 0, 0xFFFFFF);
        var calm = new CustomTechniques.Spec("Calm", CustomTechniques.Kind.BLAST, 3, CustomTechniques.CALM, 0xFFFFFF);
        helper.assertTrue(CustomTechniques.build(0, loud).destructive(), "a design tears up the land by default");
        helper.assertTrue(!CustomTechniques.build(0, calm).destructive(), "a calm one leaves it alone");
        helper.assertTrue(CustomTechniques.tpCost(calm) == CustomTechniques.tpCost(loud), "being calm costs nothing");
        var full = new CustomTechniques.Spec("Two", CustomTechniques.Kind.BLAST, 3,
                1 << CustomTechniques.Mod.FAST.ordinal() | 1 << CustomTechniques.Mod.LARGE.ordinal() | CustomTechniques.CALM, 0xFFFFFF);
        helper.assertTrue(Integer.bitCount(CustomTechniques.modBits(full.mods())) == 2, "nor a modifier slot");
        helper.succeed();
    }
}
