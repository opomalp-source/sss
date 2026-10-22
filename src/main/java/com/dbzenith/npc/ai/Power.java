package com.dbzenith.npc.ai;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.npc.KiFighter;
import com.dbzenith.stats.StatCalculator;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Power as a fighter can judge it, in battle-power units (what a scouter shows): a player's battle power (form and
 * release included), an NPC's level power times what its form and fury add to its blows, a plain mob's guess from its
 * health and bite. {@link #standing} weighs that by the health left: a strong fighter on its last legs is less of a
 * threat. Never the opponent's inputs or hidden numbers.
 */
public final class Power {
    private Power() {}

    public static double of(LivingEntity e) {
        if (e instanceof Player p) {
            PlayerData d = ModCapabilities.get(p).orElse(null);
            if (d == null) return 700;
            d.recomputeIfStale();
            long bp = StatCalculator.battlePower(d);
            // god ki can't be read: a fighter only knows it is at least the character's full power, and feels more
            return d.hasFlag("god_ki") ? StatCalculator.fullPower(d) * 1.5 : Math.max(1, bp);
        }
        if (e instanceof KiFighter k) return levelPower(k.fighterLevel()) * k.powerFactor();
        var attack = e.getAttribute(Attributes.ATTACK_DAMAGE);
        double bite = attack == null ? 1 : attack.getValue();
        return 700 * Math.max(0.15, Math.sqrt(Math.max(1, bite) / 4.0 * Math.max(1, e.getMaxHealth()) / 20.0));
    }

    /** An NPC level's power: the inverse of {@code KiFighter.levelFor}. */
    public static double levelPower(int level) {
        double per;
        try {
            per = DBZConfig.SERVER.enemyPowerPerLevel.get();
        } catch (IllegalStateException e) {
            per = 700;
        }
        return Math.max(1, level) * per;
    }

    /** Health left, 0..1 (a player's body pool). */
    public static double health(LivingEntity e) {
        if (e instanceof Player p) {
            PlayerData d = ModCapabilities.get(p).orElse(null);
            if (d != null) return d.getBody() / Math.max(1, d.getDerived().maxBody());
        }
        return e.getHealth() / Math.max(1f, e.getMaxHealth());
    }

    /** Power weighed by the health left. */
    public static double standing(LivingEntity e) {
        return of(e) * (0.35 + 0.65 * health(e));
    }

    /** The character level an NPC of this level fights like: the inverse of a player's full power at 100% release. */
    public static int equivalentLevel(int npcLevel) {
        int start;
        try {
            start = DBZConfig.SERVER.startingAttribute.get();
        } catch (IllegalStateException e) {
            start = 10;
        }
        return (int) Math.max(0, Math.round(levelPower(npcLevel) / 10.0 - 7.0 * start));
    }
}
