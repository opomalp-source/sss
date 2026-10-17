package com.dbzenith.skill;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;

/**
 * A ki attack's explosion (the ki attack rework, phase 6): the damage and the knockback of a vanilla explosion of the
 * same power, but none of its look or sound. Vanilla's grey puffs and its bang are replaced by the mod's own (the
 * {@code EXPLOSION} impact: the flash, the energy dome, the shock ring across the ground, the plume, the layered boom).
 * Terrain is always handled by the protected crater queue, including the legacy explosion-block setting.
 */
public final class KiExplosion {
    private KiExplosion() {}

    public static void blast(ServerLevel level, Entity source, double x, double y, double z, float power) {
        if(!Float.isFinite(power)||power<=0||!Double.isFinite(x+y+z))return;
        Explosion explosion = new Explosion(level, source, null, null, x, y, z, Math.min(16,power), false,
                Explosion.BlockInteraction.KEEP);
        if (net.minecraftforge.event.ForgeEventFactory.onExplosionStart(level, explosion)) return;
        explosion.explode();
        explosion.finalizeExplosion(false);                                     // on the server: the blocks only, no particles
        for (var hit : explosion.getHitPlayers().keySet()) {                    // a player's knockback is theirs to apply: tell them
            if (hit instanceof ServerPlayer p) p.connection.send(new ClientboundSetEntityMotionPacket(p));
        }
    }
}
