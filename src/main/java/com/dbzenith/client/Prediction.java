package com.dbzenith.client;

import com.dbzenith.DBZenith;
import com.dbzenith.client.anim.AnimController;
import com.dbzenith.client.anim.FighterStates;
import com.dbzenith.combat.engine.Fighter;
import com.dbzenith.combat.engine.Move;
import com.dbzenith.combat.engine.Moves;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.AnimEventPacket;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side prediction (CX-19 phase 7): what you press starts on your screen at once instead of a round trip later,
 * and the server's word, when it comes, wins.
 * <ul>
 *   <li><b>Melee</b>: the client keeps a copy of the move files ({@code MovesSyncPacket}) and the chain it believes in
 *       (a blow it saw land, from its own impacts, continues the chain). A press picks the move as the server would and
 *       plays its clip and swing. The server's {@code MoveAnimPacket} confirms it (nothing replays), corrects it (the
 *       server's move plays instead), or never comes (the predicted pose is dropped after the ping plus a few ticks).</li>
 *   <li><b>Quick ki blasts</b>: the throwing pose on release; the server's pose event is then swallowed.</li>
 *   <li><b>Dashes</b>: the plain dash moves you at once with the server's own formula and settings. The client says it
 *       predicted, so the server doesn't push the same velocity again. Whenever the Dash key would do something else
 *       (floored, stunned, guarding, a chase, a super dash), nothing is predicted and the server's move comes as before.</li>
 * </ul>
 * Prediction only ever touches your own screen: costs, hits and damage are the server's alone.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, value = Dist.CLIENT)
public final class Prediction {
    private static List<Move> moves = List.of();
    private static Map<String, Move> byId = Map.of();

    private static Move current;          // the move we believe is under way
    private static long startedAt;
    private static boolean landed, pending;
    private static long confirmBy;
    private static String lastMove = "start";
    private static long chainUntil;
    private static long blastAt = Long.MIN_VALUE / 2, dashAt = Long.MIN_VALUE / 2;

    private Prediction() {}

    public static void setMoves(Map<String, String> sources) {
        List<Move> out = new ArrayList<>();
        Map<String, Move> ids = new LinkedHashMap<>();
        sources.forEach((id, json) -> {
            try {
                Move m = Move.parse(id, JsonParser.parseString(json).getAsJsonObject());
                out.add(m);
                ids.put(id, m);
            } catch (RuntimeException ignored) {                               // a move the client can't read is just not predicted
            }
        });
        moves = out;
        byId = ids;
    }

    private static boolean enabled() {
        return DBZConfig.CLIENT.prediction.get();
    }

    /** Round trip in ticks, from the server list's ping. */
    static int roundTripTicks(Minecraft mc) {
        PlayerInfo info = mc.getConnection() == null || mc.player == null ? null : mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return info == null ? 2 : Mth.clamp(info.getLatency() / 50, 0, 40);
    }

    /** Can this client's own fighter act at all, as far as it knows? */
    private static boolean free(Minecraft mc, LocalPlayer p) {
        if (!enabled() || mc.screen != null || !p.isAlive() || p.isSpectator() || mc.level == null) return false;
        Fighter.State s = FighterStates.get(p.getId());
        if (s == Fighter.State.STUNNED || s == Fighter.State.LAUNCHED || s == Fighter.State.KNOCKDOWN) return false;
        PlayerData d = ClientPlayerData.get();
        return !d.isTransforming() && !d.isMeditating() && !ClientStruggle.active();
    }

    // ------------------------------------------------------------------ melee

    /** A melee press about to be sent: start the move we expect. */
    public static void melee(boolean heavy, byte push) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || moves.isEmpty() || !free(mc, p) || !p.getMainHandItem().isEmpty() || ClientPlayerData.get().isGuarding()) return;
        long now = mc.level.getGameTime();
        boolean busy = current != null && now - startedAt < current.total();
        if (busy && !(landed && now - startedAt >= current.cancel)) return;   // mid-move: the server buffers it; so do we, by waiting
        String previous = busy || now <= chainUntil ? lastMove(busy) : "start";
        Move.Dir dir = switch (push) {
            case 1 -> Move.Dir.FORWARD;
            case 2 -> Move.Dir.BACK;
            case 3 -> Move.Dir.SIDE;
            default -> Move.Dir.NEUTRAL;
        };
        float pitch = p.getXRot();
        Move m = Moves.select(moves, new Moves.Input(heavy ? Move.Button.HEAVY : Move.Button.LIGHT, dir, pitch < -35, pitch > 40, p.onGround(),
                ClientPlayerData.get().getFormId()), previous);
        if (m == null) return;
        start(m, now);
        pending = true;
        confirmBy = now + roundTripTicks(mc) + 6;
        AnimController.playClip(p.getId(), m.anim);
    }

    private static String lastMove(boolean busy) {
        if (busy) return landed ? current.id : "start";
        return lastMove;
    }

    private static void start(Move m, long now) {
        if (current != null) lastMove = landed ? current.id : "start";
        current = m;
        startedAt = now;
        landed = false;
    }

    /**
     * The server started a move for us: confirm the prediction (nothing to replay) or correct it. Returns true if the
     * clip should be played (it was not predicted, or the server picked another move).
     */
    public static boolean confirm(String moveId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return true;
        long now = mc.level.getGameTime();
        if (pending && current != null && current.id.equals(moveId)) {
            pending = false;
            return false;
        }
        pending = false;
        Move m = byId.get(moveId);
        if (m != null) start(m, now);
        else current = null;
        return true;
    }

    /** One of our blows landed (an impact we threw): the chain goes on. */
    public static void landed() {
        landed = true;
    }

    // ------------------------------------------------------------------ ki blasts

    /** The Ki Blast key released after {@code heldTicks}: a tap throws a quick blast at once. */
    public static void kiBlast(int heldTicks) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || heldTicks >= 8 || !free(mc, p) || ClientPlayerData.get().getKi() <= 0) return;
        AnimController.onEvent(new AnimEventPacket(p.getId(), AnimEventPacket.BLAST, 0));
        blastAt = mc.level.getGameTime();                                      // after: our own event must not be swallowed
    }

    /** The server's own pose event for something we already showed: swallow it. */
    public static boolean swallow(AnimEventPacket msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || msg.entityId() != mc.player.getId()) return false;
        long now = mc.level.getGameTime(), window = roundTripTicks(mc) + 8;
        if (msg.kind() == AnimEventPacket.BLAST && now - blastAt <= window) {
            blastAt = Long.MIN_VALUE / 2;
            return true;
        }
        if (msg.kind() == AnimEventPacket.DASH && now - dashAt <= window) {
            dashAt = Long.MIN_VALUE / 2;
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ dashes

    /** The Dash key: if it will be a plain dash, move now. Returns true if it predicted (the server is told). */
    public static boolean dash(float forward, float strafe) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || !free(mc, p)) return false;
        PlayerData d = ClientPlayerData.get();
        long now = mc.level.getGameTime();
        DBZConfig.Server c = DBZConfig.SERVER;
        try {
            if (d.isGuarding() || d.isOnCooldown(com.dbzenith.ki.DashHandler.COOLDOWN_ID, now)) return false;
            if (!p.getAbilities().instabuild && (d.getStamina() < c.dashStaminaCost.get() || d.getKi() < c.dashKiCost.get())) return false;
            if (forward > 0 && superDashLikely(mc, p, c.superDashRange.get())) return false;
            if (chaseLikely(mc, p)) return false;
            forward = Mth.clamp(forward, -1, 1);
            strafe = Mth.clamp(strafe, -1, 1);
            if (Math.abs(forward) < 0.01f && Math.abs(strafe) < 0.01f) forward = 1;
            float yaw = p.getYRot() * Mth.DEG_TO_RAD;
            Vec3 dir = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)).scale(forward).add(new Vec3(Mth.cos(yaw), 0, Mth.sin(yaw)).scale(strafe));
            if (d.isFlying() && p.getAbilities().flying && forward != 0) dir = dir.add(0, -Mth.sin(p.getXRot() * Mth.DEG_TO_RAD) * forward, 0);
            double speed = c.dashStrength.get() * (1.0 + d.getDerived().moveSpeed());
            Vec3 v = dir.normalize().scale(speed);
            p.setDeltaMovement(v.x, p.onGround() ? Math.max(0.15, v.y) : v.y, v.z);
            d.setCooldown(com.dbzenith.ki.DashHandler.COOLDOWN_ID, now + c.dashCooldownTicks.get());
        } catch (IllegalStateException e) {                                    // no server settings yet
            return false;
        }
        AnimController.onEvent(new AnimEventPacket(p.getId(), AnimEventPacket.DASH, 0));
        dashAt = now;
        return true;
    }

    /** Pushing forward at a foe the super dash would take (or the locked one): leave it to the server. */
    private static boolean superDashLikely(Minecraft mc, LocalPlayer p, double range) {
        if (LockOn.active()) return true;
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle();
        for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity l) || e == p || !l.isAlive()) continue;
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double dist = to.length();
            if (dist >= 4 && dist <= range && to.dot(look) / dist >= 0.94) return true;
        }
        return false;
    }

    /** Someone near is flying from a launch: the Dash key may be a chase. */
    private static boolean chaseLikely(Minecraft mc, LocalPlayer p) {
        for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
            if (e != p && e.distanceToSqr(p) < 24 * 24 && FighterStates.get(e.getId()) == Fighter.State.LAUNCHED) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ keeping it honest

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            current = null;
            pending = false;
            return;
        }
        long now = mc.level.getGameTime();
        if (pending && now > confirmBy) {                                      // the server never started it: take it back
            pending = false;
            current = null;
            lastMove = "start";
            AnimController.stop(mc.player.getId());
            return;
        }
        if (current != null && now - startedAt >= current.total()) {          // done: the chain stays open a moment
            lastMove = landed ? current.id : "start";
            int window;
            try {
                window = DBZConfig.SERVER.chainWindowTicks.get();
            } catch (IllegalStateException e) {
                window = 12;
            }
            chainUntil = now + window;
            current = null;
        }
    }

    /** The moves the client knows (for the move list). */
    public static List<Move> moves() {
        return moves;
    }

    /** For /dbz netstats on the client side and tests: moves known. */
    public static int movesKnown() {
        return moves.size();
    }
}
