package com.dbzenith.combat;

import com.dbzenith.data.PlayerData;
import net.minecraft.world.entity.player.Player;

/**
 * DBC-style body pool. {@link PlayerData#getBody()} is the real health of a player; vanilla health is a
 * mirror showing the same ratio, so hearts, death, totems and other mods keep working.
 *
 * <ul>
 *   <li>DBZ damage is subtracted from body in {@link CombatEvents}, then vanilla health is re-mirrored.</li>
 *   <li>Changes to vanilla health made by anything else (food regen, potions, /kill, other mods) are
 *       adopted back into body on the next tick.</li>
 * </ul>
 */
public final class BodyHealth {
    /** Below this, a living player still shows a sliver of health instead of 0 (which would skip death handling). */
    private static final float MIN_ALIVE_HEALTH = 0.5f;

    private BodyHealth() {}

    /** Adopt external vanilla-health changes into body. Call before modifying body this tick. */
    public static void adoptExternalChanges(Player player, PlayerData data) {
        float last = data.getLastSetHealth();
        float now = player.getHealth();
        if (last >= 0 && Math.abs(now - last) > 1e-3f && player.getMaxHealth() > 0) {
            data.setBody(now / player.getMaxHealth() * data.getDerived().maxBody());
        }
    }

    /** Push body ratio into vanilla health. */
    public static void mirror(Player player, PlayerData data) {
        if (player.isDeadOrDying()) return;
        float expected = expectedHealth(player, data);
        if (Math.abs(player.getHealth() - expected) > 1e-3f) player.setHealth(expected);
        data.setLastSetHealth(player.getHealth());
    }

    public static float expectedHealth(Player player, PlayerData data) {
        double max = data.getDerived().maxBody();
        if (max <= 0) return player.getMaxHealth();
        float h = (float) (player.getMaxHealth() * data.getBody() / max);
        return data.getBody() > 0 ? Math.max(MIN_ALIVE_HEALTH, h) : 0f;
    }
}
