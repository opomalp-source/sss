package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.skill.KiAttackData;
import com.dbzenith.skill.KiBlastEntity;
import com.dbzenith.skill.Technique;
import com.dbzenith.skill.TechniqueHandler;
import com.dbzenith.skill.Techniques;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.Map;

/** The ki attack rework, phase 5: barrages stream out shot by shot; a homing orb shows clients what it seeks. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public class KiAttackKindTests {
    private static int blasts(Zombie caster) {
        return caster.level().getEntitiesOfClass(KiBlastEntity.class, caster.getBoundingBox().inflate(6), b -> b.getOwner() == caster).size();
    }

    @GameTest(template = "empty", batch = "kikinds", timeoutTicks = 60)
    public static void barrageStreamsOut(GameTestHelper h) {
        Map<String, String> shipped = KiAttackData.loaded();
        Map<String, String> files = new HashMap<>(shipped);                     // a slow barrage, so its shots stay close by
        files.put("test_slow_barrage", "{\"style\": \"ball\", \"count\": 6, \"speed\": 0.02, \"size\": 0.3, \"life\": 200, \"destructive\": false}");
        KiAttackData.apply(files);
        Technique barrage = Techniques.byId("test_slow_barrage");
        Zombie caster = h.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 2, 2);
        TechniqueHandler.spawn(h.getLevel(), caster, barrage, 1);
        int first = blasts(caster);
        Delays.after(h, 5, () -> {
            int some = blasts(caster);
            Delays.after(h, 9, () -> {
                int all = blasts(caster);
                KiAttackData.apply(shipped);
                h.assertTrue(first == 1, "only the first shot goes at once, not all six: " + first);
                h.assertTrue(some >= 2 && some < 6, "more stream out, a shot every couple of ticks: " + some);
                h.assertTrue(all == 6, "then all of them: " + all);
                h.succeed();
            });
        });
    }

    @GameTest(template = "empty", batch = "kikinds", timeoutTicks = 20)
    public static void homingOrbShowsItsTarget(GameTestHelper h) {
        Zombie caster = h.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 2, 1);
        Zombie prey = h.spawnWithNoFreeWill(EntityType.ZOMBIE, 2, 2, 4);
        KiBlastEntity orb = KiBlastEntity.create(h.getLevel(), caster, Techniques.byId("homing_orb"), 1);
        orb.setHomingTarget(prey);
        h.assertTrue(orb.seeking() == prey.getId(), "a homing technique's target is carried to clients to mark");
        KiBlastEntity curving = KiBlastEntity.create(h.getLevel(), caster, Techniques.KI_BLAST, 1);
        curving.setHomingTarget(prey, 0.05);
        h.assertTrue(curving.seeking() == -1, "a lock-on curve is not marked");
        h.succeed();
    }
}
