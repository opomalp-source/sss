package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.registry.ModSounds;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** CX-10: every registered sound is described in sounds.json and every variant it names is a real Ogg file. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SoundTests {
    private SoundTests() {}

    @GameTest(template = "empty")
    public static void everySoundHasItsFiles(GameTestHelper helper) {
        try (InputStream in = SoundTests.class.getResourceAsStream("/assets/dbzenith/sounds.json")) {
            helper.assertTrue(in != null, "sounds.json is packaged");
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            int files = 0;
            for (var entry : ModSounds.SOUNDS.getEntries()) {
                String name = entry.getId().getPath();
                helper.assertTrue(json.has(name), name + " is in sounds.json");
                for (var s : json.getAsJsonObject(name).getAsJsonArray("sounds")) {
                    String file = s.getAsString().replace("dbzenith:", "");
                    try (InputStream ogg = SoundTests.class.getResourceAsStream("/assets/dbzenith/sounds/" + file + ".ogg")) {
                        helper.assertTrue(ogg != null, file + ".ogg exists");
                        byte[] head = ogg.readNBytes(4);
                        helper.assertTrue(new String(head, StandardCharsets.US_ASCII).equals("OggS"), file + " is an Ogg stream");
                    }
                    files++;
                }
            }
            helper.assertTrue(files >= 55, "every variant was checked: " + files);
        } catch (java.io.IOException e) {
            helper.fail("could not read the sounds: " + e);
        }
        helper.succeed();
    }
}
