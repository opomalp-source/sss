package com.dbzenith.client.render;

import com.dbzenith.DBZenith;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * The face each race skin had before faces became parts: iris and brow colours and whether the eyes have whites
 * (face_defaults.json, written by ArtGen). FaceLayer uses them on race skins unless the player chose otherwise.
 */
public final class FaceDefaults {
    public record Face(int iris, int brow, boolean whites) {}

    private static Map<String, Face> faces;

    private FaceDefaults() {}

    public static Face of(String skin) {
        if (faces == null) load();
        return skin == null ? null : faces.get(skin);
    }

    private static void load() {
        faces = new HashMap<>();
        var res = Minecraft.getInstance().getResourceManager().getResource(new ResourceLocation(DBZenith.MOD_ID, "face_defaults.json"));
        if (res.isEmpty()) return;
        try (var reader = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            for (var e : json.entrySet()) {
                JsonObject f = e.getValue().getAsJsonObject();
                faces.put(e.getKey(), new Face(f.get("iris").getAsInt(), f.get("brow").getAsInt(), f.get("whites").getAsBoolean()));
            }
        } catch (Exception ex) {
            DBZenith.LOGGER.warn("Could not read face_defaults.json", ex);
        }
    }
}
