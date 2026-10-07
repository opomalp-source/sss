package com.dbzenith.combat;

import com.dbzenith.DBZenith;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.world.Otherworld;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

/**
 * PvP mode (CX-19). Players can only hurt each other in PvP mode; it is off when a player joins and after every death.
 * <ul>
 *   <li>Toggle with the PvP key (P) or {@code /pvp}: a cooldown between toggles, and no turning it off while in a fight
 *       (for a while after hitting or being hit by a player).</li>
 *   <li>A player in PvP mode who strikes one who is not pulls them in: their PvP mode turns on and that first blow does
 *       no harm, so nobody is caught defenceless. (Configurable: both must be in PvP mode, and the pull-in.)</li>
 *   <li>Safe zones: around world spawn, the other world (not Hell), the tournament grounds (except between the two
 *       fighters of a match), and any zone set with {@code /dbz pvpzone}.</li>
 * </ul>
 * Every player-on-player blow, ki attack and effect goes through {@link #judge}; NPCs and mobs are not affected.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PvpRules {
    public enum Verdict { ALLOW, BLOCK_SAFE, BLOCK_ATTACKER_OFF, BLOCK_VICTIM_OFF, PULL_IN, BLOCK_DUEL }

    private static final Vector3f RED = new Vector3f(0.95f, 0.15f, 0.12f), WHITE = new Vector3f(0.9f, 0.95f, 1f);

    /** GameTest players: two of them fight whatever the rules (the PvP tests take theirs out). */
    public static final java.util.Set<java.util.UUID> TEST_BYPASS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private PvpRules() {}

    public static boolean active() {
        return DBZConfig.SERVER.pvpRules.get();
    }

    private static boolean bypass(ServerPlayer p) {
        return TEST_BYPASS.contains(p.getUUID());
    }

    public static boolean isPvp(ServerPlayer p) {
        return ModCapabilities.get(p).map(PlayerData::isPvp).orElse(false);
    }

    /** Whether a player stands somewhere no PvP happens. */
    public static boolean inSafeZone(ServerPlayer p) {
        Level level = p.level();
        DBZConfig.Server c = DBZConfig.SERVER;
        if (c.pvpSafeOtherworld.get() && level.dimension() == Otherworld.OTHERWORLD) return true;
        int r = c.pvpSpawnSafeRadius.get();
        if (r > 0 && level.dimension() == Level.OVERWORLD) {
            BlockPos s = ((ServerLevel) level).getSharedSpawnPos();
            double dx = p.getX() - s.getX() - 0.5, dz = p.getZ() - s.getZ() - 0.5;
            if (dx * dx + dz * dz < (double) r * r) return true;
        }
        if (c.pvpSafeTournament.get() && level.dimension() == Level.OVERWORLD && !com.dbzenith.tournament.Tournament.isFighter(p)) {
            BlockPos ring = com.dbzenith.tournament.TournamentGrounds.ring((ServerLevel) level);
            int half = com.dbzenith.tournament.TournamentGrounds.PLAZA;
            if (ring != null && Math.abs(p.getX() - ring.getX() - 0.5) <= half + 1 && Math.abs(p.getZ() - ring.getZ() - 0.5) <= half + 1
                    && Math.abs(p.getY() - ring.getY()) < 24) return true;
        }
        return PvpZones.of(p.server).at(level.dimension().location().toString(), p.position()) != null;
    }

    /** What happens when {@code attacker} tries to harm {@code victim} (both players). */
    public static Verdict judge(ServerPlayer attacker, ServerPlayer victim) {
        if (com.dbzenith.duel.Duels.inDuel(attacker) || com.dbzenith.duel.Duels.inDuel(victim)) {   // arena rules (CX-19 phase 9)
            return com.dbzenith.duel.Duels.opponents(attacker, victim) ? Verdict.ALLOW : Verdict.BLOCK_DUEL;
        }
        if (!active() || attacker == victim) return Verdict.ALLOW;
        if (bypass(attacker) && bypass(victim)) return Verdict.ALLOW;
        if (com.dbzenith.tournament.Tournament.isFighter(attacker) && com.dbzenith.tournament.Tournament.isFighter(victim)) return Verdict.ALLOW;
        if (inSafeZone(attacker) || inSafeZone(victim)) return Verdict.BLOCK_SAFE;
        if (!isPvp(attacker)) return Verdict.BLOCK_ATTACKER_OFF;
        if (isPvp(victim) || !DBZConfig.SERVER.pvpRequireBoth.get()) return Verdict.ALLOW;
        return DBZConfig.SERVER.pvpAutoEnable.get() ? Verdict.PULL_IN : Verdict.BLOCK_VICTIM_OFF;
    }

    /**
     * For anything a player does to another that is not a blow (stuns, seals, grabs, absorption): true if it may go
     * ahead. A pull-in happens here too (and blocks this one effect).
     */
    public static boolean mayAffect(Entity attacker, Entity target) {
        if (!(attacker instanceof ServerPlayer a) || !(target instanceof ServerPlayer v)) return true;
        return apply(a, v, judge(a, v));
    }

    /** Carries a verdict out (messages, pull-in, the fight timer). Returns whether the harm goes ahead. */
    static boolean apply(ServerPlayer attacker, ServerPlayer victim, Verdict v) {
        long now = attacker.level().getGameTime();
        switch (v) {
            case ALLOW -> {
                tag(attacker, now);
                tag(victim, now);
                return true;
            }
            case PULL_IN -> {
                set(victim, true, true);
                victim.displayClientMessage(Component.translatable("message.dbzenith.pvp_pulled_in", attacker.getDisplayName()).withStyle(ChatFormatting.RED), false);
                attacker.displayClientMessage(Component.translatable("message.dbzenith.pvp_pulled_other", victim.getDisplayName()).withStyle(ChatFormatting.GOLD), true);
                tag(attacker, now);
                tag(victim, now);
                return false;
            }
            case BLOCK_SAFE -> attacker.displayClientMessage(Component.translatable("message.dbzenith.pvp_safe_zone"), true);
            case BLOCK_DUEL -> attacker.displayClientMessage(Component.translatable("message.dbzenith.duel_no_interfere"), true);
            case BLOCK_ATTACKER_OFF -> attacker.displayClientMessage(Component.translatable("message.dbzenith.pvp_you_off"), true);
            case BLOCK_VICTIM_OFF -> attacker.displayClientMessage(Component.translatable("message.dbzenith.pvp_they_off", victim.getDisplayName()), true);
        }
        return false;
    }

    private static void tag(ServerPlayer p, long now) {
        ModCapabilities.get(p).ifPresent(d -> d.setPvpCombatUntil(now + DBZConfig.SERVER.pvpCombatTag.get() * 20L));
    }

    /** Every blow between two players: melee, ki, throws, beams, explosions they own. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        Entity by = event.getSource().getEntity();
        if (!(by instanceof ServerPlayer attacker) || attacker == victim) return;
        if (!apply(attacker, victim, judge(attacker, victim))) event.setCanceled(true);
    }

    // ------------------------------------------------------------------ effects that come without a blow

    /** Who is acting right now (a technique, a racial skill, a ki attack landing), so effects they cause can be judged. */
    private static Entity actor;

    /** Marks {@code e} as the one acting until the end of this server tick. */
    public static void actor(Entity e) {
        actor = e;
    }

    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event) {
        if (event.phase == net.minecraftforge.event.TickEvent.Phase.END) actor = null;
    }

    /** A harmful effect (stun, ki seal, blindness, slowness...) a player puts on another goes through the same rules as a blow. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEffect(net.minecraftforge.event.entity.living.MobEffectEvent.Applicable event) {
        if (!(event.getEntity() instanceof ServerPlayer victim) || !(actor instanceof ServerPlayer attacker) || attacker == victim) return;
        if (event.getEffectInstance().getEffect().getCategory() != net.minecraft.world.effect.MobEffectCategory.HARMFUL) return;
        if (!apply(attacker, victim, judge(attacker, victim))) event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
    }

    // ------------------------------------------------------------------ toggling

    /**
     * Turns PvP mode on or off ({@code want} null: the other way), as the player asked. Refused during the cooldown, or
     * turning it off while in a fight. Returns whether it changed.
     */
    public static boolean toggle(ServerPlayer p, Boolean want) {
        PlayerData d = ModCapabilities.get(p).orElse(null);
        if (d == null) return false;
        boolean on = want == null ? !d.isPvp() : want;
        if (on == d.isPvp()) return false;
        long now = p.level().getGameTime();
        if (now < d.getPvpReadyAt() && !p.getAbilities().instabuild) {
            p.displayClientMessage(Component.translatable("message.dbzenith.pvp_cooldown", (d.getPvpReadyAt() - now + 19) / 20), true);
            return false;
        }
        if (!on && now < d.getPvpCombatUntil() && !p.getAbilities().instabuild) {
            p.displayClientMessage(Component.translatable("message.dbzenith.pvp_in_combat", (d.getPvpCombatUntil() - now + 19) / 20), true);
            return false;
        }
        set(p, on, false);
        return true;
    }

    /** Sets PvP mode with its sound, ring of light and message. {@code forced}: pulled in, so no cooldown on turning it back off later. */
    public static void set(ServerPlayer p, boolean on, boolean forced) {
        PlayerData d = ModCapabilities.get(p).orElse(null);
        if (d == null) return;
        d.setPvp(on);
        if (!forced) d.setPvpReadyAt(p.level().getGameTime() + DBZConfig.SERVER.pvpToggleCooldown.get() * 20L);
        ServerLevel level = p.serverLevel();
        Vec3 at = p.position();
        for (int i = 0; i < 24; i++) {                                     // a ring at the feet: red going in, white coming out
            double a = Math.PI * 2 * i / 24;
            level.sendParticles(new DustParticleOptions(on ? RED : WHITE, 1.4f), at.x + Math.cos(a) * 1.1, at.y + 0.1, at.z + Math.sin(a) * 1.1, 1, 0, 0.05, 0, 0);
        }
        level.playSound(null, at.x, at.y, at.z, on ? com.dbzenith.registry.ModSounds.POWERUP.get() : com.dbzenith.registry.ModSounds.POWER_DOWN.get(),
                SoundSource.PLAYERS, 0.8f, on ? 1.3f : 1.0f);
        p.displayClientMessage(Component.translatable(on ? "message.dbzenith.pvp_on" : "message.dbzenith.pvp_off")
                .withStyle(on ? ChatFormatting.RED : ChatFormatting.AQUA), true);
    }
}
