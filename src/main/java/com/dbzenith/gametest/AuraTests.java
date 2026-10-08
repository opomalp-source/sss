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
    private static final String[] FOLDERS = {"", "forms/", "families/", "techniques/"};

    private AuraTests() {}

    /** One aura file as shipped, by id, from whichever folder it sits in (null when there is none). */
    static JsonObject raw(String id) throws Exception {
        for (String folder : FOLDERS) {
            try (InputStream in = AuraTests.class.getResourceAsStream("/assets/" + DBZenith.MOD_ID + "/auras/" + folder + id + ".json")) {
                if (in != null) return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            }
        }
        return null;
    }

    /** An aura read the way the game reads it, {@code extends} and all. */
    static AuraDef load(String id) throws Exception {
        java.util.Map<String, JsonObject> files = new java.util.HashMap<>();
        String at = id;
        for (int i = 0; i < 10 && at != null && !files.containsKey(at); i++) {
            JsonObject j = raw(at);
            if (j == null) break;
            files.put(at, j);
            at = j.has("extends") ? j.get("extends").getAsString() : null;
        }
        if (!files.containsKey(id)) return null;
        java.util.List<String> problems = new java.util.ArrayList<>();
        AuraDef d = AuraDef.parse(id, AuraDef.resolve(id, files, problems::add));
        if (!problems.isEmpty()) throw new IllegalStateException(problems.toString());
        return d;
    }

    /** The aura file each form wears: Blue's own, the base form's, the rest under forms/. Great Apes have none. */
    static String auraFor(String form) {
        if (form.equals("base")) return "base";
        return form;
    }

    @GameTest(template = EMPTY)
    public static void auraFilesAreSound(GameTestHelper helper) throws Exception {
        int checked = 0;
        for (com.dbzenith.transform.Form f : Forms.all()) {
            if (f.id().contains("ape")) continue;                                   // the apes burn no aura, only their size
            AuraDef d = load(auraFor(f.id()));
            helper.assertTrue(d != null, "the form " + f.id() + " has an aura file");
            helper.assertTrue(d.forms.contains(f.id()), d.id + " is worn by " + f.id());
            sound(helper, d);
            checked++;
        }
        helper.assertTrue(checked >= 100, "every form checked: " + checked);
        AuraDef blue = load("super_saiyan_blue");
        helper.assertTrue(!blue.jagged && blue.forms.contains(Forms.SUPER_SAIYAN_BLUE.id()), "Blue: a lobed shell");
        helper.assertTrue((blue.edge & 0xFF) > ((blue.edge >> 16) & 0xFF), "Blue's edge is blue");
        AuraDef gold = load("super_saiyan");
        helper.assertTrue(gold.jagged && ((gold.edge >> 16) & 0xFF) > (gold.edge & 0xFF), "Super Saiyan: jagged and golden");
        AuraDef base = load("base");
        helper.assertTrue(base.followsFighter && !base.idle, "the base aura takes the fighter's colour and only burns while charging");
        AuraDef red = base.tinted(0xFF0000);
        helper.assertTrue(((red.edge >> 16) & 0xFF) > (red.edge & 0xFF), "tinted red, the base aura is red");
        helper.succeed();
    }

    private static void sound(GameTestHelper helper, AuraDef d) {
        String id = d.id;
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
            if (l.kind == AuraDef.Layer.TONGUES) {
                helper.assertTrue(l.tongueCount > 0 && l.tongueCount <= 64 && l.tongueLength > 0 && l.tongueLength < 1, id + " layer " + i + ": sane tongues");
            }
        }
        helper.assertTrue(shell, id + ": at least one flame shell");
        helper.assertTrue(d.react.chargeScale >= 0 && d.react.chargeScale < 1.5f && d.react.trailMax >= 0, id + ": sane reactions");
    }

    /** Kaioken wraps the form's aura, and its x20 tier is bigger and wilder than its x2. */
    @GameTest(template = EMPTY)
    public static void kaiokenTiers(GameTestHelper helper) throws Exception {
        AuraDef k = load("kaioken");
        helper.assertTrue(k != null && "kaioken".equals(k.technique), "Kaioken's aura is a technique's");
        AuraDef low = k.forStage(2), high = k.forStage(20);
        sound(helper, low);
        sound(helper, high);
        helper.assertTrue(low.stageFrom == 1 && high.stageFrom == 11, "x2 is the first tier, x20 the second");
        helper.assertTrue(high.wrapScale > low.wrapScale && high.wrapHeight > low.wrapHeight, "x20 wraps wider and taller");
        helper.assertTrue(high.layers.get(1).spikeSize > low.layers.get(1).spikeSize && high.peak > low.peak, "x20 is wilder");
        helper.assertTrue(k.forStage(20) == high, "a tier is read once and kept");
        helper.assertTrue(low.wrapScale > 1.1f, "it sits outside the form's aura");
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
