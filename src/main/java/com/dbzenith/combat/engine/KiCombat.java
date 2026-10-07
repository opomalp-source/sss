package com.dbzenith.combat.engine;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.DamageCalculator;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.AnimEventPacket;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.registry.ModEffects;
import com.dbzenith.skill.KiBlastEntity;
import com.dbzenith.skill.Technique;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ki in the fight (CX-19 phase 4), on the Ki Blast key (C):
 * <ul>
 *   <li><b>tap</b>: a quick ki blast in your aura's colour, as fast as you can tap (a few ticks apart), cheap;</li>
 *   <li><b>hold</b>: charge (slowed, the focus pose); <b>release</b>: a charged blast, bigger, harder and costlier the
 *       longer you held (up to its full charge), stunning and knocking back.</li>
 * </ul>
 * Both come from data ({@code data/<ns>/combat/ki/rapid_blast.json}, {@code charged_blast.json}). The same reload also
 * reads {@code combat/techniques/<technique>.json}: a technique's <b>tier</b> (basic, super, ultimate), the special
 * meter it costs and whether it plays the cinematic (see {@link Tier}).
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KiCombat {
    /** One kind of blast, from data. */
    public record Blast(double costPercent, double costPercentMax, double damage, double damageMax, float size, float sizeMax, float speed,
                        float explosion, float explosionMax, int life, int cooldown, int chargeTicks, int minCharge, int hitstun, int hitstunMax,
                        double knockback, double knockbackMax, boolean homing, float spread, double lockHoming) {
        static Blast parse(JsonObject j) {
            return new Blast(GsonHelper.getAsDouble(j, "cost_percent", 1.5), GsonHelper.getAsDouble(j, "cost_percent_max", GsonHelper.getAsDouble(j, "cost_percent", 1.5)),
                    GsonHelper.getAsDouble(j, "damage", 0.35), GsonHelper.getAsDouble(j, "damage_max", GsonHelper.getAsDouble(j, "damage", 0.35)),
                    GsonHelper.getAsFloat(j, "size", 0.35f), GsonHelper.getAsFloat(j, "size_max", GsonHelper.getAsFloat(j, "size", 0.35f)),
                    GsonHelper.getAsFloat(j, "speed", 2.0f), GsonHelper.getAsFloat(j, "explosion", 0f), GsonHelper.getAsFloat(j, "explosion_max", GsonHelper.getAsFloat(j, "explosion", 0f)),
                    GsonHelper.getAsInt(j, "life", 40), GsonHelper.getAsInt(j, "cooldown", 3), GsonHelper.getAsInt(j, "charge_ticks", 1),
                    GsonHelper.getAsInt(j, "min_charge", 0), GsonHelper.getAsInt(j, "hitstun", 5), GsonHelper.getAsInt(j, "hitstun_max", GsonHelper.getAsInt(j, "hitstun", 5)),
                    GsonHelper.getAsDouble(j, "knockback", 0.1), GsonHelper.getAsDouble(j, "knockback_max", GsonHelper.getAsDouble(j, "knockback", 0.1)),
                    GsonHelper.getAsBoolean(j, "homing", false), GsonHelper.getAsFloat(j, "spread", 0f),
                    GsonHelper.getAsDouble(j, "lock_homing", 0.0));
        }
    }

    /** What tier a technique is, and what it costs from the special meter (0 for basic). */
    public record Tier(String tier, double meter, boolean cinematic) {
        public static final Tier BASIC = new Tier("basic", 0, false);
    }

    private static Blast rapid, charged;
    private static Map<String, Tier> tiers = new HashMap<>();
    private static final Map<UUID, Long> CHARGING = new HashMap<>();
    private static final Map<UUID, Boolean> ANNOUNCED = new HashMap<>();

    private KiCombat() {}

    public static Tier tier(String techniqueId) {
        return tiers.getOrDefault(techniqueId, Tier.BASIC);
    }

    public static Map<String, Tier> tiers() {
        return tiers;
    }

    // ------------------------------------------------------------------ data

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), "combat/ki") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller profiler) {
                Blast r = null, c = null;
                for (Map.Entry<ResourceLocation, JsonElement> e : files.entrySet()) {
                    String id = e.getKey().getPath();
                    if (id.equals("rapid_blast")) r = Blast.parse(e.getValue().getAsJsonObject());
                    if (id.equals("charged_blast")) c = Blast.parse(e.getValue().getAsJsonObject());
                }
                rapid = r;
                charged = c;
            }
        });
        event.addListener(new SimpleJsonResourceReloadListener(new Gson(), "combat/techniques") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller profiler) {
                Map<String, Tier> out = new HashMap<>();
                for (Map.Entry<ResourceLocation, JsonElement> e : files.entrySet()) {
                    JsonObject j = e.getValue().getAsJsonObject();
                    String tier = GsonHelper.getAsString(j, "tier", "basic");
                    double meter = GsonHelper.getAsDouble(j, "meter", tier.equals("ultimate") ? 300 : tier.equals("super") ? 100 : 0);
                    out.put(e.getKey().getPath(), new Tier(tier, meter, GsonHelper.getAsBoolean(j, "cinematic", tier.equals("ultimate"))));
                }
                tiers = out;
                com.mojang.logging.LogUtils.getLogger().info("Combat engine: {} technique tiers", out.size());
            }
        });
    }

    /** Every client learns the meter costs on joining and after a reload. */
    @SubscribeEvent
    public static void onDatapackSync(net.minecraftforge.event.OnDatapackSyncEvent event) {
        Map<String, Double> costs = new HashMap<>();
        tiers.forEach((id, t) -> costs.put(id, t.meter()));
        var packet = new com.dbzenith.network.TechniqueTiersPacket(costs);
        if (event.getPlayer() != null) ModNetwork.sendTo(event.getPlayer(), packet);
        else for (ServerPlayer p : event.getPlayerList().getPlayers()) ModNetwork.sendTo(p, packet);
        var moves = new com.dbzenith.network.MovesSyncPacket(Moves.sources());                   // and the moves, for prediction (phase 7)
        if (event.getPlayer() != null) ModNetwork.sendTo(event.getPlayer(), moves);
        else for (ServerPlayer p : event.getPlayerList().getPlayers()) ModNetwork.sendTo(p, moves);
    }

    // ------------------------------------------------------------------ the key

    /** The Ki Blast key went down or up. */
    public static void key(ServerPlayer p, boolean down) {
        long now = p.level().getGameTime();
        if (com.dbzenith.skill.BeamStruggle.isStruggling(p)) {                  // in a beam clash: the key is a surge
            if (down) com.dbzenith.skill.BeamStruggle.surge(p);
            CHARGING.remove(p.getUUID());
            return;
        }
        if (down) {
            CHARGING.put(p.getUUID(), now);
            ANNOUNCED.remove(p.getUUID());
            return;
        }
        Long start = CHARGING.remove(p.getUUID());
        boolean announced = ANNOUNCED.remove(p.getUUID()) != null;
        if (start == null) return;
        long held = now - start;
        if (charged != null && held >= charged.minCharge) fire(p, charged, Math.min(1.0, (double) (held - charged.minCharge) / Math.max(1, charged.chargeTicks)), true);
        else if (rapid != null && !announced) fire(p, rapid, 0, false);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || CHARGING.isEmpty() || charged == null) return;
        var server = event.getServer();
        for (Map.Entry<UUID, Long> e : CHARGING.entrySet()) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null) continue;
            long held = p.level().getGameTime() - e.getValue();
            if (held >= charged.minCharge) {
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 2, false, false, false));
                if (ANNOUNCED.put(e.getKey(), true) == null) {                     // the charge begins: the focus pose and hum
                    ModNetwork.sendToTrackingAndSelf(p, new AnimEventPacket(p.getId(), AnimEventPacket.FOCUS, 0));
                    p.level().playSound(null, p.getX(), p.getY(), p.getZ(), com.dbzenith.registry.ModSounds.AURA_CHARGE.get(), SoundSource.PLAYERS, 0.6f, 1.4f);
                }
            }
        }
        CHARGING.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
    }

    /** Fires a blast; {@code t} is the charge, 0..1. */
    static boolean fire(ServerPlayer p, Blast b, double t, boolean isCharged) {
        PlayerData d = ModCapabilities.get(p).orElse(null);
        if (d == null || !p.isAlive() || p.isSpectator() || !CombatEngine.mayAct(p)) return false;
        if (ModEffects.isStunned(p) || ModEffects.isKiSealed(p)) return false;
        long now = p.level().getGameTime();
        String cd = isCharged ? "ki_charged" : "ki_rapid";
        if (d.isOnCooldown(cd, now)) return false;
        d.recomputeIfStale();
        double cost = d.getDerived().maxKi() * lerp(b.costPercent, b.costPercentMax, t) / 100.0;
        if (!p.getAbilities().instabuild) {
            if (d.getKi() < cost) {
                p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.dbzenith.no_ki"), true);
                return false;
            }
            d.setKi(d.getKi() - cost);
        }
        d.setCooldown(cd, now + b.cooldown);
        Technique shape = Technique.builder(isCharged ? "ki:charged_blast" : "ki:rapid_blast")
                .speed(b.speed).size((float) lerp(b.size, b.sizeMax, t)).explosion((float) lerp(b.explosion, b.explosionMax, t))
                .life(b.life).build();
        double damage = DamageCalculator.kiOutgoing(d, lerp(b.damage, b.damageMax, t));
        ServerLevel level = p.serverLevel();
        Vec3 look = p.getLookAngle();
        if (b.spread > 0) {
            float yaw = (p.getRandom().nextFloat() - 0.5f) * 2 * b.spread * net.minecraft.util.Mth.DEG_TO_RAD;
            float pitch = (p.getRandom().nextFloat() - 0.5f) * 2 * b.spread * net.minecraft.util.Mth.DEG_TO_RAD;
            look = look.yRot(yaw).xRot(pitch);
        }
        KiBlastEntity blast = KiBlastEntity.create(level, p, shape, damage);
        blast.setColor(com.dbzenith.ki.Aura.color(d));
        blast.setCombat((int) Math.round(lerp(b.hitstun, b.hitstunMax, t)), lerp(b.knockback, b.knockbackMax, t), isCharged);
        Vec3 start = p.getEyePosition().add(look.scale(0.6)).subtract(0, shape.size() / 2.0, 0);
        blast.moveTo(start.x, start.y, start.z, p.getYRot(), p.getXRot());
        blast.setDeltaMovement(look.normalize().scale(b.speed));
        if (b.homing) blast.setHomingTarget(Evasion.superDashTarget(p));
        else if (b.lockHoming > 0 && Targeting.target(p) != null) blast.setHomingTarget(Targeting.target(p), b.lockHoming);   // curves toward the locked foe
        level.addFreshEntity(blast);
        ModNetwork.sendToTrackingAndSelf(p, new AnimEventPacket(p.getId(), AnimEventPacket.BLAST, 0));
        level.playSound(null, p.getX(), p.getY(), p.getZ(), com.dbzenith.registry.ModSounds.KI_FIRE.get(), SoundSource.PLAYERS,
                isCharged ? 0.9f : 0.45f, isCharged ? 0.8f : 1.5f + p.getRandom().nextFloat() * 0.3f);
        return true;
    }

    static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** Dev and tests: fire a blast as if from the key ({@code charge} 0..1, or -1 for a quick one). */
    public static boolean fireForTest(ServerPlayer p, double charge) {
        return charge < 0 ? rapid != null && fire(p, rapid, 0, false) : charged != null && fire(p, charged, charge, true);
    }

    public static boolean loaded() {
        return rapid != null && charged != null;
    }
}
