package com.dbzenith.client.anim;

import com.dbzenith.combat.engine.Fighter;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.client.Minecraft;

/** What the server says each fighter is doing (CX-19e): stunned, launched, knocked down, guarding, or idle. */
public final class FighterStates {
    private static final Int2IntOpenHashMap STATES = new Int2IntOpenHashMap();
    private static Object level;

    private FighterStates() {}

    public static void set(int entityId, int state) {
        check();
        if (state == Fighter.State.IDLE.ordinal()) STATES.remove(entityId);
        else STATES.put(entityId, state);
    }

    public static Fighter.State get(int entityId) {
        check();
        int s = STATES.getOrDefault(entityId, Fighter.State.IDLE.ordinal());
        Fighter.State[] all = Fighter.State.values();
        return s >= 0 && s < all.length ? all[s] : Fighter.State.IDLE;
    }

    /** A new world forgets the old one's states. */
    private static void check() {
        Object now = Minecraft.getInstance().level;
        if (now != level) {
            level = now;
            STATES.clear();
        }
    }
}
