package com.dbzenith.race;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.BodyHealth;
import com.dbzenith.combat.DamageCalculator;
import com.dbzenith.combat.ModDamageTypes;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.ImpactPacket;
import com.dbzenith.race.RacialSkill.Stat;
import com.dbzenith.skill.TechniqueEffects;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;

/**
 * What racial skills do: the Racial key (actives), the live conditions passives read, timed buffs and the
 * passives that are more than a modifier (Second Wind, Death Regeneration, Reincarnation, Limb Regeneration...).
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID)
public final class RacialSkillEffects {
    /** Once-in-a-while passives: Second Wind every 10 minutes, Death Regeneration 10, Reincarnation 30. */
    static final long SECOND_WIND_COOLDOWN = 12000, DEATH_REGEN_COOLDOWN = 12000, REINCARNATION_COOLDOWN = 36000;

    private RacialSkillEffects() {}

    // ------------------------------------------------------------------ the Racial key

    /** Fire the selected active racial skill. Returns whether it went off. */
    public static boolean use(ServerPlayer player) {
        com.dbzenith.combat.PvpRules.actor(player);                         // effects it causes on players are judged (CX-19)
        PlayerData d = ModCapabilities.get(player).orElse(null);
        if (d == null || !player.isAlive()) return false;
        RacialSkill s = RacialSkills.byId(d.getRacialSelected());
        if (s == null || !s.isActive() || !s.fits(d.getRace(), d.getVariant())) {
            s = RacialSkills.forCharacter(d.getRace(), d.getVariant()).stream()
                    .filter(x -> x.isActive() && RacialSkills.unlocked(d, x)).findFirst().orElse(null);
            if (s == null) {
                player.displayClientMessage(Component.translatable("message.dbzenith.racial_none"), true);
                return false;
            }
            d.setRacialSelected(s.id());
        }
        return use(player, d, s);
    }

    /** The Skill key: fire the selected universal skill (Kaioken goes up a stage). */
    public static boolean useSkill(ServerPlayer player) {
        PlayerData d = ModCapabilities.get(player).orElse(null);
        if (d == null || !player.isAlive()) return false;
        RacialSkill s = RacialSkills.byId(d.getSkillSelected());
        if (s == null || !s.isActive() || !RacialSkills.unlocked(d, s)) {
            s = RacialSkills.universal().stream().filter(x -> x.isActive() && RacialSkills.unlocked(d, x)).findFirst().orElse(null);
            if (s == null) {
                player.displayClientMessage(Component.translatable("message.dbzenith.skill_none"), true);
                return false;
            }
            d.setSkillSelected(s.id());
        }
        if (s.id().equals("kaioken")) return com.dbzenith.transform.Kaioken.raise(player, d);
        return use(player, d, s);
    }

    /** Instant Transmission's destination, set by the target picker just before the skill fires. */
    private static final java.util.Map<java.util.UUID, Vec3> TRANSMISSION = new java.util.HashMap<>();

    public static boolean transmit(ServerPlayer player, Vec3 destination) {
        PlayerData d = ModCapabilities.get(player).orElse(null);
        RacialSkill s = RacialSkills.byId("instant_transmission");
        if (d == null || s == null) return false;
        TRANSMISSION.put(player.getUUID(), destination);
        try {
            return use(player, d, s);
        } finally {
            TRANSMISSION.remove(player.getUUID());
        }
    }

    public static boolean use(ServerPlayer player, PlayerData d, RacialSkill s) {
        long now = player.level().getGameTime();
        if (!RacialSkills.unlocked(d, s)) {
            player.displayClientMessage(Component.translatable("message.dbzenith.racial_locked", RacialSkills.unlockLevel(s)), true);
            return false;
        }
        if (com.dbzenith.registry.ModEffects.isKiSealed(player)) {
            player.displayClientMessage(Component.translatable("message.dbzenith.ki_sealed"), true);
            return false;
        }
        long ready = d.getRacialCooldown(s.id());
        if (now < ready) {
            player.displayClientMessage(Component.translatable("message.dbzenith.racial_cooldown",
                    Component.translatable(s.translationKey()), (ready - now + 19) / 20), true);
            return false;
        }
        double cost = d.getDerived().maxKi() * s.kiCostPercent() / 100.0;
        boolean free = player.getAbilities().instabuild;
        if (!free && d.getKi() < cost) {
            player.displayClientMessage(Component.translatable("message.dbzenith.no_ki"), true);
            return false;
        }
        if (!instant(player, d, s, now)) return false;                   // nothing to act on: no cost, no cooldown
        if (!free) d.setKi(d.getKi() - cost);
        d.setRacialCooldown(s.id(), now + s.cooldownTicks());
        if (s.durationTicks() > 0) {
            d.startRacialBuff(s.id(), now);
            refresh(player, d, now);
        }
        int c = s.color();
        ImpactPacket.at(player.position().add(0, 1, 0), new Vec3(0, 1, 0), ImpactPacket.KI_HIT, 0.7f, c & 0xFFFFFF, player.getId())
                .send(player.serverLevel());
        player.displayClientMessage(Component.translatable("message.dbzenith.racial_used", Component.translatable(s.translationKey())), true);
        return true;
    }

    /** The instant part of an active. False when it had nothing to act on. */
    static boolean instant(ServerPlayer player, PlayerData d, RacialSkill s, long now) {
        ServerLevel level = player.serverLevel();
        switch (s.id()) {
            case "venting" -> {                                  // let the excess out: a burst that clears space and restores stamina
                double damage = DamageCalculator.kiOutgoing(d, 0.8);
                for (LivingEntity e : TechniqueEffects.around(player, 5)) {
                    e.invulnerableTime = 0;
                    e.hurt(ModDamageTypes.kiBlast(level, player, player), (float) damage);
                    Vec3 push = e.position().subtract(player.position()).normalize().scale(1.4);
                    e.push(push.x, 0.5, push.z);
                }
                d.setStamina(d.getStamina() + d.getDerived().maxStamina() * 0.3);
                ImpactPacket.at(player.position().add(0, 1, 0), new Vec3(0, 1, 0), ImpactPacket.EXPLOSION, 1.4f, s.color() & 0xFFFFFF, player.getId()).send(level);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), com.dbzenith.registry.ModSounds.EXPLOSION.get(), SoundSource.PLAYERS, 1f, 0.6f);
            }
            case "roaring_evolution" -> {                        // a roar that staggers everything near
                for (LivingEntity e : TechniqueEffects.around(player, 8)) {
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2));
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1));
                    Vec3 push = e.position().subtract(player.position()).normalize().scale(0.8);
                    e.push(push.x, 0.3, push.z);
                }
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.4f, 0.7f);
                level.sendParticles(ParticleTypes.SONIC_BOOM, player.getX(), player.getY() + 1.5, player.getZ(), 1, 0, 0, 0, 0);
            }
            case "tyrants_glare" -> {                            // a look that freezes the weak in place
                Vec3 look = player.getLookAngle();
                int n = 0;
                for (LivingEntity e : TechniqueEffects.around(player, 10)) {
                    if (e.position().subtract(player.position()).normalize().dot(look) < 0.5) continue;
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 3));
                    if (e instanceof Player p) ModCapabilities.get(p).ifPresent(o -> o.setStamina(o.getStamina() * 0.9));
                    n++;
                }
                if (n == 0) return false;
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.2f, 0.8f);
            }
            case "spirit_disruption" -> {                        // drain the ki of everyone near
                boolean any = false;
                for (LivingEntity e : TechniqueEffects.around(player, 8)) {
                    any = true;
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
                    if (e instanceof Player p) ModCapabilities.get(p).ifPresent(o -> o.setKi(o.getKi() - o.getDerived().maxKi() * 0.2));
                    level.sendParticles(new DustParticleOptions(new Vector3f(0.4f, 0.9f, 0.5f), 1.2f), e.getX(), e.getY() + 1, e.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
                }
                if (!any) return false;
                level.playSound(null, player.getX(), player.getY(), player.getZ(), com.dbzenith.registry.ModSounds.SKILL.get(), SoundSource.PLAYERS, 1f, 0.9f);
            }
            case "dragon_blessing" -> {                          // the clan's healing: allies near, and a little yourself
                heal(player, d, 0.10);
                for (Player p : level.getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(8), p -> p != player && p.isAlive())) {
                    ModCapabilities.get(p).ifPresent(o -> {
                        o.setBody(o.getBody() + o.getDerived().maxBody() * 0.2);
                        BodyHealth.mirror(p, o);
                    });
                    level.sendParticles(ParticleTypes.HAPPY_VILLAGER, p.getX(), p.getY() + 1, p.getZ(), 12, 0.4, 0.6, 0.4, 0);
                }
                level.playSound(null, player.getX(), player.getY(), player.getZ(), com.dbzenith.registry.ModSounds.SKILL.get(), SoundSource.PLAYERS, 1.2f, 0.8f);
            }
            case "remote_absorb" -> {                            // pull ki out of whatever you look at
                LivingEntity target = TechniqueEffects.lookedAtLiving(player, 16);
                if (target == null) return false;
                double stolen = d.getDerived().maxKi() * 0.10;
                if (target instanceof Player p) ModCapabilities.get(p).ifPresent(o -> o.setKi(o.getKi() - o.getDerived().maxKi() * 0.10));
                else {
                    target.invulnerableTime = 0;
                    target.hurt(ModDamageTypes.absorbed(level, player), (float) DamageCalculator.toVanilla(DamageCalculator.kiOutgoing(d, 0.3)));
                }
                d.setKi(d.getKi() + stolen);
                heal(player, d, 0.10);
                Vec3 from = target.position().add(0, target.getBbHeight() * 0.6, 0), to = player.position().add(0, 1, 0);
                for (int i = 0; i <= 12; i++) {
                    Vec3 p = from.lerp(to, i / 12.0);
                    level.sendParticles(new DustParticleOptions(new Vector3f(1f, 0.5f, 0.75f), 1.0f), p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0);
                }
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1f, 0.7f);
            }
            case "self_repair", "nano_repair" -> {
                if (d.getBody() >= d.getDerived().maxBody()) return false;
                heal(player, d, s.id().equals("self_repair") ? 0.25 : 0.20);
                if (s.id().equals("nano_repair")) clearHarmful(player);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1, player.getZ(), 30, 0.4, 0.7, 0.4, 0.1);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), com.dbzenith.registry.ModSounds.SKILL.get(), SoundSource.PLAYERS, 1f, 1.6f);
            }
            case "bat_swarm" -> {                                // burst into bats, through everything in front
                Vec3 eye = player.getEyePosition();
                Vec3 end = eye.add(player.getLookAngle().scale(8));
                BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                Vec3 dest = hit.getLocation().subtract(player.getLookAngle().scale(0.6));
                double damage = DamageCalculator.kiOutgoing(d, 0.5);
                for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().expandTowards(dest.subtract(eye)).inflate(1.2),
                        e -> e != player && e.isAlive())) {
                    e.invulnerableTime = 0;
                    e.hurt(ModDamageTypes.kiBlast(level, player, player), (float) damage);
                    e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0));
                }
                level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1, player.getZ(), 30, 0.4, 0.6, 0.4, 0.05);
                player.teleportTo(dest.x, dest.y - player.getEyeHeight(), dest.z);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), com.dbzenith.registry.ModSounds.VANISH.get(), SoundSource.PLAYERS, 1.2f, 0.8f);
            }
            case "cellular_absorption" -> {                      // grab the nearest foe and drink its body
                LivingEntity prey = TechniqueEffects.around(player, 3.5).stream()
                        .min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(player))).orElse(null);
                if (prey == null) return false;
                double amount = prey instanceof Player p ? ModCapabilities.get(p).map(o -> o.getDerived().maxBody() * 0.12).orElse(4.0)
                        : prey.getMaxHealth() * 0.12;
                prey.invulnerableTime = 0;
                prey.hurt(ModDamageTypes.absorbed(level, player), (float) (prey instanceof Player ? DamageCalculator.toVanilla(amount) : amount));
                d.setBody(d.getBody() + d.getDerived().maxBody() * 0.12);
                BodyHealth.mirror(player, d);
                level.sendParticles(new DustParticleOptions(new Vector3f(0.6f, 0.85f, 0.25f), 1.4f), prey.getX(), prey.getY() + 1, prey.getZ(), 24, 0.3, 0.5, 0.3, 0.05);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.HONEY_DRINK, SoundSource.PLAYERS, 1f, 0.6f);
            }
            case "system_scan" -> {                              // mark everyone near: they glow and take more
                for (LivingEntity e : TechniqueEffects.around(player, 24)) e.addEffect(new MobEffectInstance(MobEffects.GLOWING, s.durationTicks(), 0));
                level.playSound(null, player.getX(), player.getY(), player.getZ(), com.dbzenith.registry.ModSounds.SKILL.get(), SoundSource.PLAYERS, 0.8f, 1.8f);
            }
            case "void_step" -> {                                // fold space: ten blocks where you look
                Vec3 eye = player.getEyePosition();
                BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(10)), ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, player));
                Vec3 dest = hit.getLocation().subtract(player.getLookAngle().scale(0.6));
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1, player.getZ(), 40, 0.3, 0.6, 0.3, 0.1);
                player.teleportTo(dest.x, dest.y - player.getEyeHeight(), dest.z);
                player.fallDistance = 0;
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, dest.x, dest.y - 0.5, dest.z, 40, 0.3, 0.6, 0.3, 0.1);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), com.dbzenith.registry.ModSounds.VANISH.get(), SoundSource.PLAYERS, 0.8f, 1.4f);
            }
            case "seismic_stomp" -> {                            // the ground jumps: everything near is thrown up
                double damage = DamageCalculator.kiOutgoing(d, 0.5);
                for (LivingEntity e : TechniqueEffects.around(player, 6)) {
                    if (!e.onGround()) continue;
                    e.invulnerableTime = 0;
                    e.hurt(ModDamageTypes.thrown(level, player), (float) damage);
                    e.push(0, 0.9, 0);
                }
                ImpactPacket.at(player.position(), new Vec3(0, 1, 0), ImpactPacket.SPIKE, 1.5f, 0xC0A060, player.getId()).send(level);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), com.dbzenith.registry.ModSounds.EXPLOSION.get(), SoundSource.PLAYERS, 1f, 0.5f);
            }
            case "kaioken" -> {
                return com.dbzenith.transform.Kaioken.raise(player, d);
            }
            case "ki_sense" -> {                                 // the scan: every ki near you shows for ten seconds
                if (d.getSkillLevel("ki_sense") < 3) {
                    player.displayClientMessage(Component.translatable("message.dbzenith.ki_sense_scan_locked"), true);
                    return false;
                }
                level.playSound(null, player.getX(), player.getY(), player.getZ(), com.dbzenith.registry.ModSounds.SKILL.get(), SoundSource.PLAYERS, 1f, 1.4f);
            }
            case "spirit_shock" -> {                             // a point-blank shock: stuns, and breaks guards
                boolean any = false;
                for (LivingEntity e : TechniqueEffects.around(player, 4.5)) {
                    any = true;
                    e.addEffect(new MobEffectInstance(com.dbzenith.registry.ModEffects.STUN.get(), 30, 0));
                    if (e instanceof Player p) ModCapabilities.get(p).ifPresent(o -> {
                        if (o.isGuarding()) com.dbzenith.combat.GuardRules.lower(o);
                    });
                    Vec3 push = e.position().subtract(player.position()).normalize().scale(0.6);
                    e.push(push.x, 0.2, push.z);
                }
                if (!any) return false;
                ImpactPacket.at(player.position().add(0, 1, 0), player.getLookAngle(), ImpactPacket.GUARD_BREAK, 1.2f, s.color() & 0xFFFFFF, player.getId()).send(level);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), com.dbzenith.registry.ModSounds.STUN.get(), SoundSource.PLAYERS, 0.6f, 1.6f);
            }
            case "desperate_gambit" -> {
                if (d.getBody() > d.getDerived().maxBody() * 0.25) {
                    player.displayClientMessage(Component.translatable("message.dbzenith.gambit_not_yet"), true);
                    return false;
                }
                d.setKi(d.getKi() * 0.5);
                d.setStamina(0);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.5f, 1.6f);
            }
            case "instant_transmission" -> {
                Vec3 dest = TRANSMISSION.get(player.getUUID());
                if (dest == null) return false;
                level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1, player.getZ(), 24, 0.3, 0.6, 0.3, 0.05);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), com.dbzenith.registry.ModSounds.TELEPORT.get(), SoundSource.PLAYERS, 1f, 1.4f);
                player.teleportTo(dest.x, dest.y, dest.z);
                player.fallDistance = 0;
                level.sendParticles(ParticleTypes.END_ROD, dest.x, dest.y + 1, dest.z, 24, 0.3, 0.6, 0.3, 0.05);
                level.playSound(null, dest.x, dest.y, dest.z, com.dbzenith.registry.ModSounds.TELEPORT.get(), SoundSource.PLAYERS, 1f, 1.6f);
            }
            case "sheer_willpower", "saiyans_resolve", "shattering_the_limit", "blazing_spirit", "mindless_gambit", "overclock",
                    "blur", "sacred_barrier", "dark_aura", "limit_break", "ki_barrier" -> level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    com.dbzenith.registry.ModSounds.SKILL.get(), SoundSource.PLAYERS, 1f, 0.9f);   // timed buffs: the buff is the effect
            default -> { }
        }
        return true;
    }

    static void heal(ServerPlayer player, PlayerData d, double fraction) {
        d.setBody(Math.min(d.getDerived().maxBody(), d.getBody() + d.getDerived().maxBody() * fraction));
        BodyHealth.mirror(player, d);
    }

    static void clearHarmful(ServerPlayer player) {
        for (MobEffectInstance e : java.util.List.copyOf(player.getActiveEffects())) {
            if (!e.getEffect().isBeneficial() && e.getEffect() != com.dbzenith.registry.ModEffects.KI_SEAL.get()) player.removeEffect(e.getEffect());
        }
    }

    // ------------------------------------------------------------------ ticking

    /** Every tick from KiTicker: buffs and the conditions the passives read, plus the passives that act. */
    public static void tick(ServerPlayer player, PlayerData d, long now) {
        if (now % 5 == 0) refresh(player, d, now);
        Set<String> active = d.getRacialActive();
        if (active.contains("sheer_willpower")) d.setStamina(d.getDerived().maxStamina());
        if (now % 20 == 0) {
            ServerLevel level = player.serverLevel();
            if (active.contains("blazing_spirit")) {             // the aura burns what stands in it
                double damage = DamageCalculator.kiOutgoing(d, 0.08);
                for (LivingEntity e : TechniqueEffects.around(player, 3)) {
                    e.invulnerableTime = 0;
                    e.hurt(ModDamageTypes.kiBlast(level, player, player), (float) damage);
                }
            }
            if (active.contains("dark_aura")) {
                for (LivingEntity e : TechniqueEffects.around(player, 8)) e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 30, 0));
                level.sendParticles(new DustParticleOptions(new Vector3f(0.35f, 0.1f, 0.6f), 1.6f), player.getX(), player.getY() + 0.2, player.getZ(),
                        10, 1.5, 0.1, 1.5, 0.01);
            }
            if (RacialSkills.has(d, "vacuum_breathing")) player.setAirSupply(player.getMaxAirSupply());
            if (now % 100 == 0 && RacialSkills.has(d, "limb_regeneration")) clearHarmful(player);
            secondWind(player, d, now);
        }
    }

    /** Recompute the live conditions and which buffs are on (ACTIVE) or wearing off (AFTER). */
    static void refresh(ServerPlayer player, PlayerData d, long now) {
        Set<String> active = new HashSet<>(), after = new HashSet<>();
        var it = d.getRacialBuffs().entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            RacialSkill s = RacialSkills.byId(e.getKey());
            long age = now - e.getValue();
            if (s == null || age < 0 || age >= s.durationTicks() * 3L) {
                it.remove();
                continue;
            }
            if (age < s.durationTicks()) active.add(s.id());
            else after.add(s.id());
        }
        boolean hurt = now - d.getLastDamagedTick() < 100;
        d.setRacialState(RacialSkills.mask(d, RacialSkills.isNight(player.level()), hurt), active, after);
    }

    /** Second Wind: falling below 10% body in a fight restores a third of body and stamina, every ten minutes. */
    static void secondWind(ServerPlayer player, PlayerData d, long now) {
        if (!RacialSkills.has(d, "second_wind")) return;
        double max = d.getDerived().maxBody();
        if (max <= 0 || d.getBody() > max * 0.10 || d.getBody() <= 0) return;
        if (now - d.getRacialOnce("second_wind") < SECOND_WIND_COOLDOWN) return;
        d.setRacialOnce("second_wind", now);
        heal(player, d, 0.35);
        d.setStamina(d.getStamina() + d.getDerived().maxStamina() * 0.35);
        player.displayClientMessage(Component.translatable("message.dbzenith.second_wind"), true);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), com.dbzenith.registry.ModSounds.ZENKAI.get(), SoundSource.PLAYERS, 1f, 1.4f);
    }

    /** Zenkai strength factor: Primal Zenkai makes it half again as strong. */
    public static double zenkaiFactor(PlayerData d) {
        return RacialSkills.has(d, "primal_zenkai") ? 1.5 : 1.0;
    }

    /** Revitalizing Metamorphosis: a Frost Demon's change of form heals. */
    public static void onFormEntered(ServerPlayer player, PlayerData d, boolean base) {
        if (!base && RacialSkills.has(d, "revitalizing_metamorphosis")) heal(player, d, 0.15);
    }

    // ------------------------------------------------------------------ events

    /** Death Regeneration (Majin) and Reincarnation (Namekian): cheat death once in a while. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerData d = ModCapabilities.get(player).orElse(null);
        if (d == null) return;
        if (d.getRacialActive().contains("desperate_gambit")) {       // the gambit: you will not fall while it lasts
            event.setCanceled(true);
            d.setBody(1);
            player.setHealth(1f);
            BodyHealth.mirror(player, d);
            return;
        }
        long now = player.level().getGameTime();
        String id = RacialSkills.has(d, "death_regeneration") && now - d.getRacialOnce("death_regeneration") >= DEATH_REGEN_COOLDOWN ? "death_regeneration"
                : RacialSkills.has(d, "reincarnation") && now - d.getRacialOnce("reincarnation") >= REINCARNATION_COOLDOWN ? "reincarnation" : null;
        if (id == null) return;
        event.setCanceled(true);
        d.setRacialOnce(id, now);
        double fraction = id.equals("reincarnation") ? 0.30 : 0.20;
        d.setBody(d.getDerived().maxBody() * fraction);
        player.setHealth(Math.max(1f, player.getMaxHealth() * (float) fraction));
        BodyHealth.mirror(player, d);
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1));
        ServerLevel level = player.serverLevel();
        int color = RacialSkills.byId(id).color() & 0xFFFFFF;
        ImpactPacket.at(player.position().add(0, 1, 0), new Vec3(0, 1, 0), ImpactPacket.EXPLOSION, 1.0f, color, player.getId()).send(level);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), id.equals("reincarnation") ? SoundEvents.TOTEM_USE : SoundEvents.SLIME_BLOCK_BREAK,
                SoundSource.PLAYERS, 1f, 0.8f);
        player.displayClientMessage(Component.translatable("message.dbzenith." + id), false);
    }

    /** Elastic Monster: a Majin bounces. */
    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer p
                && ModCapabilities.get(p).map(d -> RacialSkills.has(d, "elastic_monster")).orElse(false)) {
            event.setDamageMultiplier(0);
        }
    }

    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        ModCapabilities.get(p).ifPresent(d -> {
            double resist = Math.min(1.0, RacialSkills.sum(d, Stat.KNOCKBACK_RESIST));
            if (resist >= 1.0) event.setCanceled(true);
            else if (resist > 0) event.setStrength((float) (event.getStrength() * (1 - resist)));
        });
    }

    /** After a blow lands: lifesteal and ki on hit. */
    public static void afterHit(ServerPlayer attacker, PlayerData d, double dealt) {
        if (dealt <= 0) return;
        double steal = RacialSkills.sum(d, Stat.LIFESTEAL);
        if (steal > 0) {
            d.setBody(Math.min(d.getDerived().maxBody(), d.getBody() + dealt * steal));
            BodyHealth.mirror(attacker, d);
        }
        double ki = RacialSkills.sum(d, Stat.KI_ON_HIT);
        if (ki > 0) d.setKi(d.getKi() + d.getDerived().maxKi() * ki);
    }

    /**
     * Echo Strike: dodging a blow with an afterimage leaves you behind the attacker, striking back: Light (x1),
     * Weightless (x1.3), Phantom (x1.6). Once every six seconds.
     */
    public static void echoStrike(ServerPlayer player, PlayerData d, LivingEntity attacker, long now) {
        int level = d.getSkillLevel("echo_strike");
        if (level <= 0 || attacker == null || attacker == player || now < d.getRacialCooldown("echo_strike")) return;
        d.setRacialCooldown("echo_strike", now + 120);
        Vec3 behind = attacker.position().subtract(attacker.getLookAngle().multiply(1, 0, 1).normalize().scale(1.4));
        ServerLevel lvl = player.serverLevel();
        lvl.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 1, player.getZ(), 10, 0.3, 0.6, 0.3, 0.02);
        player.teleportTo(behind.x, attacker.getY(), behind.z);
        player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, attacker.getEyePosition());
        double damage = DamageCalculator.meleeOutgoing(d, 1.0, 1) * (level == 1 ? 1.0 : level == 2 ? 1.3 : 1.6);
        attacker.invulnerableTime = 0;
        attacker.hurt(ModDamageTypes.thrown(lvl, player), (float) damage);
        ImpactPacket.melee(player, attacker, ImpactPacket.HEAVY).send(lvl);
        lvl.playSound(null, player.getX(), player.getY(), player.getZ(), com.dbzenith.registry.ModSounds.PUNCH_HEAVY.get(), SoundSource.PLAYERS, 1f, 1.3f);
    }

    /** Rising Charge: a technique fired within two seconds of letting go of a charge gains up to +50% (at five seconds held). */
    public static double risingChargeBonus(PlayerData d) {
        int held = d.takeRisingCharge();
        if (d.getSkillLevel("rising_charge") <= 0 || held < 40) return 1.0;
        return 1.0 + Math.min(0.5, held / 100.0 * 0.5);
    }
}
