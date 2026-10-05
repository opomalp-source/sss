package com.dbzenith.client;

import com.dbzenith.network.StrugglePacket;
import net.minecraft.client.Minecraft;

/** The beam struggle this client's player is locked in, if any (from {@link StrugglePacket}). */
public final class ClientStruggle {
    private static boolean active;
    private static float balance, shown;
    private static int mine, theirs;
    private static long lastUpdate;
    private static long lastMash;

    private ClientStruggle() {}

    public static void update(StrugglePacket m) {
        active = m.active();
        balance = m.balance();
        mine = m.mine();
        theirs = m.theirs();
        lastUpdate = now();
    }

    /** Active, and heard from the server recently (a lost packet must not leave the bar up forever). */
    public static boolean active() {
        if (active && now() - lastUpdate > 20) active = false;
        return active;
    }

    public static float shownBalance(float dt) {
        shown += (balance - shown) * Math.min(1f, dt * 0.4f);
        return shown;
    }

    public static int mine() {
        return mine;
    }

    public static int theirs() {
        return theirs;
    }

    public static void mashed() {
        lastMash = now();
    }

    public static long sinceMash() {
        return now() - lastMash;
    }

    public static void clear() {
        active = false;
        shown = 0;
    }

    private static long now() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : mc.level.getGameTime();
    }
}
