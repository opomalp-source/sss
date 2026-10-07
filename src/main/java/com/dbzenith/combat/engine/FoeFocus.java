package com.dbzenith.combat.engine;

import com.dbzenith.DBZenith;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.FoeStatusPacket;
import com.dbzenith.network.ModNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Who each player is fighting (CX-19 phase 8), for the enemy panel: the locked-on target, else the last thing they hit
 * or were hit by in the last five seconds. Four times a second the player is told that foe's ki, guard and special meter
 * (players only; health the client already sees), or that there is no foe any more.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FoeFocus {
    private static final int MEMORY_TICKS = 100, EVERY = 5;

    private record Focus(int entityId, long until) {}

    private static final Map<UUID, Focus> RECENT = new HashMap<>();
    private static final Map<UUID, Integer> SENT = new HashMap<>();

    private FoeFocus() {}

    /** A blow between {@code attacker} and {@code victim} landed: each is the other's foe for a while. */
    public static void fought(Entity attacker, LivingEntity victim) {
        if (attacker == null || attacker == victim) return;
        long now = victim.level().getGameTime();
        if (attacker instanceof ServerPlayer p) RECENT.put(p.getUUID(), new Focus(victim.getId(), now + MEMORY_TICKS));
        if (victim instanceof ServerPlayer p) {
            Focus f = RECENT.get(p.getUUID());
            if (f == null || f.until < now || f.entityId == attacker.getId()) RECENT.put(p.getUUID(), new Focus(attacker.getId(), now + MEMORY_TICKS));
        }
    }

    /** The foe a player faces now, or null. */
    public static LivingEntity foe(ServerPlayer p) {
        LivingEntity locked = Targeting.target(p);
        if (locked != null) return locked;
        Focus f = RECENT.get(p.getUUID());
        if (f == null || f.until < p.level().getGameTime()) return null;
        return p.level().getEntity(f.entityId) instanceof LivingEntity l && l.isAlive() && l.distanceToSqr(p) < 64 * 64 ? l : null;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % EVERY != 0) return;
        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
            LivingEntity foe = foe(p);
            if (foe == null) {
                if (SENT.remove(p.getUUID()) != null) ModNetwork.sendTo(p, new FoeStatusPacket(-1, -1, -1, -1));
                continue;
            }
            float ki = -1, guard = -1, special = -1;
            if (foe instanceof Player fp) {
                PlayerData d = ModCapabilities.get(fp).orElse(null);
                if (d != null) {
                    ki = (float) (d.getKi() / Math.max(1, d.getDerived().maxKi()));
                    guard = (float) (d.getGuardMeter() / 100);
                    special = (float) d.getSpecial();
                }
            }
            SENT.put(p.getUUID(), foe.getId());
            ModNetwork.sendTo(p, new FoeStatusPacket(foe.getId(), ki, guard, special));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        RECENT.remove(event.getEntity().getUUID());
        SENT.remove(event.getEntity().getUUID());
    }
}
