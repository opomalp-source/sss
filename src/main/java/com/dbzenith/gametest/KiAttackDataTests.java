package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.skill.KiAttackData;
import com.dbzenith.skill.Technique;
import com.dbzenith.skill.Techniques;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.Map;

/** Ki attacks as data: every built-in is in a file, a file changes an attack or adds one, and loading again restores. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public class KiAttackDataTests {
    @GameTest(template = "empty", batch = "kidata", timeoutTicks = 20)
    public static void attacksComeFromDataFiles(GameTestHelper h) {
        Map<String, String> shipped = KiAttackData.loaded();
        h.assertTrue(shipped.size() >= 34, "every built-in attack has its file: " + shipped.size());
        h.assertTrue(Techniques.WAVE_BEAM.style() == Technique.Style.BEAM && Techniques.WAVE_BEAM.color() == 0x4FA8FF, "the Kamehameha read from its file");
        Map<String, String> files = new HashMap<>(shipped);
        files.put("ki_blast", "{\"damage\": 9.5, \"look\": {\"glow\": 2.0}}");
        files.put("test_orb", "{\"style\": \"ball\", \"damage\": 2.0, \"color\": \"#FF0000\", \"size\": 1.5}");
        KiAttackData.apply(files);
        h.assertTrue(Techniques.KI_BLAST.damageMult() == 9.5 && Techniques.KI_BLAST.look().glow == 2f, "a file changes a built-in");
        h.assertTrue(Techniques.KI_BLAST.speed() == 1.6f, "what it leaves out stays");
        Technique orb = Techniques.byId("test_orb");
        h.assertTrue(orb != null && orb.color() == 0xFF0000 && orb.size() == 1.5f, "a new file adds an attack");
        KiAttackData.apply(shipped);
        h.assertTrue(Techniques.KI_BLAST.damageMult() == 1.0 && Techniques.KI_BLAST.look().glow == 1f, "loading again restores it");
        h.assertTrue(Techniques.byId("test_orb") == null, "and the added one is gone");
        h.succeed();
    }
}
