package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.client.aura.AuraDef;
import com.dbzenith.transform.Forms;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** CX-24: the aura files read cleanly, wear real forms and keep sane sizes. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AuraTests {
    private static final String EMPTY = "empty";
    /** Every aura shipped with the mod (assets/dbzenith/auras). */
    static final String[] AURAS = {"super_saiyan_blue"};

    private AuraTests() {}

    static AuraDef load(String id) throws Exception {
        try (InputStream in = AuraTests.class.getResourceAsStream("/assets/" + DBZenith.MOD_ID + "/auras/" + id + ".json")) {
            if (in == null) return null;
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            return AuraDef.parse(id, json);
        }
    }

    @GameTest(template = EMPTY)
    public static void auraFilesAreSound(GameTestHelper helper) throws Exception {
        for (String id : AURAS) {
            AuraDef d = load(id);
            helper.assertTrue(d != null, "the aura " + id + " is there and reads");
            helper.assertTrue(!d.forms.isEmpty(), id + " is worn by some form");
            for (String f : d.forms) helper.assertTrue(Forms.exists(f), id + ": no form called " + f);
            helper.assertTrue(d.width > 0.5f && d.width < 4f && d.height > 1f && d.height < 6f, id + ": a sane size");
            helper.assertTrue(d.widest > 0.05f && d.widest < 0.9f && d.taper > 0 && d.tip > 0, id + ": a sane shape");
            helper.assertTrue(d.coreAlpha >= 0 && d.coreAlpha <= 1 && d.edgeAlpha >= 0 && d.edgeAlpha <= 1, id + ": opacities 0..1");
            helper.assertTrue(d.spikeSize >= 0 && d.spikeSize < 0.6f && d.spikeSharpness >= 0 && d.spikeSharpness <= 1, id + ": sane spikes");
            helper.assertTrue(d.glowScale >= 1f && d.glowScale < 1.5f, id + ": the glow sits just outside");
        }
        AuraDef blue = load("super_saiyan_blue");
        helper.assertTrue(!blue.jagged && blue.forms.contains(Forms.SUPER_SAIYAN_BLUE.id()), "Blue: a lobed shell");
        helper.assertTrue((blue.edge & 0xFF) > ((blue.edge >> 16) & 0xFF), "Blue's edge is blue");
        helper.succeed();
    }
}
