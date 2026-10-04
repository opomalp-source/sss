package com.dbzenith.client;

import com.dbzenith.skill.Technique;
import com.dbzenith.skill.Techniques;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-only UI state: the selected technique and predicted cooldowns for the HUD.
 * The server stays authoritative; a mispredicted cooldown only affects the HUD bar.
 */
public final class ClientCombatState {
    private static int selected;
    private static final Map<String, long[]> cooldowns = new HashMap<>(); // id -> {start, end}

    private ClientCombatState() {}

    public static Technique selected() {
        List<Technique> all = Techniques.all();
        return all.get(Math.floorMod(selected, all.size()));
    }

    public static Technique cycle() {
        selected = Math.floorMod(selected + 1, Techniques.all().size());
        return selected();
    }

    public static boolean onCooldown(Technique t, long now) {
        long[] cd = cooldowns.get(t.id());
        return cd != null && now < cd[1];
    }

    public static void startCooldown(Technique t, long now) {
        cooldowns.put(t.id(), new long[]{now, now + t.cooldownTicks()});
    }

    /** 0 = ready, 1 = just used. */
    public static float cooldownFraction(Technique t, long now) {
        long[] cd = cooldowns.get(t.id());
        if (cd == null || now >= cd[1]) return 0f;
        return (cd[1] - now) / (float) Math.max(1, cd[1] - cd[0]);
    }

    public static void clear() {
        cooldowns.clear();
    }
}
