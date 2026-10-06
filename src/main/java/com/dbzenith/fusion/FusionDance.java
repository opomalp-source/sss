package com.dbzenith.fusion;

import com.dbzenith.DBZenith;
import com.dbzenith.network.FusionPackets;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.registry.ModItems;
import com.dbzenith.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The two ways to fuse (12c), from the request to the moment of fusion.
 * <p>
 * <b>The Fusion Dance</b>: whoever asks stands on the left, the partner on the right, both facing the same way a few
 * steps apart. Three beats, FU-SION-HA: each dancer clicks on each beat (the HUD rings close on it). The server holds
 * them in place and judges every press; the dance always plays out, and only when the light clears does it show
 * whether all six landed (a true fusion) or not (a botched one).
 * <p>
 * <b>The Potara</b>: one earring each. The two turn to face each other and are dragged together by the ears until they
 * meet, and fuse on contact. No timing to get wrong, but it costs a pair of earrings.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FusionDance {
    /** Session kinds (the wire value of {@link FusionPackets.Show#kind}). */
    public static final int DANCE = 0, POTARA = 1;

    /** The whole dance, in ticks, and the three beats: FU, SION, HA. */
    public static final int LENGTH = 84;
    public static final int[] BEATS = {30, 47, 62};
    /** How early or late a press may come and still count (the late side allows for latency). */
    public static final int EARLY = 6, LATE = 7;
    /** Presses further before a beat than this are ignored rather than spending the beat. */
    public static final int LISTEN = 14;
    /** Distance between the two dancers' feet (blocks); the steps of the dance cover the rest in the animation. */
    public static final double SEPARATION = 2.75;

    /** The Potara: earrings on, then the pull, then contact. */
    public static final int POTARA_LENGTH = 34, POTARA_PULL = 9;

    private static final long REQUEST_TICKS = 600;

    private record Request(UUID asker, int kind, long expires) {}

    private static final Map<UUID, Request> REQUESTS = new HashMap<>();
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private FusionDance() {}

    /** A dance or Potara in progress. {@code a} asked and becomes the host; {@code b} the partner. */
    public static final class Session {
        public final ServerPlayer a, b;
        public final int kind;
        public final long start;
        public final float yaw;
        final Vec3 anchorA, anchorB;
        /** [dancer][beat]: pressed (spent the try) and hit (on time). */
        final boolean[][] pressed = new boolean[2][3], hit = new boolean[2][3];
        final boolean[] judged = new boolean[3];

        Session(ServerPlayer a, ServerPlayer b, int kind, long start, float yaw, Vec3 anchorA, Vec3 anchorB) {
            this.a = a;
            this.b = b;
            this.kind = kind;
            this.start = start;
            this.yaw = yaw;
            this.anchorA = anchorA;
            this.anchorB = anchorB;
        }

        public int tick() {
            return (int) (a.level().getGameTime() - start);
        }

        /** Two bits per dancer and beat: 0 waiting, 1 on time, 2 missed. */
        public int marks() {
            int m = 0;
            for (int who = 0; who < 2; who++) {
                for (int i = 0; i < 3; i++) {
                    int s = hit[who][i] ? 1 : pressed[who][i] || judged[i] ? 2 : 0;
                    m |= s << ((who * 3 + i) * 2);
                }
            }
            return m;
        }

        public boolean allHit() {
            for (boolean[] row : hit) for (boolean h : row) if (!h) return false;
            return true;
        }

        FusionPackets.Show packet() {
            return new FusionPackets.Show(a.getId(), b.getId(), kind, start, yaw, marks());
        }
    }

    public static Session session(ServerPlayer p) {
        return SESSIONS.get(p.getUUID());
    }

    // ------------------------------------------------------------------ asking

    /** {@code asker} invites {@code target} to the dance or the Potara: a chat button to accept. */
    public static boolean request(ServerPlayer asker, ServerPlayer target, int kind) {
        String problem = Fusion.problem(asker, target);
        if (problem == null && (SESSIONS.containsKey(asker.getUUID()) || SESSIONS.containsKey(target.getUUID()))) problem = "message.dbzenith.fusion_already";
        if (problem != null) {
            asker.displayClientMessage(Component.translatable(problem), true);
            return false;
        }
        REQUESTS.put(target.getUUID(), new Request(asker.getUUID(), kind, asker.level().getGameTime() + REQUEST_TICKS));
        String what = kind == POTARA ? "potara" : "dance";
        Component accept = Component.translatable("message.dbzenith.fusion_accept_button").withStyle(Style.EMPTY
                .withColor(ChatFormatting.GOLD).withBold(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/dbzfusion " + what)));
        target.sendSystemMessage(Component.translatable("message.dbzenith.fusion_" + what + "_request", asker.getDisplayName()).append(" ").append(accept));
        asker.displayClientMessage(Component.translatable("message.dbzenith.fusion_asked", target.getDisplayName()), true);
        return true;
    }

    /** The invited player agrees. Returns false if there is no live request of that kind. */
    public static boolean accept(ServerPlayer target, int kind) {
        Request r = REQUESTS.get(target.getUUID());
        if (r == null || r.kind != kind || target.level().getGameTime() > r.expires) return false;
        ServerPlayer asker = target.server.getPlayerList().getPlayer(r.asker);
        String problem = Fusion.problem(asker, target);
        if (problem != null) {
            target.displayClientMessage(Component.translatable(problem), true);
            return false;
        }
        if (kind == POTARA && !takeEarrings(asker)) {
            target.displayClientMessage(Component.translatable("message.dbzenith.potara_gone"), true);
            return false;
        }
        REQUESTS.remove(target.getUUID());
        return start(asker, target, kind) != null;
    }

    private static boolean takeEarrings(ServerPlayer p) {
        if (p.getAbilities().instabuild) return true;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(ModItems.POTARA_EARRINGS.get())) {
                s.shrink(1);
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ the session

    /**
     * Begins at once (no request): lines the two up for the dance, or turns them to face each other for the Potara.
     * Returns null if they cannot fuse.
     */
    public static Session start(ServerPlayer a, ServerPlayer b, int kind) {
        if (Fusion.problem(a, b) != null || SESSIONS.containsKey(a.getUUID()) || SESSIONS.containsKey(b.getUUID())) return null;
        long now = a.level().getGameTime();
        Session s;
        if (kind == DANCE) {
            float yaw = a.getYRot();
            double r = Math.toRadians(yaw);
            Vec3 right = new Vec3(-Math.cos(r), 0, -Math.sin(r));                 // a's right hand, facing yaw
            Vec3 mid = a.position().add(b.position()).scale(0.5);
            mid = new Vec3(mid.x, a.getY(), mid.z);
            Vec3 pa = mid.subtract(right.scale(SEPARATION / 2)), pb = mid.add(right.scale(SEPARATION / 2));
            s = new Session(a, b, kind, now, yaw, pa, pb);
            a.teleportTo(a.serverLevel(), pa.x, pa.y, pa.z, yaw, 0);
            b.teleportTo(a.serverLevel(), pb.x, pb.y, pb.z, yaw, 0);
            a.setDeltaMovement(Vec3.ZERO);
            b.setDeltaMovement(Vec3.ZERO);
        } else {
            Vec3 d = b.position().subtract(a.position());
            float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            s = new Session(a, b, kind, now, yaw, a.position(), b.position());
            a.teleportTo(a.serverLevel(), a.getX(), a.getY(), a.getZ(), yaw, 0);
            b.teleportTo(b.serverLevel(), b.getX(), b.getY(), b.getZ(), yaw + 180, 0);
            a.level().playSound(null, a.getX(), a.getY(), a.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2f, 1.6f);
        }
        SESSIONS.put(a.getUUID(), s);
        SESSIONS.put(b.getUUID(), s);
        broadcast(s, s.packet());
        return s;
    }

    /** A dancer clicks: judged against the nearest beat still open. */
    public static void press(ServerPlayer p) {
        Session s = SESSIONS.get(p.getUUID());
        if (s != null) press(s, p, s.tick());
    }

    /** A press at dance tick {@code t} (tests drive the clock themselves). */
    public static void press(Session s, ServerPlayer p, int t) {
        if (s.kind != DANCE) return;
        int who = p == s.a ? 0 : 1;
        for (int i = 0; i < 3; i++) {
            if (s.judged[i] || s.pressed[who][i]) continue;
            int d = t - FusionDance.BEATS[i];
            if (d < -LISTEN) return;                                              // too early to be meant for this beat
            if (d > LATE) continue;                                               // this one has passed
            s.pressed[who][i] = true;
            s.hit[who][i] = d >= -EARLY;
            if (s.hit[who][i]) p.level().playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.SKILL.get(), SoundSource.PLAYERS, 0.7f, 1.2f + i * 0.15f);
            broadcast(s, s.packet());
            return;
        }
    }

    /** Ends a session early (someone left, fell or wandered off). */
    public static void cancel(ServerPlayer p) {
        Session s = SESSIONS.get(p.getUUID());
        if (s != null) end(s);
    }

    private static void end(Session s) {
        SESSIONS.remove(s.a.getUUID());
        SESSIONS.remove(s.b.getUUID());
        s.a.setNoGravity(false);
        s.b.setNoGravity(false);
        broadcast(s, new FusionPackets.End(s.a.getId(), s.b.getId()));
    }

    private static void broadcast(Session s, Object packet) {
        ModNetwork.sendToTrackingAndSelf(s.a, packet);
        ModNetwork.sendTo(s.b, packet);                                          // a duplicate if b tracks a: the client takes it twice harmlessly
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || SESSIONS.isEmpty()) return;
        List<Session> live = new ArrayList<>(new java.util.LinkedHashSet<>(SESSIONS.values()));
        for (Session s : live) tick(s);
    }

    /** One step of a session. Public so tests can drive it. */
    public static void tick(Session s) {
        if (s.a.isRemoved() || s.b.isRemoved() || !s.a.isAlive() || !s.b.isAlive() || s.a.level() != s.b.level()
                || s.a.distanceToSqr(s.b) > 16 * 16) {
            end(s);
            return;
        }
        int t = s.tick();
        if (s.kind == DANCE) {
            hold(s.a, s.anchorA, s.yaw);
            hold(s.b, s.anchorB, s.yaw);
            boolean changed = false;
            for (int i = 0; i < 3; i++) {
                if (!s.judged[i] && t > BEATS[i] + LATE) {
                    s.judged[i] = true;
                    changed = true;
                }
            }
            if (changed) broadcast(s, s.packet());
            if (t >= LENGTH) finish(s);
        } else {
            if (t >= POTARA_PULL) {                                                // dragged together by the ears
                Vec3 mid = s.a.position().add(s.b.position()).scale(0.5);
                pull(s.a, mid);
                pull(s.b, mid);
                if (s.a.distanceToSqr(s.b) < 0.7 * 0.7 || t >= POTARA_LENGTH) finish(s);
            }
        }
    }

    private static void hold(ServerPlayer p, Vec3 at, float yaw) {
        p.setDeltaMovement(Vec3.ZERO);
        if (p.position().distanceToSqr(at) > 0.04) p.teleportTo(p.serverLevel(), at.x, at.y, at.z, yaw, p.getXRot());
    }

    private static void pull(ServerPlayer p, Vec3 mid) {
        Vec3 to = mid.subtract(p.position());
        double len = to.length();
        Vec3 v = len < 1e-3 ? Vec3.ZERO : to.scale(Math.min(0.55, 0.12 + len * 0.18) / len);
        p.setNoGravity(true);
        p.setDeltaMovement(v.x, v.y + 0.02, v.z);
        p.hurtMarked = true;                                                      // send the motion to the client
    }

    /** The light: the dance's result (every beat hit, or a botch, fat or thin), or the Potara's. */
    public static void finish(Session s) {
        int kind = s.kind == POTARA ? Fusion.POTARA : s.allHit() ? Fusion.DANCE : s.a.getRandom().nextBoolean() ? Fusion.FAILED_FAT : Fusion.FAILED_THIN;
        end(s);
        Fusion.fuse(s.a, s.b, kind);
    }
}
