package com.dbzenith.skill;

import com.dbzenith.config.DBZConfig;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;

/**
 * A ki attack's explosion (the ki attack rework, phase 6): the damage and the knockback of a vanilla explosion of the
 * same power, but none of its look or sound. Vanilla's grey puffs and its bang are replaced by the mod's own (the
 * {@code EXPLOSION} impact: the flash, the energy dome, the shock ring across the ground, the plume, the layered boom).
 * Blocks break only with {@code kiBlastsBreakBlocks} on (and mob griefing allowed); otherwise the land is left to the
 * craters ({@code [destruction]}).
 */
public final class KiExplosion {
    private KiExplosion() {}

    public static void blast(ServerLevel level, Entity source, double x, double y, double z, float power) {
        boolean breaks = DBZConfig.SERVER.kiBlastsBreakBlocks.get() && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        Explosion explosion = new Explosion(level, source, null, null, x, y, z, power, false,
                breaks ? Explosion.BlockInteraction.DESTROY_WITH_DECAY : Explosion.BlockInteraction.KEEP);
        if (net.minecraftforge.event.ForgeEventFactory.onExplosionStart(level, explosion)) return;
        explosion.explode();
        explosion.finalizeExplosion(false);                                     // on the server: the blocks only, no particles
        for (var hit : explosion.getHitPlayers().keySet()) {                    // a player's knockback is theirs to apply: tell them
            if (hit instanceof ServerPlayer p) p.connection.send(new ClientboundSetEntityMotionPacket(p));
        }
    }
}
