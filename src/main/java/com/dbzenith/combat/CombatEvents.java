package com.dbzenith.combat;

import com.dbzenith.DBZenith;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.ImpactPacket;
import com.dbzenith.race.RacePassives;
import com.dbzenith.stats.StatCalculator;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Routes every hit through {@link DamageCalculator}.
 * <ul>
 *   <li>Player victims: damage becomes body damage (defense, guard, evasion), vanilla health is re-mirrored.</li>
 *   <li>Non-player victims hit by DBZ melee or ki: damage is DBZ damage converted to vanilla scale.</li>
 *   <li>Attacking players earn TP for damage dealt and kills.</li>
 * </ul>
 * Runs at LOW priority so other mods' modifications to the raw amount are respected.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CombatEvents {
    /** Large enough to kill through armor and absorption once body hits zero. */
    private static final float LETHAL = 1.0e6f;

    private CombatEvents() {}

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        DamageSource source = event.getSource();
        // /kill, the void etc. must stay absolute; BodyHealth adopts the resulting health change.
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;

        boolean isKi = source.is(ModDamageTypes.KI_BLAST);
        boolean isThrow = source.is(ModDamageTypes.THROW);
        boolean isStrike = source.is(ModDamageTypes.STRIKE);                   // a blow of the combat engine (CX-19): finished damage
        Player attacker = source.getEntity() instanceof Player p ? p : null;
        boolean isMelee = !isKi && !isStrike && attacker != null && source.is(DamageTypes.PLAYER_ATTACK) && source.getDirectEntity() == attacker;
        PlayerData attackerData = attacker != null ? ModCapabilities.get(attacker).orElse(null) : null;

        // 1) raw DBZ damage
        double raw;
        int impact = -1; // which hit effect the clients draw (ImpactPacket), -1 for none
        if (isKi || isThrow || isStrike) {
            raw = event.getAmount(); // ki blasts, throws and the engine's blows deal raw DBZ damage
            if (isStrike) impact = com.dbzenith.combat.engine.CombatEngine.pendingImpact >= 0 ? com.dbzenith.combat.engine.CombatEngine.pendingImpact : ImpactPacket.PUNCH;
        } else if (isMelee && attackerData != null) {                       // a weapon: vanilla's swing, with DBZ strength behind it
            attackerData.recomputeIfStale();
            DBZConfig.Server c = DBZConfig.SERVER;
            int combo = attackerData.registerHit(victim.level().getGameTime(), c.comboWindowTicks.get(), c.comboMaxHits.get());
            raw = DamageCalculator.meleeOutgoing(attackerData, event.getAmount(), combo);
            attackerData.setStamina(attackerData.getStamina() - c.meleeStaminaCost.get());
            impact = ImpactPacket.PUNCH;
        } else {
            raw = DamageCalculator.fromVanilla(event.getAmount());
            if (source.getEntity() instanceof com.dbzenith.npc.KiFighter f && source.getDirectEntity() == f) impact = ImpactPacket.PUNCH;
        }

        // 2) apply to victim
        PlayerData victimData = victim instanceof Player vp ? ModCapabilities.get(vp).orElse(null) : null;
        raw *= godKiFactor(attackerData, victimData);
        if (attackerData != null && (isKi || isMelee || isThrow || isStrike)) raw *= com.dbzenith.race.Alignment.damageMultiplier(attackerData);
        if (attackerData != null || victimData != null) {                      // racial skills on both sides of the blow
            PlayerData by = isKi || isMelee || isThrow || isStrike ? attackerData : null;
            double foeBody = victimData != null ? victimData.getBody() / Math.max(1, victimData.getDerived().maxBody())
                    : victim.getHealth() / Math.max(1f, victim.getMaxHealth());
            boolean stronger = by != null && (victimData != null
                    ? com.dbzenith.stats.StatCalculator.battlePower(victimData) > com.dbzenith.stats.StatCalculator.battlePower(by)
                    : com.dbzenith.npc.KiFighter.effectiveMaxHealth(victim) > by.getDerived().maxBody());
            raw *= com.dbzenith.race.RacialSkills.blowFactor(by, victimData, foeBody, stronger, isKi);
        }
        double dealt;
        boolean evaded = false, guarded = false;
        if (victimData != null) {
            Player player = (Player) victim;
            victimData.recomputeIfStale();
            boolean guardAway = victimData.isGuarding() && !com.dbzenith.combat.engine.Evasion.guardCovers(victim, source.getEntity());
            if (guardAway) victimData.setGuarding(false);                       // a blow from behind gets round the guard (CX-19)
            if (isStrike && com.dbzenith.combat.engine.CombatEngine.pendingUnblockable && victimData.isGuarding()) GuardRules.lower(victimData);   // the sweep takes your legs
            if (!isKi && !isThrow && !isMelee && !isStrike && !source.is(DamageTypeTags.BYPASSES_ARMOR) && event.getAmount() > 0) {
                // Vanilla armor still matters against mobs and the environment.
                float afterArmor = CombatRules.getDamageAfterAbsorb(event.getAmount(), player.getArmorValue(),
                        (float) player.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
                double kept = afterArmor / event.getAmount();
                if (source.getEntity() instanceof com.dbzenith.npc.KiFighter) { // fighters hit through most of it
                    kept = 1 - (1 - kept) * DBZConfig.SERVER.fighterArmorEffect.get();
                }
                raw *= kept;
            }
            boolean afterimage = source.getEntity() != null && victim.level().getGameTime() <= victimData.getDashEvadeUntil();
            if (!afterimage && !isThrow && source.getEntity() != null && victim instanceof net.minecraft.server.level.ServerPlayer uiPlayer
                    && com.dbzenith.transform.UltraInstinct.evade(uiPlayer, victimData, source.getEntity(), victim.getRandom().nextDouble())) {
                afterimage = true;                                               // Ultra Instinct: the body moved on its own
            }
            if (afterimage && !isKi && victim instanceof net.minecraft.server.level.ServerPlayer sp
                    && source.getEntity() instanceof net.minecraft.world.entity.LivingEntity foe && foe != victim) {
                com.dbzenith.race.RacialSkillEffects.echoStrike(sp, victimData, foe, victim.level().getGameTime());
            }
            if (isKi && !afterimage) raw *= RacePassives.absorbKiHit(victimData, raw, victim.level().getGameTime());
            dealt = afterimage ? 0 : DamageCalculator.againstPlayer(raw, victimData, source.getEntity() != null && !isThrow, victim.getRandom());
            dealt *= 1 - victimData.getGearReduction();                          // a full gi or armour set
            if (attackerData != null && attacker != victim && attacker instanceof Player) {   // the PvP balance curve (CX-19 phase 10)
                com.dbzenith.combat.engine.Fighter g = com.dbzenith.combat.engine.CombatEngine.peek(victim);
                double comboSoFar = g != null && g.comboFrom() == attacker.getId() ? g.comboDamage() : 0;
                dealt = PvpBalance.apply(attackerData, victimData, dealt, comboSoFar, isKi);
            }
            if (afterimage) impact = -1;                                         // dodged: nothing landed
            else if (impact >= 0 && victimData.isGuarding()) impact = ImpactPacket.GUARD;
            if (victimData.isGuarding() && dealt > 0) {
                boolean blow = impact >= 0;                                      // fists and kicks can be parried
                GuardRules.Outcome g = GuardRules.onHit(player, victimData, source.getEntity(), DamageCalculator.guardPrevented(dealt),
                        blow, isKi, victim.level().getGameTime());
                if (g == GuardRules.Outcome.PARRY) {
                    dealt = 0;
                    impact = ImpactPacket.PARRY;
                } else if (g == GuardRules.Outcome.BREAK) {
                    if (blow) impact = ImpactPacket.GUARD_BREAK;
                    else if (victim.level() instanceof net.minecraft.server.level.ServerLevel lvl) {   // ki and other breaks get their burst too
                        ImpactPacket.at(victim.getBoundingBox().getCenter(), victim.getLookAngle(), ImpactPacket.GUARD_BREAK, 1f, 0xAEE6FF, -1).send(lvl);
                    }
                }
            }
            guarded = victimData.isGuarding();
            if (guardAway) victimData.setGuarding(true);
            evaded = afterimage;
            if (dealt > 0) com.dbzenith.race.TailRules.onHit(player, victimData, source); // blades can cut a tail
            BodyHealth.adoptExternalChanges(player, victimData);
            victimData.setBody(victimData.getBody() - dealt);
            victimData.setLastDamagedTick(victim.level().getGameTime());
            if (source.getEntity() instanceof net.minecraft.world.entity.LivingEntity foe && foe != victim) victimData.setLastFoeHitTick(victim.level().getGameTime());
            if (victimData.getBody() <= 0 && victim.level().getGameTime() < victimData.getImmortalUntil()) {
                victimData.setBody(1); // the immortality wish
            }
            if (victimData.getBody() <= 0) {
                event.setAmount(LETHAL);
            } else {
                BodyHealth.mirror(player, victimData);
                event.setAmount(0);
            }
        } else {
            dealt = raw;
            if (victim instanceof com.dbzenith.npc.TrainingDummy td) {           // the dummy's guard (CX-19 phase 9)
                int gd = td.guards(source.getEntity(), isStrike || isMelee);
                if (gd == 2) {                                                  // a parry: no damage, the attacker staggers
                    dealt = raw = 0;
                    impact = ImpactPacket.PARRY;
                    if (source.getEntity() instanceof net.minecraft.world.entity.LivingEntity foe) {
                        foe.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.dbzenith.registry.ModEffects.STUN.get(),
                                DBZConfig.SERVER.parryStunTicks.get(), 0));
                        com.dbzenith.combat.engine.Evasion.onPerfectGuard(td, foe, victim.level().getGameTime());
                    }
                } else if (gd == 1) {                                           // a block: chip damage
                    dealt = raw = raw * 0.1;
                    if (impact >= 0) impact = ImpactPacket.GUARD;
                }
                guarded = gd > 0;
            }
            float amount = isKi || isThrow || isMelee || isStrike ? DamageCalculator.toVanilla(raw) : event.getAmount();
            if (victim instanceof com.dbzenith.npc.KiFighter fighter) amount /= (float) fighter.toughness(); // leveled foes
            event.setAmount(amount);
        }

        if (isStrike || isKi) {                                                 // the engine reads what became of its blow
            com.dbzenith.combat.engine.CombatEngine.outcomeImpact = evaded ? -2 : impact;
            com.dbzenith.combat.engine.CombatEngine.outcomeDealt = dealt;
            com.dbzenith.combat.engine.CombatEngine.outcomeGuarded = guarded;
        }
        int feel = isStrike ? com.dbzenith.combat.engine.CombatEngine.pendingFlags : 0;     // critical, counter, Z-hit (CX-19e)
        if (impact >= 0 && source.getEntity() != null && victim.level() instanceof net.minecraft.server.level.ServerLevel level) {
            int hitstop = com.dbzenith.combat.engine.CombatEngine.hitstopTicks(isStrike ? com.dbzenith.combat.engine.CombatEngine.pendingHitstop : -1, impact, feel);
            ImpactPacket.melee(source.getEntity(), victim, impact).withFeel(feel, hitstop).send(level);
        }
        if (dealt > 0 && !evaded && source.getEntity() instanceof net.minecraft.world.entity.LivingEntity && source.getEntity() != victim
                && victim.level() instanceof net.minecraft.server.level.ServerLevel level) {             // the damage number
            int nf = feel | (guarded ? com.dbzenith.network.DamageNumberPacket.GUARDED : 0) | (isKi ? com.dbzenith.network.DamageNumberPacket.KI : 0)
                    | (impact == ImpactPacket.HEAVY || impact == ImpactPacket.SPIKE || impact == ImpactPacket.GUARD_BREAK ? com.dbzenith.network.DamageNumberPacket.HEAVY : 0);
            int color = source.getDirectEntity() instanceof com.dbzenith.skill.KiBlastEntity b ? b.getColor()
                    : source.getDirectEntity() instanceof com.dbzenith.skill.KiBeamEntity b ? b.getColor() : 0xFFFFFF;
            new com.dbzenith.network.DamageNumberPacket(victim.getId(), (float) dealt, nf, color, source.getEntity().getId()).send(level, victim);
            com.dbzenith.combat.engine.FoeFocus.fought(source.getEntity(), victim);   // for the enemy panel (phase 8)
            if (source.getEntity() instanceof net.minecraft.world.entity.LivingEntity le) com.dbzenith.duel.Duels.onBlow(le, victim, dealt);
            if (victim instanceof com.dbzenith.npc.TrainingDummy td) td.record(dealt);   // its string of blows
        }

        if (attackerData != null && dealt > 0) attackerData.markCombat(victim.level().getGameTime());
        if (attackerData != null && attacker instanceof net.minecraft.server.level.ServerPlayer sp && attacker != victim) {
            com.dbzenith.race.RacialSkillEffects.afterHit(sp, attackerData, dealt);
        }
        if (victimData != null && source.getEntity() != null) victimData.markCombat(victim.level().getGameTime());
        if (victimData != null && victimData.isTransforming() && victim instanceof net.minecraft.server.level.ServerPlayer sp
                && dealt >= victimData.getDerived().maxBody() * DBZConfig.SERVER.transformInterruptDamage.get()) {
            com.dbzenith.transform.FormHandler.interrupt(sp, victimData);     // caught mid power-up
        }

        // 3) training points for the attacker
        if (attackerData != null && attacker != victim && dealt > 0) {
            attackerData.addTrainingProgress(StatCalculator.scaleTpGain(attackerData,
                    Math.sqrt(dealt) * DBZConfig.SERVER.tpPerHit.get()));
        }
    }

    /** God ki over ordinary ki: hits harder against it, takes less from it. */
    public static double godKiFactor(PlayerData attacker, PlayerData victim) {
        boolean a = attacker != null && attacker.hasFlag("god_ki");
        boolean v = victim != null && victim.hasFlag("god_ki");
        double edge = DBZConfig.SERVER.godKiEdge.get();
        if (a && v) {                                           // between gods, the deeper god ki has a smaller edge
            int diff = Math.max(-5, Math.min(5, com.dbzenith.transform.GodKi.level(attacker) - com.dbzenith.transform.GodKi.level(victim)));
            return 1.0 + edge * 0.08 * diff;
        }
        if (a == v) return 1.0;
        return a ? 1.0 + edge : 1.0 - edge;
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (event.getSource().getEntity() instanceof Player killer && killer != event.getEntity()) {
            ModCapabilities.get(killer).ifPresent(data -> {
                data.addTrainingProgress(StatCalculator.scaleTpGain(data,
                        Math.sqrt(com.dbzenith.npc.KiFighter.effectiveMaxHealth(event.getEntity())) * DBZConfig.SERVER.tpPerKill.get()));
                double heal = RacePassives.traits(data).killHeal(); // Majin: absorb the fallen
                if (heal > 0) data.setBody(data.getBody() + data.getDerived().maxBody() * heal);
            });
            LivingEntity fallen = event.getEntity();
            if (killer instanceof net.minecraft.server.level.ServerPlayer sp && (fallen instanceof Player || fallen instanceof com.dbzenith.npc.KiFighter)) {
                com.dbzenith.network.ModNetwork.sendToTrackingAndSelf(sp, new com.dbzenith.network.AnimEventPacket(sp.getId(), com.dbzenith.network.AnimEventPacket.VICTORY, 0));   // a worthy foe: a victory pose
            }
        }
    }
}
