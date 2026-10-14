package com.dbzenith.skill;

import com.dbzenith.DBZenith;
import com.dbzenith.network.ModNetwork;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Ki attacks as data (the ki attack rework, phase 0): {@code data/<namespace>/ki_attacks/<id>.json}, one file an
 * attack. A file named after a built-in technique replaces what it says of it (anything it leaves out keeps the
 * built-in value); any other name adds a new attack. Loaded with the datapacks (so {@code /reload} picks up changes)
 * and sent to every client, which applies the same files. The fields: see {@link Technique#toJson} and {@link KiLook}.
 * {@code /dbz kiattacks export} writes every attack out as files to start from.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID)
public final class KiAttackData {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    /** The built-in attacks as they were before any file touched them. */
    private static final Map<String, JsonObject> DEFAULTS = new LinkedHashMap<>();
    /** What the files said, last load (id to file text), for the clients. */
    private static Map<String, String> loaded = Map.of();

    private KiAttackData() {}

    private static void snapshot() {
        if (!DEFAULTS.isEmpty()) return;
        for (Technique t : Techniques.all()) DEFAULTS.put(t.id(), t.toJson());
    }

    /** Puts every attack back to built-in, then applies the files (both sides use this). */
    public static synchronized void apply(Map<String, String> files) {
        snapshot();
        for (Map.Entry<String, JsonObject> d : DEFAULTS.entrySet()) {
            Technique t = Techniques.byId(d.getKey());
            if (t != null) t.apply(d.getValue());
        }
        for (Technique t : Techniques.all()) if (!DEFAULTS.containsKey(t.id())) Techniques.remove(t.id());   // added by files before
        int added = 0, changed = 0;
        for (Map.Entry<String, String> f : files.entrySet()) {
            JsonObject j;
            try {
                j = JsonParser.parseString(f.getValue()).getAsJsonObject();
            } catch (RuntimeException e) {
                DBZenith.LOGGER.warn("Ki attack {}: not valid JSON ({})", f.getKey(), e.getMessage());
                continue;
            }
            try {
                Technique t = Techniques.byId(f.getKey());
                if (t != null) {
                    t.apply(j);
                    changed++;
                } else {
                    Techniques.register(Technique.fromJson(f.getKey(), j));
                    added++;
                }
            } catch (RuntimeException e) {
                DBZenith.LOGGER.warn("Ki attack {}: {}", f.getKey(), e.getMessage());
            }
        }
        loaded = Map.copyOf(files);
        DBZenith.LOGGER.info("Ki attacks: {} from data files ({} new)", changed + added, added);
    }

    /** What the files said, last load. */
    public static Map<String, String> loaded() {
        return loaded;
    }

    /** Every attack as it stands, ready to write out as files. */
    public static Map<String, String> export() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Technique t : Techniques.all()) if (!t.isCustom()) out.put(t.id(), GSON.toJson(t.toJson()));
        return out;
    }

    // ------------------------------------------------------------------ server: loading with the datapacks

    @SubscribeEvent
    public static void onReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new SimpleJsonResourceReloadListener(GSON, "ki_attacks") {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager manager, ProfilerFiller profiler) {
                Map<String, String> files = new LinkedHashMap<>();
                map.forEach((rl, json) -> files.put(rl.getPath(), GSON.toJson(json)));
                KiAttackData.apply(files);
            }
        });
    }

    /** Joining, or after /reload: the clients get the files too. */
    @SubscribeEvent
    public static void onSync(OnDatapackSyncEvent event) {
        Packet m = new Packet(new HashMap<>(loaded));
        if (event.getPlayer() != null) ModNetwork.sendTo(event.getPlayer(), m);
        else ModNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), m);
    }

    /** Server to client: the ki attack files. */
    public record Packet(Map<String, String> files) {
        public static void encode(Packet m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.files.size());
            m.files.forEach((k, v) -> {
                buf.writeUtf(k, 128);
                buf.writeUtf(v, 1 << 18);
            });
        }

        public static Packet decode(FriendlyByteBuf buf) {
            int n = buf.readVarInt();
            Map<String, String> files = new HashMap<>();
            for (int i = 0; i < n; i++) files.put(buf.readUtf(128), buf.readUtf(1 << 18));
            return new Packet(files);
        }

        public static void handle(Packet m, Supplier<NetworkEvent.Context> ctx) {
            KiAttackData.apply(m.files);
        }
    }
}
