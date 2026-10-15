package com.dbzenith.combat;

import com.dbzenith.DBZenith;
import com.dbzenith.network.GripPacket;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.npc.KiFighter;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gripping: finishing off the downed (the user: "when someone is knocked the only way to kill them, except when their
 * timer runs out, is to grip them with a button; there should be various gripping animations"). Blows no longer kill
 * someone who is down (they shorten their time); only a grip does.
 * <ul>
 *   <li>Press the grip key (F) next to a downed fighter. The direction you hold picks the finisher
 *       ({@link Finisher#forInput}); NPC fighters pick one at random.</li>
 *   <li>Both of you are held in it. The victim is placed by the finisher's own timeline ({@link #victimOffset}), on the
 *       server and on every screen alike, and dies on its killing frame.</li>
 *   <li>Anyone hurting the gripper hard enough (6% of their health in one blow), stunning them or knocking them down
 *       breaks the grip, and the downed fighter lives on.</li>
 *   <li>NPC fighters walk up to a downed player they were fighting and grip them, so allies have a moment to step in.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID)
public final class Grip {
    /** The finishers: their length and the frame that kills, in ticks. */
    public enum Finisher {
        KI_EXECUTION(54, 38),      // hauled up by the collar, a ki blast point-blank to the chest
        SKULL_SLAM(50, 36),        // lifted high by the head and slammed into the ground
        STOMP(36, 22),             // a stomp to the chest that cracks the ground
        BEAR_HUG(62, 46),          // lifted into a crushing hug
        ELBOW_DROP(46, 32),        // a leap and an elbow driven down from above
        KI_SPEAR(44, 28);          // a hand of ki driven clean through

        public final int length, killAt;

        Finisher(int length, int killAt) {
            this.length = length;
            this.killAt = killAt;
        }

        public String key() {
            return name().toLowerCase();
        }

        /** The direction held when the key went down: none or forward, left, right, back, jumping, sneaking. */
        public static Finisher forInput(int input) {
            return switch (input) {
                case 1 -> SKULL_SLAM;
                case 2 -> BEAR_HUG;
                case 3 -> STOMP;
                case 4 -> ELBOW_DROP;
                case 5 -> KI_SPEAR;
                default -> KI_EXECUTION;
            };
        }
    }

    /** Within this many blocks a grip can start. */
    public static final double REACH = 3.0;
    private static final UUID HOLD = UUID.fromString("c51d7a10-3e1b-4c5a-9e1d-0c51ab7e0003");

    private static final class Session {
        final LivingEntity gripper, victim;
        final Finisher finisher;
        final long start;
        final float yaw;
        boolean killed;
        float lastHealth;

        Session(LivingEntity gripper, LivingEntity victim, Finisher finisher, long start, float yaw) {
            this.gripper = gripper;
            this.victim = victim;
            this.finisher = finisher;
            this.start = start;
            this.yaw = yaw;
        }
    }

    private static final List<Session> SESSIONS = new ArrayList<>();
    /** NPCs closing in on a downed player: since when (ticks), so they pause a moment before they grip. */
    private static final Map<LivingEntity, Long> CLOSING = new HashMap<>();

    private Grip() {}

    public static boolean isGripping(LivingEntity e) {
        for (Session s : SESSIONS) if (s.gripper == e) return true;
        return false;
    }

    public static boolean isGripped(LivingEntity e) {
        for (Session s : SESSIONS) if (s.victim == e) return true;
        return false;
    }

    /**
     * Where the victim is during a finisher, {@code age} ticks in: (side, up, forward) from the gripper's feet, in the
     * gripper's frame. Shared by the server and the clients so they agree.
     */
    public static Vec3 victimOffset(Finisher f, float age) {
        return switch (f) {
            case KI_EXECUTION -> age < 10 ? new Vec3(0, 0, 1.0) : age < 20 ? lerp(new Vec3(0, 0, 1.0), new Vec3(0, 0.3, 0.62), (age - 10) / 10f) : new Vec3(0, 0.3, 0.62);
            case SKULL_SLAM -> age < 10 ? new Vec3(0, 0, 1.0)
                    : age < 24 ? lerp(new Vec3(0, 0, 1.0), new Vec3(0, 1.05, 0.45), ease((age - 10) / 14f))
                    : age < 34 ? lerp(new Vec3(0, 1.05, 0.45), new Vec3(0, 0, 1.25), ease((age - 24) / 10f)) : new Vec3(0, 0, 1.25);
            case STOMP -> new Vec3(0, 0, 0.85);
            case BEAR_HUG -> age < 10 ? new Vec3(0, 0, 0.9) : age < 20 ? lerp(new Vec3(0, 0, 0.9), new Vec3(0, 0.35, 0.42), ease((age - 10) / 10f)) : new Vec3(0, 0.35, 0.42);
            case ELBOW_DROP -> new Vec3(0, 0, 1.2);
            case KI_SPEAR -> age < 8 ? new Vec3(0, 0, 0.95) : age < 16 ? lerp(new Vec3(0, 0, 0.95), new Vec3(0, 0.12, 0.66), ease((age - 8) / 8f)) : new Vec3(0, 0.12, 0.66);
        };
    }

    /** Whether the victim is held upright (facing the gripper) at this point of a finisher, rather than lying. */
    public static boolean victimUpright(Finisher f, float age) {
        return switch (f) {
            case KI_EXECUTION -> age >= 14;
            case SKULL_SLAM -> age >= 16 && age < 30;
            case BEAR_HUG -> age >= 14;
            case KI_SPEAR -> age >= 12;
            default -> false;
        };
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, float k) {
        k = Math.max(0, Math.min(1, k));
        return a.add(b.subtract(a).scale(k));
    }

    private static float ease(float k) {
        k = Math.max(0, Math.min(1, k));
        return k * k * (3 - 2 * k);
    }

    /** The victim's feet position for a finisher {@code age} ticks in, the gripper at (x, y, z) facing {@code yaw}. */
    public static Vec3 victimAt(Finisher f, float age, double x, double y, double z, float yaw) {
        Vec3 o = victimOffset(f, age);
        double r = Math.toRadians(yaw);
        double fx = -Math.sin(r), fz = Math.cos(r), rx = -Math.cos(r), rz = -Math.sin(r);
        return new Vec3(x + fx * o.z + rx * o.x, y + o.y, z + fz * o.z + rz * o.x);
    }

    // ------------------------------------------------------------------ starting, ending

    /** Starts a finisher; false if it can't be done (out of reach, not down, already held...). */
    public static boolean start(LivingEntity gripper, LivingEntity victim, Finisher f) {
        if (gripper == victim || !KnockOut.isDown(victim) || KnockOut.isDown(gripper) || isGripping(gripper) || isGripped(victim)) return false;
        if (Carry.isCarrying(gripper) || com.dbzenith.registry.ModEffects.isStunned(gripper)) return false;
        if (gripper.distanceTo(victim) > REACH || !gripper.hasLineOfSight(victim)) return false;
        Carry.release(victim);
        Vec3 to = victim.position().subtract(gripper.position());
        float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
        gripper.setYRot(yaw);
        gripper.setYHeadRot(yaw);
        gripper.yBodyRot = yaw;
        Session s = new Session(gripper, victim, f, gripper.level().getGameTime(), yaw);
        s.lastHealth = gripper.getHealth();
        SESSIONS.add(s);
        hold(gripper, true);
        victim.setNoGravity(true);
        if (gripper instanceof ServerPlayer p) p.connection.teleport(p.getX(), p.getY(), p.getZ(), yaw, p.getXRot());
        place(s, 0);
        broadcast(s, GripPacket.START);
        if (victim instanceof Player p) p.displayClientMessage(Component.translatable("message.dbzenith.being_gripped", gripper.getDisplayName()), true);
        return true;
    }

    private static void end(Session s, boolean broken) {
        SESSIONS.remove(s);
        hold(s.gripper, false);
        if (s.victim.isAlive()) s.victim.setNoGravity(false);
        broadcast(s, broken ? GripPacket.BROKEN : GripPacket.DONE);
        if (broken) {
            if (s.gripper instanceof Player p) p.displayClientMessage(Component.translatable("message.dbzenith.grip_broken"), true);
            if (s.victim instanceof Player p) p.displayClientMessage(Component.translatable("message.dbzenith.grip_escaped"), true);
        }
    }

    /** Breaks every grip this fighter is in (hurt, stunned, knocked down, gone). */
    public static void breakFor(LivingEntity e) {
        for (Session s : new ArrayList<>(SESSIONS)) if ((s.gripper == e || s.victim == e) && !s.killed) end(s, true);
    }

    private static void hold(LivingEntity e, boolean on) {
        AttributeInstance speed = e.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        boolean has = speed.getModifier(HOLD) != null;
        if (on && !has) speed.addTransientModifier(new AttributeModifier(HOLD, "dbzenith gripping", -1.0, AttributeModifier.Operation.MULTIPLY_TOTAL));
        else if (!on && has) speed.removeModifier(HOLD);
    }

    private static void place(Session s, float age) {
        LivingEntity g = s.gripper, v = s.victim;
        Vec3 at = victimAt(s.finisher, age, g.getX(), g.getY(), g.getZ(), s.yaw);
        v.setPos(at.x, at.y, at.z);
        float face = victimUpright(s.finisher, age) ? s.yaw + 180 : s.yaw + 90;   // upright facing the gripper, or lying across in front
        v.setYRot(face);
        v.yBodyRot = face;
        v.setYHeadRot(face);
        v.setDeltaMovement(Vec3.ZERO);
        v.fallDistance = 0;
        g.setDeltaMovement(0, Math.min(0, g.getDeltaMovement().y), 0);
    }

    // ------------------------------------------------------------------ every tick

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        for (Session s : new ArrayList<>(SESSIONS)) {
            LivingEntity g = s.gripper, v = s.victim;
            long age = g.level().getGameTime() - s.start;
            if (g.isRemoved() || !g.isAlive() || g.level() != v.level() || KnockOut.isDown(g) || com.dbzenith.registry.ModEffects.isStunned(g)) {
                if (!s.killed) end(s, true);
                else SESSIONS.remove(s);
                continue;
            }
            if (!s.killed && (v.isRemoved() || !v.isAlive() || !KnockOut.isDown(v))) {   // saved or gone mid-grip
                end(s, true);
                continue;
            }
            float hp = g.getHealth();                                           // hurt hard (by any means) in the act: the hold breaks
            if (!s.killed && s.lastHealth - hp >= g.getMaxHealth() * 0.06f) {
                end(s, true);
                continue;
            }
            s.lastHealth = hp;
            if (!s.killed) place(s, age);
            if (!s.killed && age >= s.finisher.killAt) {
                s.killed = true;
                v.setNoGravity(false);
                kill(s);
            }
            if (age >= s.finisher.length) {
                SESSIONS.remove(s);
                hold(g, false);
                broadcast(s, GripPacket.DONE);
            }
        }
        if (g().getGameTime() % 10 == 0) npcs();
    }

    private static net.minecraft.server.level.ServerLevel g() {
        return net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().overworld();
    }

    /** The killing frame: the victim dies by the gripper's hand. */
    private static void kill(Session s) {
        LivingEntity g = s.gripper, v = s.victim;
        if (v.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            Vec3 at = v.position().add(0, v.getBbHeight() * 0.5, 0);
            int kind = switch (s.finisher) {
                case KI_EXECUTION, KI_SPEAR -> com.dbzenith.network.ImpactPacket.KI_HIT;
                default -> com.dbzenith.network.ImpactPacket.HEAVY;
            };
            com.dbzenith.network.ImpactPacket.at(at, at.subtract(g.position().add(0, 1, 0)).normalize(), kind, 2.2f,
                    g instanceof Player p ? com.dbzenith.data.ModCapabilities.get(p).map(com.dbzenith.ki.Aura::color).orElse(0xFFFFFF) : 0xFFFFFF, g.getId()).send(sl);
        }
        broadcast(s, GripPacket.KILL);
        KnockOut.finish(v, ModDamageTypes.finished(v.level(), g));
        if (g instanceof Player p) p.displayClientMessage(Component.translatable("message.dbzenith.finished", v.getDisplayName()), true);
    }

    /** A hard blow to the gripper breaks their hold. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDamage(LivingDamageEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide || !isGripping(e)) return;
        if (event.getAmount() >= e.getMaxHealth() * 0.06f) breakFor(e);
    }

    // ------------------------------------------------------------------ NPCs finish what they started

    private static void npcs() {
        for (LivingEntity v : KnockOut.downed()) {
            if (!(v instanceof Player) || isGripped(v) || Carry.isCarried(v)) continue;
            for (Mob m : v.level().getEntitiesOfClass(Mob.class, v.getBoundingBox().inflate(16),
                    m -> m instanceof KiFighter && m.isAlive() && !KnockOut.isDown(m) && !isGripping(m) && !Carry.isCarrying(m)
                            && (m.getTarget() == v || v.getLastHurtByMob() == m))) {
                if (m.distanceTo(v) > 2.4) {
                    m.getNavigation().moveTo(v, 1.0);
                    CLOSING.remove(m);
                } else {
                    long now = m.level().getGameTime();
                    long since = CLOSING.computeIfAbsent(m, k -> now);
                    if (now - since >= 60) {                                    // a pause: time for an ally to step in
                        CLOSING.remove(m);
                        Finisher[] all = Finisher.values();
                        start(m, v, all[m.getRandom().nextInt(all.length)]);
                    }
                }
                break;                                                           // one at a time
            }
        }
        CLOSING.keySet().removeIf(m -> m.isRemoved() || !m.isAlive());
    }

    // ------------------------------------------------------------------ telling clients

    private static void broadcast(Session s, int phase) {
        GripPacket m = new GripPacket(s.gripper.getId(), s.victim.getId(), s.finisher.ordinal(), phase);
        ModNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> s.gripper), m);
        if (s.victim instanceof ServerPlayer p) ModNetwork.sendTo(p, m);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        breakFor(event.getEntity());
    }

    @SubscribeEvent
    public static void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        SESSIONS.clear();
        CLOSING.clear();
    }
}
