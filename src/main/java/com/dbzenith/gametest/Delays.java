package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Delayed steps for GameTests, safely. Vanilla's {@code runAfterDelay} puts the step straight into the test's timer map,
 * and a step scheduled from inside another one lands in that map while the test runner is walking it: when the map
 * grows it rehashes under the walk and the server crashes (a fastutil NullPointerException, "Exception in server tick
 * loop"), now and then, depending on timing. Steps go through here instead: they are handed to the helper at the start
 * of the next server tick, outside the walk (so they run one tick later than asked, at most).
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID)
public final class Delays {
    private record Step(GameTestHelper helper, long delay, Runnable run) {}

    private static final List<Step> PENDING = new ArrayList<>();

    private Delays() {}

    public static void after(GameTestHelper helper, long delay, Runnable run) {
        synchronized (PENDING) {
            PENDING.add(new Step(helper, delay, run));
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        List<Step> now;
        synchronized (PENDING) {
            if (PENDING.isEmpty()) return;
            now = new ArrayList<>(PENDING);
            PENDING.clear();
        }
        for (Step s : now) s.helper().runAfterDelay(Math.max(0, s.delay() - 1), s.run());
    }
}
