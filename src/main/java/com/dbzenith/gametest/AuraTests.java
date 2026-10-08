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
            helper.assertTrue(d.peak >= 0 && d.peak < 1.5f && d.flare >= 0 && d.flare < 1f, id + ": a sane peak and flare");
            helper.assertTrue(!d.layers.isEmpty() && d.layers.size() <= 6, id + ": one to six layers");
            boolean shell = false;
            for (int i = 0; i < d.layers.size(); i++) {
                AuraDef.Layer l = d.layers.get(i);
                shell |= l.kind == AuraDef.Layer.SHELL;
                helper.assertTrue(l.coreAlpha >= 0 && l.coreAlpha <= 1 && l.edgeAlpha >= 0 && l.edgeAlpha <= 1, id + " layer " + i + ": opacities 0..1");
                helper.assertTrue(l.spikeSize >= 0 && l.spikeSize < 0.6f && l.spikeSharpness >= 0 && l.spikeSharpness <= 1, id + " layer " + i + ": sane spikes");
                helper.assertTrue(l.scale > 0.2f && l.scale < 3f, id + " layer " + i + ": a sane scale");
                if (l.kind == AuraDef.Layer.GLOW) {
                    helper.assertTrue(l.scale >= 1f && l.scale < 1.5f, id + " layer " + i + ": the glow sits just outside");
                    helper.assertTrue(d.wrapped(i) != null, id + " layer " + i + ": the glow wraps a shell");
                }
            }
            helper.assertTrue(shell, id + ": at least one flame shell");
            helper.assertTrue(d.react.chargeScale >= 0 && d.react.chargeScale < 1.5f && d.react.trailMax >= 0, id + ": sane reactions");
        }
        AuraDef blue = load("super_saiyan_blue");
        helper.assertTrue(!blue.jagged && blue.forms.contains(Forms.SUPER_SAIYAN_BLUE.id()), "Blue: a lobed shell");
        helper.assertTrue((blue.edge & 0xFF) > ((blue.edge >> 16) & 0xFF), "Blue's edge is blue");
        helper.succeed();
    }

    /** A file that extends another takes its values and overrides some, and never inherits who wears it. */
    @GameTest(template = EMPTY)
    public static void auraExtendsMerges(GameTestHelper helper) {
        JsonObject parent = JsonParser.parseString("{\"forms\":[\"super_saiyan\"],\"shape\":{\"width\":1.6,\"height\":2.4},"
                + "\"layers\":[{\"kind\":\"shell\"}]}").getAsJsonObject();
        JsonObject child = JsonParser.parseString("{\"extends\":\"parent\",\"shape\":{\"height\":3.0}}").getAsJsonObject();
        java.util.Map<String, JsonObject> raw = new java.util.HashMap<>();
        raw.put("parent", parent);
        raw.put("child", child);
        java.util.List<String> problems = new java.util.ArrayList<>();
        AuraDef d = AuraDef.parse("child", AuraDef.resolve("child", raw, problems::add));
        helper.assertTrue(problems.isEmpty(), "no problems: " + problems);
        helper.assertTrue(d.width == 1.6f && d.height == 3.0f, "width from the parent, height its own");
        helper.assertTrue(d.forms.isEmpty() && d.layers.size() == 1, "the parent's layers, but not who wears it");
        raw.put("loop", JsonParser.parseString("{\"extends\":\"loop\"}").getAsJsonObject());
        AuraDef.resolve("loop", raw, problems::add);
        helper.assertTrue(!problems.isEmpty(), "a loop is reported, not hung on");
        helper.succeed();
    }
}
