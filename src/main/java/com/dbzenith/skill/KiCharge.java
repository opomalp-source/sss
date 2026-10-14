package com.dbzenith.skill;

import com.dbzenith.DBZenith;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.KiChargePackets;
import com.dbzenith.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Charging techniques (CX-23, user: "you can charge them when you hold the button, for some attacks up to 30 seconds or
 * more; the more you charge, the bigger and stronger, but it costs more ki").
 * <ul>
 *   <li>Pressing the technique key starts a charge; letting go fires it. A tap (under {@code tapTicks}) fires at once,
 *       uncharged, as before.</li>
 *   <li>The charge grows to the technique's longest ({@link Technique#chargeTicks()}; ultimates half as long again):
 *       up to its {@code chargePower} times the damage and bigger (TechniqueHandler.chargeDamage, chargeScale).</li>
 *   <li>While charging, ki drains (a share of the technique's cost a second, rising as it grows) and you move slowly.
 *       If the ki would fall below what firing costs, it goes off on its own. Stunned, sealed or locked in a struggle,
 *       the charge fizzles.</li>
 *   <li>Everyone near sees it: the clients get the charge as it grows (the energy at the hands, the pose, the gauge).</li>
 * </ul>
 * Techniques that go off at once (self techniques, mines, the Spirit Bomb, which gathers its own way) still do.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KiCharge {
    /** How the energy is held while charging (the pose and where the light gathers). */
    public static final int KIND_BLAST = 0, KIND_BEAM = 1, KIND_OVERHEAD = 2;

    static final class State {
        final Technique technique;
        final int kind, color, max;
        boolean bypass;                                  // the admin path (/dbz charge): no deck or meter
        int ticks;

        State(Technique technique, int kind, int color, int max) {
            this.technique = technique;
            this.kind = kind;
            this.color = color;
            this.max = max;
        }

        double fraction() {
            return Math.min(1, ticks / (double) Math.max(1, max));
        }
    }

    private static final Map<UUID, State> CHARGING = new ConcurrentHashMap<>();

    private KiCharge() {}

    public static boolean isCharging(ServerPlayer p) {
        return CHARGING.containsKey(p.getUUID());
    }

    /** The charge so far (0..1), or -1 when not charging. */
    public static double fraction(ServerPlayer p) {
        State s = CHARGING.get(p.getUUID());
        return s == null ? -1 : s.fraction();
    }

    public static int kindOf(Technique t) {
        if (t.style() == Technique.Style.BEAM) return KIND_BEAM;
        if (t.holdTicks() > 0 || t.size() >= 1.4f || "ultimate".equals(com.dbzenith.combat.engine.KiCombat.tier(t.id()).tier())) return KIND_OVERHEAD;
        return KIND_BLAST;
    }

    /** The longest charge for a technique: its own, half as long again for an ultimate. */
    public static int maxTicks(Technique t) {
        boolean ultimate = "ultimate".equals(com.dbzenith.combat.engine.KiCombat.tier(t.id()).tier());
        return (int) Math.round(t.chargeTicks() * (ultimate ? 1.5 : 1));
    }

    /** The technique key went down. */
    public static void press(ServerPlayer p, String techniqueId) {
        press(p, techniqueId, false);
    }

    /** The same; {@code bypass} skips the deck and the meter (/dbz charge). */
    public static void press(ServerPlayer p, String techniqueId, boolean bypass) {
        PlayerData d = ModCapabilities.get(p).orElse(null);
        if (d == null || CHARGING.containsKey(p.getUUID())) return;
        Technique t = bypass ? Techniques.byId(techniqueId) : Techniques.resolve(d, techniqueId);
        if (t == null) return;
        if (!DBZConfig.SERVER.techCharge.get() || t.chargeTicks() <= 0) {               // goes off at once
            report(p, TechniqueHandler.use(p, t, bypass, 0), t);
            return;
        }
        TechniqueHandler.Result r = TechniqueHandler.check(p, t, bypass);
        if (r != TechniqueHandler.Result.FIRED) {
            report(p, r, t);
            return;
        }
        int color = t.color() == 0xFFFFFF ? com.dbzenith.ki.Aura.color(d) : t.color();
        State s = new State(t, kindOf(t), color, maxTicks(t));
        s.bypass = bypass;
        CHARGING.put(p.getUUID(), s);
        broadcast(p, s);
    }

    /** The technique key came up: fire what has been gathered. */
    public static void release(ServerPlayer p) {
        State s = CHARGING.remove(p.getUUID());
        if (s == null) return;
        stopped(p);
        double f = s.ticks < DBZConfig.SERVER.techTapTicks.get() ? 0 : s.fraction();
        report(p, TechniqueHandler.use(p, s.technique, s.bypass, f), s.technique);
    }

    /** Lets a charge go to waste (stunned, sealed...). */
    public static void fizzle(ServerPlayer p, boolean tell) {
        if (CHARGING.remove(p.getUUID()) == null) return;
        stopped(p);
        if (tell) p.displayClientMessage(Component.translatable("message.dbzenith.charge_fizzled").withStyle(ChatFormatting.GRAY), true);
    }

    /** Every tick from KiTicker: the charge grows, ki drains, and it goes off on its own if the ki runs short. */
    public static void tick(ServerPlayer p, PlayerData d, long now) {
        State s = CHARGING.get(p.getUUID());
        if (s == null) return;
        if (!p.isAlive() || com.dbzenith.registry.ModEffects.isStunned(p) || com.dbzenith.registry.ModEffects.isKiSealed(p) || BeamStruggle.isStruggling(p)) {
            fizzle(p, p.isAlive());
            return;
        }
        s.ticks++;
        int tap = DBZConfig.SERVER.techTapTicks.get();
        if (s.ticks < tap) return;
        double f = s.fraction();
        double base = TechniqueHandler.baseCost(d, s.technique);
        double drain = base * DBZConfig.SERVER.techChargeCost.get() / 20.0 * (1 + f);
        if (!p.getAbilities().instabuild) {
            if (d.getKi() - drain < base) {                                          // nothing more to give: it goes off
                release(p);
                return;
            }
            d.setKi(d.getKi() - drain);
        }
        if (locksMovement(s.technique) && DBZConfig.SERVER.chargeLockMovement.get()) lock(p, true);   // planted for the big ones (the ki attack rework)
        else p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, f < 0.5 ? 1 : 2, false, false, false));   // a slow walk for the small ones
        if (s.ticks % 4 == 0) broadcast(p, s);
        // the charge's sound is the client's rising loop now (the ki attack rework, phase 2)
    }

    private static void broadcast(ServerPlayer p, State s) {
        ModNetwork.sendToTrackingAndSelf(p, new KiChargePackets.State(p.getId(), s.kind, s.color, (float) s.fraction(),
                (float) TechniqueHandler.chargeDamage(s.technique, s.fraction()), s.max, s.technique.id()));
    }

    private static void stopped(ServerPlayer p) {
        lock(p, false);
        ModNetwork.sendToTrackingAndSelf(p, new KiChargePackets.State(p.getId(), 0, 0, -1f, 1f, 0, ""));
    }

    private static void report(ServerPlayer p, TechniqueHandler.Result r, Technique t) {
        if (r == TechniqueHandler.Result.STUNNED) p.displayClientMessage(Component.translatable("message.dbzenith.stunned"), true);
        if (r == TechniqueHandler.Result.SEALED) p.displayClientMessage(Component.translatable("message.dbzenith.ki_sealed"), true);
        if (r == TechniqueHandler.Result.NOT_ENOUGH_KI) p.displayClientMessage(Component.translatable("message.dbzenith.charge_no_ki"), true);
        if (r == TechniqueHandler.Result.NO_METER) p.displayClientMessage(Component.translatable("message.dbzenith.no_meter",
                (int) Math.ceil(com.dbzenith.combat.engine.KiCombat.tier(t.id()).meter() / com.dbzenith.combat.engine.SpecialMeter.BAR)).withStyle(ChatFormatting.YELLOW), true);
    }


    // ------------------------------------------------------------------ the ki attack rework, phase 2: planted, and broken by a heavy blow

    private static final java.util.UUID PLANTED = java.util.UUID.fromString("c55d7a10-3e1b-4c5a-9e1d-0c55ab7e0001");

    /** Charging this, you stand your ground (turning only): beams, the big spheres, anything thrown from overhead. */
    public static boolean locksMovement(Technique t) {
        return kindOf(t) != KIND_BLAST;
    }

    private static void lock(ServerPlayer p, boolean on) {
        var speed = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        boolean has = speed.getModifier(PLANTED) != null;
        if (on && !has) speed.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(PLANTED, "dbzenith charging",
                -1.0, net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.MULTIPLY_TOTAL));
        else if (!on && has) speed.removeModifier(PLANTED);
    }

    /** A heavy blow (a tenth of your health or more at once) breaks your concentration: the charge is lost. */
    @SubscribeEvent
    public static void onHurt(net.minecraftforge.event.entity.living.LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !CHARGING.containsKey(p.getUUID())) return;
        if (event.getAmount() >= p.getMaxHealth() * 0.1f) {
            fizzle(p, false);
            p.displayClientMessage(Component.translatable("message.dbzenith.charge_broken").withStyle(ChatFormatting.RED), true);
        }
    }

    /** Is this player charging something that plants them? (Both sides: the client stops its own jumps.) */
    public static boolean planted(net.minecraft.world.entity.player.Player p) {
        var speed = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        return speed != null && speed.getModifier(PLANTED) != null;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CHARGING.remove(event.getEntity().getUUID());
    }
}
