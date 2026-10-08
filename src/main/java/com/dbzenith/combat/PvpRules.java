package com.dbzenith.combat;

import com.dbzenith.DBZenith;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.world.Otherworld;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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
    public enum Verdict { ALLOW, BLOCK_SAFE, BLOCK_ATTACKER_OFF, BLOCK_VICTIM_OFF, BLOCK_DUEL }

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
        boolean aOn = isPvp(attacker), vOn = isPvp(victim);
        if ((aOn && vOn) || DBZConfig.SERVER.pvpHurtOutOfPvp.get()) return Verdict.ALLOW;   // out of PvP: the blow lands, and tags them in (CX-20)
        return aOn ? Verdict.BLOCK_VICTIM_OFF : Verdict.BLOCK_ATTACKER_OFF;
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
            case BLOCK_SAFE -> attacker.displayClientMessage(Component.translatable("message.dbzenith.pvp_safe_zone"), true);
            case BLOCK_DUEL -> attacker.displayClientMessage(Component.translatable("message.dbzenith.duel_no_interfere"), true);
            case BLOCK_ATTACKER_OFF -> attacker.displayClientMessage(Component.translatable("message.dbzenith.pvp_you_off"), true);
            case BLOCK_VICTIM_OFF -> attacker.displayClientMessage(Component.translatable("message.dbzenith.pvp_they_off", victim.getDisplayName()), true);
        }
        return false;
    }

    /**
     * A combat tag (CX-20): for {@code combatTagSeconds} PvP mode cannot be switched off, and (with {@code tagForcesPvp})
     * a player out of PvP mode is switched into it. After the tag they stay in PvP mode until they switch it off.
     */
    public static void tag(ServerPlayer p, long now) {
        PlayerData d = ModCapabilities.get(p).orElse(null);
        if (d == null) return;
        d.setPvpCombatUntil(now + DBZConfig.SERVER.pvpCombatTag.get() * 20L);
        if (!d.isPvp() && DBZConfig.SERVER.pvpTagForcesOn.get() && !p.isSpectator()) set(p, true, true);
    }

    /** Hit by anything that isn't a player (an NPC, a mob, its arrow): tagged too, once the hit goes through. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMobHit(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim) || victim.isCreative() || victim.isSpectator()) return;
        Entity by = event.getSource().getEntity();
        if (!(by instanceof net.minecraft.world.entity.LivingEntity) || by instanceof net.minecraft.world.entity.player.Player || by == victim) return;
        tag(victim, victim.level().getGameTime());
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
     * Whether {@code e} fights with the combat system (CX-20): players only in PvP mode (off, it is plain Minecraft:
     * vanilla hits, no combat moves), NPCs always.
     */
    public static boolean combatOn(net.minecraft.world.entity.LivingEntity e) {
        if (!(e instanceof net.minecraft.world.entity.player.Player p)) return true;
        PlayerData d = ModCapabilities.get(p).orElse(null);
        return d != null && d.isPvp();
    }

    /**
     * Turns PvP mode on or off ({@code want} null: the other way), as the player asked: a plain switch, no sound, light or
     * message (change request, phase 1). Silently refused during the toggle cooldown (0 by default), or turning it off
     * while combat-tagged. Returns whether it changed.
     */
    public static boolean toggle(ServerPlayer p, Boolean want) {
        PlayerData d = ModCapabilities.get(p).orElse(null);
        if (d == null) return false;
        boolean on = want == null ? !d.isPvp() : want;
        if (on == d.isPvp()) return false;
        long now = p.level().getGameTime();
        if (now < d.getPvpReadyAt() && !p.getAbilities().instabuild) return false;
        if (!on && now < d.getPvpCombatUntil() && !p.getAbilities().instabuild) return false;
        set(p, on, false);
        return true;
    }

    /** Sets PvP mode, nothing more. {@code forced}: switched on by the game (a tag), so no toggle cooldown afterwards. */
    public static void set(ServerPlayer p, boolean on, boolean forced) {
        PlayerData d = ModCapabilities.get(p).orElse(null);
        if (d == null) return;
        d.setPvp(on);
        com.dbzenith.combat.meter.MeterLogic.onPvp(p, d, on);                   // the meters: brought in, or emptied (CX-20)
        if (!on) {                                                              // back to plain Minecraft: no guard up, no lock (CX-20)
            GuardRules.lower(d);
            com.dbzenith.combat.engine.Targeting.set(p, -1);
            com.dbzenith.network.ModNetwork.sendTo(p, new com.dbzenith.network.LockOnPacket(-1));
        }
        if (!forced) d.setPvpReadyAt(p.level().getGameTime() + DBZConfig.SERVER.pvpToggleCooldown.get() * 20L);
    }
}
