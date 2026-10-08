package com.dbzenith.style;

import com.dbzenith.DBZenith;
import com.dbzenith.network.ModNetwork;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Fighting styles and the masters who teach them (CX-20), from data:
 * <ul>
 *   <li>{@code data/<ns>/styles/<id>.json}: the master who teaches it, the clip it plays in each animation slot
 *       ({@link StyleSlot}), what learning it takes (battle power, the master's affinity, minutes trained with them,
 *       items given, a quest done, a race, a flag), the NPC types that move this way, and a colour for the UI.</li>
 *   <li>{@code data/<ns>/masters/<id>.json}: the style the master moves in, what they like as gifts, a colour.</li>
 * </ul>
 * Names and descriptions are lang keys: {@code style.<ns>.<id>} (+ {@code .desc}), {@code master.<ns>.<id>.greet}.
 * Clients get both on joining and after {@code /reload}: they draw the styles and check requirements in the screens
 * (the server checks again).
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class Styles {
    public record ItemReq(String item, int count) {}

    public record Requirements(long battlePower, int affinity, int trainingMinutes, List<ItemReq> items, String quest,
                               List<String> races, String flag) {
        static Requirements parse(JsonObject j) {
            List<ItemReq> items = new ArrayList<>();
            if (j.has("items")) for (JsonElement e : j.getAsJsonArray("items")) {
                JsonObject o = e.getAsJsonObject();
                items.add(new ItemReq(GsonHelper.getAsString(o, "item"), GsonHelper.getAsInt(o, "count", 1)));
            }
            List<String> races = new ArrayList<>();
            if (j.has("races")) for (JsonElement e : j.getAsJsonArray("races")) races.add(e.getAsString());
            return new Requirements(GsonHelper.getAsLong(j, "battle_power", 0), GsonHelper.getAsInt(j, "affinity", 0),
                    GsonHelper.getAsInt(j, "training_minutes", 0), List.copyOf(items), GsonHelper.getAsString(j, "quest", ""),
                    List.copyOf(races), GsonHelper.getAsString(j, "flag", ""));
        }
    }

    public record Style(String id, String master, Map<StyleSlot, String> clips, Requirements requirements, List<String> npcs, int color) {
        public String nameKey() {
            return "style.dbzenith." + id;
        }

        public String descKey() {
            return "style.dbzenith." + id + ".desc";
        }

        public boolean has(StyleSlot slot) {
            return clips.containsKey(slot);
        }
    }

    public record Master(String id, String uses, List<String> likes, int color) {
        public String nameKey() {
            return "entity.dbzenith." + id;
        }

        public String greetKey() {
            return "master.dbzenith." + id + ".greet";
        }
    }

    private static Map<String, Style> styles = Map.of();
    private static Map<String, Master> masters = Map.of();
    private static Map<String, String> npcStyles = Map.of();
    private static String stylesJson = "{}", mastersJson = "{}";
    private static int version;

    private Styles() {}

    public static Style style(String id) {
        return id == null ? null : styles.get(id);
    }

    public static Collection<Style> all() {
        return styles.values();
    }

    public static Master master(String id) {
        return id == null ? null : masters.get(id);
    }

    public static Collection<Master> masters() {
        return masters.values();
    }

    /** The styles a master teaches, in name order. */
    public static List<Style> taughtBy(String master) {
        List<Style> out = new ArrayList<>();
        for (Style s : styles.values()) if (master.equals(s.master())) out.add(s);
        return out;
    }

    /** The style an NPC type moves in (its {@code entity type id}), or null. */
    public static String npcStyle(String entityType) {
        return npcStyles.get(entityType);
    }

    /** Bumped on every reload or sync, so the motion engine looks its clips up again. */
    public static int version() {
        return version;
    }

    public static String stylesJson() {
        return stylesJson;
    }

    public static String mastersJson() {
        return mastersJson;
    }

    /** Takes the two merged JSON objects ({id: file}): the server's reload, or a client receiving them. */
    public static void apply(String stylesIn, String mastersIn) {
        try {
            Map<String, Style> s = new TreeMap<>();
            Map<String, String> npc = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> e : JsonParser.parseString(stylesIn).getAsJsonObject().entrySet()) {
                Style st = parseStyle(e.getKey(), e.getValue().getAsJsonObject());
                s.put(st.id(), st);
                for (String n : st.npcs()) npc.put(n, st.id());
            }
            Map<String, Master> m = new TreeMap<>();
            for (Map.Entry<String, JsonElement> e : JsonParser.parseString(mastersIn).getAsJsonObject().entrySet()) {
                JsonObject j = e.getValue().getAsJsonObject();
                List<String> likes = new ArrayList<>();
                if (j.has("likes")) for (JsonElement l : j.getAsJsonArray("likes")) likes.add(l.getAsString());
                m.put(e.getKey(), new Master(e.getKey(), GsonHelper.getAsString(j, "uses", ""), List.copyOf(likes), color(j, 0xFFC040)));
                if (!GsonHelper.getAsString(j, "uses", "").isEmpty()) npc.put(DBZenith.MOD_ID + ":" + e.getKey(), GsonHelper.getAsString(j, "uses"));
            }
            styles = s;
            masters = m;
            npcStyles = npc;
            stylesJson = stylesIn;
            mastersJson = mastersIn;
            version++;
        } catch (RuntimeException ex) {
            com.mojang.logging.LogUtils.getLogger().warn("Styles: bad data, keeping the old: {}", ex.toString());
        }
    }

    static Style parseStyle(String id, JsonObject j) {
        Map<StyleSlot, String> clips = new EnumMap<>(StyleSlot.class);
        for (Map.Entry<String, JsonElement> c : GsonHelper.getAsJsonObject(j, "slots", new JsonObject()).entrySet()) {
            StyleSlot slot = StyleSlot.byId(c.getKey());
            if (slot != null) clips.put(slot, c.getValue().getAsString());
        }
        List<String> npcs = new ArrayList<>();
        if (j.has("npcs")) for (JsonElement e : j.getAsJsonArray("npcs")) npcs.add(e.getAsString());
        return new Style(id, GsonHelper.getAsString(j, "master", ""), clips,
                Requirements.parse(GsonHelper.getAsJsonObject(j, "requirements", new JsonObject())), List.copyOf(npcs), color(j, 0xFFC040));
    }

    private static int color(JsonObject j, int fallback) {
        if (!j.has("color")) return fallback;
        try {
            return Integer.parseInt(j.get("color").getAsString().replace("#", ""), 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String collect(ResourceManager rm, String dir) {
        JsonObject out = new JsonObject();
        new TreeMap<>(rm.listResources(dir, f -> f.getPath().endsWith(".json"))).forEach((rl, res) -> {
            String id = rl.getPath().substring(dir.length() + 1, rl.getPath().length() - ".json".length());
            try (java.io.Reader r = res.openAsReader()) {
                JsonElement j = JsonParser.parseReader(r);
                if (j.isJsonObject()) out.add(id, j);
            } catch (Exception e) {
                com.mojang.logging.LogUtils.getLogger().error("Styles: could not read {}: {}", rl, e.toString());
            }
        });
        return out.toString();
    }

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        event.addListener(new net.minecraft.server.packs.resources.SimplePreparableReloadListener<String[]>() {
            @Override
            protected String[] prepare(ResourceManager rm, ProfilerFiller profiler) {
                return new String[]{collect(rm, "styles"), collect(rm, "masters")};
            }

            @Override
            protected void apply(String[] data, ResourceManager rm, ProfilerFiller profiler) {
                Styles.apply(data[0], data[1]);
                com.mojang.logging.LogUtils.getLogger().info("Styles: {} styles, {} masters", styles.size(), masters.size());
            }
        });
    }

    /** Every client gets the styles and masters on joining and after a reload. */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        var packet = new StylePackets.Data(stylesJson, mastersJson);
        if (event.getPlayer() != null) ModNetwork.sendTo(event.getPlayer(), packet);
        else for (ServerPlayer p : event.getPlayerList().getPlayers()) ModNetwork.sendTo(p, packet);
    }
}
