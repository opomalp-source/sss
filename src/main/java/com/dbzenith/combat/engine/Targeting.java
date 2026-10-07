package com.dbzenith.combat.engine;

import com.dbzenith.DBZenith;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lock-on, as the server knows it (CX-19 phase 6). The client picks and frames the target; it tells the server, which
 * checks it (alive, same world, within reach) and lets the moves use it: the super dash goes for the locked foe from
 * any angle and ki blasts curve toward it.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Targeting {
    /** Furthest a lock may reach (blocks); the client lets go a little before this. */
    public static final double MAX_RANGE = 80;
    private static final Map<UUID, Integer> LOCKS = new HashMap<>();

    private Targeting() {}

    /** A client's lock, or -1 to let go. Refused (and cleared) if the target is no good. */
    public static boolean set(ServerPlayer p, int entityId) {
        if (entityId < 0) {
            LOCKS.remove(p.getUUID());
            return true;
        }
        Entity e = p.level().getEntity(entityId);
        if (!valid(p, e)) {
            LOCKS.remove(p.getUUID());
            return false;
        }
        LOCKS.put(p.getUUID(), entityId);
        return true;
    }

    /** The locked target, if it is still good. */
    public static LivingEntity target(ServerPlayer p) {
        Integer id = LOCKS.get(p.getUUID());
        if (id == null) return null;
        Entity e = p.level().getEntity(id);
        if (!valid(p, e)) {
            LOCKS.remove(p.getUUID());
            return null;
        }
        return (LivingEntity) e;
    }

    static boolean valid(ServerPlayer p, Entity e) {
        return e instanceof LivingEntity l && e != p && l.isAlive() && !e.isSpectator() && e.level() == p.level()
                && e.distanceToSqr(p) <= MAX_RANGE * MAX_RANGE;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LOCKS.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        LOCKS.remove(event.getEntity().getUUID());
    }
}
