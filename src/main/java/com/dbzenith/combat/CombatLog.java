package com.dbzenith.combat;

import com.dbzenith.DBZenith;
import com.dbzenith.network.CombatLogPacket;
import com.dbzenith.network.ModNetwork;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The combat log (CX-19 phase 9): a short line for what happens in a fight, to every player within
 * {@link #RANGE} blocks of it. Knockouts and kills (with how), combos of five hits or more, guard breaks, bursts,
 * ultimates, and duels starting and ending.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CombatLog {
    public static final double RANGE = 64;
    public static final int BIG_COMBO = 5;

    private CombatLog() {}

    /** A line for everyone near {@code at}. */
    public static void near(ServerLevel level, Vec3 at, Component line) {
        for (ServerPlayer p : level.players()) {
            if (p.position().distanceToSqr(at) <= RANGE * RANGE) ModNetwork.sendTo(p, new CombatLogPacket(line));
        }
    }

    public static void near(Entity e, Component line) {
        if (e.level() instanceof ServerLevel level) near(level, e.position(), line);
    }

    /** A combo of {@code hits} worth telling about came to an end. */
    public static void combo(LivingEntity attacker, LivingEntity victim, int hits, double damage) {
        if (hits < BIG_COMBO || attacker == null) return;
        near(victim, Component.translatable("log.dbzenith.combo", attacker.getDisplayName(), hits, victim.getDisplayName(), compact(damage)));
    }

    /** Kills and knockouts a player had a hand in (after everything else had its say: a duel KO is logged by the duel). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.isCanceled()) return;
        LivingEntity victim = event.getEntity();
        Entity killer = event.getSource().getEntity();
        if (!(victim instanceof ServerPlayer) && !(killer instanceof ServerPlayer)) return;
        if (!(victim instanceof ServerPlayer) && !(victim instanceof com.dbzenith.npc.KiFighter)) return;   // not every chicken
        Entity direct = event.getSource().getDirectEntity();
        String how = direct instanceof com.dbzenith.skill.KiBeamEntity ? "beam" : direct instanceof com.dbzenith.skill.KiBlastEntity ? "ki"
                : killer != null && direct == killer ? "melee" : "other";
        Component line = killer instanceof LivingEntity k && k != victim
                ? Component.translatable("log.dbzenith.kill." + how, k.getDisplayName(), victim.getDisplayName())
                : Component.translatable("log.dbzenith.fell", victim.getDisplayName());
        near(victim, line);
    }

    static String compact(double v) {
        if (v < 1000) return String.valueOf(Math.round(v));
        String[] units = {"K", "M", "B", "T"};
        int u = -1;
        while (v >= 1000 && u < units.length - 1) {
            v /= 1000;
            u++;
        }
        return (v < 10 ? String.format("%.1f", v) : String.valueOf((int) v)) + units[u];
    }
}
