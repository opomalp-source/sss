package com.dbzenith.nemesis;

import com.dbzenith.npc.KiFighter;
import com.dbzenith.npc.ai.Power;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Sparring with a friend (an ally or a training partner): strike one and it fights back for real, until either is
 * down to 30% (or a minute passes); then it stops, says so, and both are the better for it (the bond grows; it gains
 * experience; it never finishes or chases you). Hitting it again right after a spar is a betrayal ({@code Nemesis.onBlow}).
 */
final class Spars {
    private static final Map<KiFighter, Long> SINCE = new WeakHashMap<>();

    private Spars() {}

    static void tick(NamedFighter c, KiFighter k, long now) {
        long since = SINCE.computeIfAbsent(k, x -> now);
        ServerPlayer p = k.getServer() == null ? null : k.getServer().getPlayerList().getPlayer(k.sparWith);
        if (p == null || p.level() != k.level() || p.distanceTo(k) > 32 || !p.isAlive()) {
            k.sparWith = null;
            k.setTarget(null);
            SINCE.remove(k);
            return;
        }
        if (Power.health(k) < 0.3 || Power.health(p) < 0.3 || now - since > 1200) end(c, k, p, now);
    }

    static void end(NamedFighter c, KiFighter k, ServerPlayer p, long now) {
        SINCE.remove(k);
        k.sparWith = null;
        k.spared = p.getUUID();
        k.lastSparEnd = now;
        k.setTarget(null);
        k.setHealth(Math.max(k.getHealth(), k.getMaxHealth() * 0.4f));
        Bond b = c.player(p.getUUID(), p.getGameProfile().getName());
        b.spars++;
        b.shift(6, 4);
        c.remember(new Memory(Memory.Kind.SPARRED, now, p.getUUID(), ""));
        Progression.gain(c, 20, now);
        Nemesis.refresh(c, b, now);
        Dialogue.say(k, Dialogue.On.SPAR_END, Nemesis.context(c, b, p), p);
        if (k.leaveAt == 0) k.leaveAt = now + 400;
        Nemesis.data().setDirty();
    }
}
