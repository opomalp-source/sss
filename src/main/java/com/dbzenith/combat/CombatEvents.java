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
        Player attacker = source.getEntity() instanceof Player p ? p : null;
        boolean isMelee = !isKi && attacker != null && source.is(DamageTypes.PLAYER_ATTACK) && source.getDirectEntity() == attacker;
        PlayerData attackerData = attacker != null ? ModCapabilities.get(attacker).orElse(null) : null;

        // 1) raw DBZ damage
        double raw;
        int impact = -1; // which hit effect the clients draw (ImpactPacket), -1 for none
        if (isKi || isThrow) {
            raw = event.getAmount(); // ki blasts and throws deal raw DBZ damage
        } else if (isMelee && attackerData != null) {
            attackerData.recomputeIfStale();
            DBZConfig.Server c = DBZConfig.SERVER;
            int combo = attackerData.registerHit(victim.level().getGameTime(), c.comboWindowTicks.get(), c.comboMaxHits.get());
            raw = DamageCalculator.meleeOutgoing(attackerData, event.getAmount(), combo);
            double heavy = attackerData.consumeHeavy(victim.level().getGameTime());
            raw *= heavy;
            attackerData.setStamina(attackerData.getStamina() - c.meleeStaminaCost.get());
            double extraKnockback = attackerData.getAttribute(com.dbzenith.stats.Attribute.STRENGTH) * c.meleeKnockbackPerStrength.get();
            if (heavy > 1.0) {
                extraKnockback += c.heavyKnockback.get();
                victim.level().playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                        net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT, net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.6f);
            }
            boolean aerial = AerialCombat.isAirborne(victim);
            if (aerial) raw *= 1.0 + c.airComboBonus.get();
            if (heavy > 1.0 && AerialCombat.isSpike(attacker)) raw *= 1.0 + c.spikeDamageBonus.get();
            if (extraKnockback > 0 && !aerial) {
                float yaw = attacker.getYRot() * Mth.DEG_TO_RAD;
                victim.knockback(Math.min(3.0, extraKnockback), Mth.sin(yaw), -Mth.cos(yaw));
            }
            AerialCombat.afterHit(attacker, victim, heavy > 1.0, aerial);
            impact = heavy <= 1.0 ? ImpactPacket.PUNCH : AerialCombat.isSpike(attacker) ? ImpactPacket.SPIKE : ImpactPacket.HEAVY;
        } else {
            raw = DamageCalculator.fromVanilla(event.getAmount());
            if (source.getEntity() instanceof com.dbzenith.npc.KiFighter f && source.getDirectEntity() == f) impact = ImpactPacket.PUNCH;
        }

        // 2) apply to victim
        PlayerData victimData = victim instanceof Player vp ? ModCapabilities.get(vp).orElse(null) : null;
        raw *= godKiFactor(attackerData, victimData);
        if (attackerData != null && (isKi || isMelee || isThrow)) raw *= com.dbzenith.race.Alignment.damageMultiplier(attackerData);
        double dealt;
        if (victimData != null) {
            Player player = (Player) victim;
            victimData.recomputeIfStale();
            if (!isKi && !isThrow && !isMelee && !source.is(DamageTypeTags.BYPASSES_ARMOR) && event.getAmount() > 0) {
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
            if (isKi && !afterimage) raw *= RacePassives.absorbKiHit(victimData, raw, victim.level().getGameTime());
            dealt = afterimage ? 0 : DamageCalculator.againstPlayer(raw, victimData, source.getEntity() != null && !isThrow, victim.getRandom());
            dealt *= 1 - victimData.getGearReduction();                          // a full gi or armour set
            if (afterimage) impact = -1;                                         // dodged: nothing landed
            else if (impact >= 0 && victimData.isGuarding()) impact = ImpactPacket.GUARD;
            if (victimData.isGuarding() && dealt > 0) {
                victimData.setStamina(victimData.getStamina() - DamageCalculator.guardPrevented(dealt) * DBZConfig.SERVER.guardStaminaPerDamage.get());
                if (victimData.getStamina() <= 0) victimData.setGuarding(false); // guard broken
            }
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
            float amount = isKi || isThrow || isMelee ? DamageCalculator.toVanilla(raw) : event.getAmount();
            if (victim instanceof com.dbzenith.npc.KiFighter fighter) amount /= (float) fighter.toughness(); // leveled foes
            event.setAmount(amount);
        }

        if (impact >= 0 && source.getEntity() != null && victim.level() instanceof net.minecraft.server.level.ServerLevel level) {
            ImpactPacket.melee(source.getEntity(), victim, impact).send(level);
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
        if (a == v) return 1.0;
        double edge = DBZConfig.SERVER.godKiEdge.get();
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
        }
    }
}
