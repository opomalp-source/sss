package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.engine.CombatEngine;
import com.dbzenith.combat.engine.Move;
import com.dbzenith.combat.engine.Moves;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.InputGuard;
import com.google.gson.JsonParser;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** CX-19 phase 7: input limits and checks, lag compensation, the pools-only sync, the moves clients predict with. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NetcodeTests {
    private static final String EMPTY = "empty";
    private static final Moves.Input JAB = new Moves.Input(Move.Button.LIGHT, Move.Dir.NEUTRAL, false, false, true);

    private NetcodeTests() {}

    @GameTest(template = EMPTY)
    public static void aFloodOfInputsIsCut(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.create(helper);
        InputGuard.reset(p);
        int accepted = 0;
        for (int i = 0; i < 40; i++) if (InputGuard.allow(p, InputGuard.Kind.MELEE)) accepted++;
        helper.assertTrue(accepted >= 5 && accepted <= 10, "a burst goes through, the flood doesn't: " + accepted);
        helper.assertTrue(InputGuard.stats(p)[1] >= 30, "the rest is dropped and counted");
        helper.assertTrue(InputGuard.allow(p, InputGuard.Kind.DASH), "other kinds have their own budget");
        p.setGameMode(GameType.SPECTATOR);
        helper.assertTrue(!InputGuard.allow(p, InputGuard.Kind.DASH), "no inputs from spectators");
        helper.assertTrue(!InputGuard.sane(p, false, "test"), "a malformed value is rejected");
        InputGuard.reset(p);
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 80)
    public static void lagCompensationHitsWhereTheyWere(GameTestHelper helper) {
        Pig near = pig(helper, new BlockPos(3, 2, 1)), far = pig(helper, new BlockPos(3, 2, 9));
        ServerPlayer laggy = attacker(helper, new BlockPos(1, 2, 1), near, 300), quick = attacker(helper, new BlockPos(1, 2, 9), far, 0);
        helper.runAfterDelay(12, () -> {                                        // a history builds up
            near.teleportTo(near.getX(), near.getY(), near.getZ() + 3);        // both step aside, out of reach
            far.teleportTo(far.getX(), far.getY(), far.getZ() + 3);
            float hn = near.getHealth(), hf = far.getHealth();
            helper.assertTrue(CombatEngine.press(laggy, JAB) && CombatEngine.press(quick, JAB), "two jabs");
            helper.runAfterDelay(8, () -> {
                helper.assertTrue(near.getHealth() < hn, "300 ms of ping: the jab lands where the pig was seen");
                helper.assertTrue(far.getHealth() == hf, "no ping: it misses where the pig is");
                near.discard();
                far.discard();
                TestPlayers.remove(helper, laggy);
                TestPlayers.remove(helper, quick);
                helper.succeed();
            });
        });
    }

    @GameTest(template = EMPTY)
    public static void onlyThePoolsGoWhenOnlyThePoolsChange(GameTestHelper helper) {
        ServerPlayer p = TestPlayers.create(helper);
        PlayerData d = ModCapabilities.getOrThrow(p);
        for (int i = 0; i < 5; i++) d.tickSyncTimer(1);                         // drain what is pending
        d.refill();
        for (int i = 0; i < 5; i++) d.tickSyncTimer(1);
        d.setKi(d.getKi() - 1);
        helper.assertTrue(d.tickSyncTimer(1) == PlayerData.SYNC_POOLS, "ki alone: the small packet");
        d.setReleasePercent(d.getReleasePercent() == 50 ? 60 : 50);
        helper.assertTrue(d.tickSyncTimer(1) == PlayerData.SYNC_FULL, "anything else: the whole state");
        helper.assertTrue(d.tickSyncTimer(1) == PlayerData.SYNC_NONE, "then nothing");
        TestPlayers.remove(helper, p);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void clientsPredictWithTheSameMoves(GameTestHelper helper) {
        helper.assertTrue(!Moves.sources().isEmpty(), "the move files are kept for clients");
        List<Move> copy = new ArrayList<>();
        Moves.sources().forEach((id, json) -> copy.add(Move.parse(id, JsonParser.parseString(json).getAsJsonObject())));
        for (Move.Button b : Move.Button.values()) {
            for (Move.Dir d : new Move.Dir[]{Move.Dir.NEUTRAL, Move.Dir.FORWARD, Move.Dir.BACK, Move.Dir.SIDE}) {
                Moves.Input in = new Moves.Input(b, d, false, false, true);
                for (String prev : new String[]{"start", "light_1", "light_2"}) {
                    Move server = Moves.select(in, prev), client = Moves.select(copy, in, prev);
                    helper.assertTrue(server == null ? client == null : client != null && client.id.equals(server.id),
                            "the client picks what the server picks: " + b + " " + d + " after " + prev);
                }
            }
        }
        helper.succeed();
    }

    private static Pig pig(GameTestHelper helper, BlockPos at) {
        Pig pig = helper.spawn(EntityType.PIG, at);
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        pig.setHealth(1000);
        return pig;
    }

    private static ServerPlayer attacker(GameTestHelper helper, BlockPos at, Pig foe, int pingMs) {
        ServerPlayer a = TestPlayers.create(helper);
        a.setGameMode(GameType.CREATIVE);
        Vec3 p = Vec3.atBottomCenterOf(helper.absolutePos(at));
        a.teleportTo(p.x, p.y, p.z);
        a.lookAt(EntityAnchorArgument.Anchor.EYES, foe.getBoundingBox().getCenter());
        a.latency = pingMs;
        return a;
    }
}
