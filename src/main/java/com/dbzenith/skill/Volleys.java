package com.dbzenith.skill;

import com.dbzenith.DBZenith;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Barrages (the ki attack rework, phase 5): a technique that throws several balls (Rapid Volley, Chaos Barrage, Ki
 * Creator designs) no longer throws them all in one tick. They stream out one every {@link #GAP} ticks, two from each
 * palm in turn as the volley animation throws them, each along where the caster looks then, so a barrage can be swept
 * across a target. A caster who dies, is stunned or leaves stops firing.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Volleys {
    /** Ticks between shots. */
    public static final int GAP = 2;

    private static final class Volley {
        final ServerLevel level;
        final LivingEntity caster;
        final Technique technique;
        final double damage;
        final float scale;
        final LivingEntity target;
        int fired = 1, wait = GAP;                                       // the first went off as it was cast

        Volley(ServerLevel level, LivingEntity caster, Technique technique, double damage, float scale, LivingEntity target) {
            this.level = level;
            this.caster = caster;
            this.technique = technique;
            this.damage = damage;
            this.scale = scale;
            this.target = target;
        }
    }

    private static final List<Volley> ACTIVE = new ArrayList<>();

    private Volleys() {}

    /** The rest of a barrage whose first shot has just gone. */
    static void queue(ServerLevel level, LivingEntity caster, Technique technique, double damage, float scale, LivingEntity target) {
        ACTIVE.add(new Volley(level, caster, technique, damage, scale, target));
    }

    /** Is this caster still throwing a barrage? */
    public static boolean firing(LivingEntity caster) {
        for (Volley v : ACTIVE) if (v.caster == caster) return true;
        return false;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) return;
        Iterator<Volley> it = ACTIVE.iterator();
        while (it.hasNext()) {
            Volley v = it.next();
            LivingEntity c = v.caster;
            if (!c.isAlive() || c.isRemoved() || c.level() != v.level || com.dbzenith.registry.ModEffects.isStunned(c)) {
                it.remove();
                continue;
            }
            if (--v.wait > 0) continue;
            v.wait = GAP;
            LivingEntity target = v.target != null && v.target.isAlive() ? v.target : null;
            TechniqueHandler.fireOne(v.level, c, v.technique, v.damage, v.scale, target, v.fired);
            if (++v.fired >= v.technique.count()) it.remove();
        }
    }

    @SubscribeEvent
    public static void onServerStopping(net.minecraftforge.event.server.ServerStoppingEvent event) {
        ACTIVE.clear();
    }
}
