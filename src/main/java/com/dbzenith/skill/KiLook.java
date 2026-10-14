package com.dbzenith.skill;

import com.google.gson.JsonObject;

/**
 * How a ki attack looks and sounds (the ki attack rework, phase 0): read from its data file's {@code "look"} block, with
 * these defaults for anything left out. Sizes and strengths are multipliers on what the attack's kind does anyway.
 */
public final class KiLook {
    /** How far its colour leans to the fighter's own aura colour (0 its own .. 1 the aura's), so a Kamehameha fired in Super Saiyan Blue is bluer than one fired in Super Saiyan. */
    public float auraMix = 0.35f;
    /** The white-hot core's colour. */
    public int core = 0xFFFFFF;
    /** The soft halo round it, the moving plasma over its surface, the trail behind it, the sparks it sheds, the light it throws on the ground, its flicker. */
    public float glow = 1, plasma = 1, trail = 1, sparks = 1, light = 1, flicker = 1;
    /** Sound events (empty: chosen by its kind): charging, the release, in flight, the impact. */
    public String chargeSound = "", releaseSound = "", travelSound = "", impactSound = "";
    /** The impact: how big its flash, rings and sparks are, how hard the camera shakes, how big the crater is. */
    public float impact = 1, shake = 1, crater = 1;
    /** A big sphere's character (the ki attack rework, phase 4): "auto" (by size), "gather" (energy flowing in, like the
     *  Spirit Bomb), "nova" (flares leaping off it, like the Supernova), "plain" (orbiting sparks only) or "none" (never drawn big). */
    public String sphere = "auto";

    public KiLook copy() {
        KiLook k = new KiLook();
        k.apply(toJson());
        return k;
    }

    public JsonObject toJson() {
        JsonObject j = new JsonObject();
        j.addProperty("aura_mix", auraMix);
        j.addProperty("core", Technique.hex(core));
        j.addProperty("glow", glow);
        j.addProperty("plasma", plasma);
        j.addProperty("trail", trail);
        j.addProperty("sparks", sparks);
        j.addProperty("light", light);
        j.addProperty("flicker", flicker);
        JsonObject s = new JsonObject();
        s.addProperty("charge", chargeSound);
        s.addProperty("release", releaseSound);
        s.addProperty("travel", travelSound);
        s.addProperty("impact", impactSound);
        j.add("sounds", s);
        j.addProperty("impact", impact);
        j.addProperty("shake", shake);
        j.addProperty("crater", crater);
        j.addProperty("sphere", sphere);
        return j;
    }

    public void apply(JsonObject j) {
        if (j.has("aura_mix")) auraMix = j.get("aura_mix").getAsFloat();
        if (j.has("core")) core = Technique.parseColor(j.get("core").getAsString());
        if (j.has("glow")) glow = j.get("glow").getAsFloat();
        if (j.has("plasma")) plasma = j.get("plasma").getAsFloat();
        if (j.has("trail")) trail = j.get("trail").getAsFloat();
        if (j.has("sparks")) sparks = j.get("sparks").getAsFloat();
        if (j.has("light")) light = j.get("light").getAsFloat();
        if (j.has("flicker")) flicker = j.get("flicker").getAsFloat();
        if (j.has("sounds")) {
            JsonObject s = j.getAsJsonObject("sounds");
            if (s.has("charge")) chargeSound = s.get("charge").getAsString();
            if (s.has("release")) releaseSound = s.get("release").getAsString();
            if (s.has("travel")) travelSound = s.get("travel").getAsString();
            if (s.has("impact")) impactSound = s.get("impact").getAsString();
        }
        if (j.has("impact")) impact = j.get("impact").getAsFloat();
        if (j.has("shake")) shake = j.get("shake").getAsFloat();
        if (j.has("crater")) crater = j.get("crater").getAsFloat();
        if (j.has("sphere")) sphere = j.get("sphere").getAsString();
    }
}
