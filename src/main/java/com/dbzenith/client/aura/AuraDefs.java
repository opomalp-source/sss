package com.dbzenith.client.aura;

import com.dbzenith.DBZenith;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.io.Reader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * The auras, loaded from {@code assets/<namespace>/auras/*.json} with the resource packs (CX-24): a resource pack can
 * change any aura or add one, and F3+T reloads them. A form wears the aura that lists it under {@code forms}. Files
 * may start from another with {@code "extends"}; /dbzaura set swaps in a changed copy until the next reload.
 */
public final class AuraDefs extends SimplePreparableReloadListener<Map<String, AuraDef>> {
    public static final AuraDefs INSTANCE = new AuraDefs();
    private static final Logger LOG = DBZenith.LOGGER;
    private static volatile Map<String, AuraDef> byId = Map.of();
    private static volatile Map<String, AuraDef> byForm = Map.of();

    private AuraDefs() {}

    public static AuraDef byId(String id) {
        return byId.get(id);
    }

    /** The aura a form wears, or null when it has none (yet). */
    public static AuraDef forForm(String formId) {
        return byForm.get(formId);
    }

    public static Collection<String> ids() {
        return byId.keySet();
    }

    /** Swaps in a changed aura (live tweaking) for everyone wearing it, until the next reload. */
    static void replace(AuraDef d) {
        Map<String, AuraDef> ids = new HashMap<>(byId);
        ids.put(d.id, d);
        Map<String, AuraDef> forms = new HashMap<>(byForm);
        for (String f : d.forms) forms.put(f, d);
        byId = Map.copyOf(ids);
        byForm = Map.copyOf(forms);
    }

    /** Reads every aura file again now, without reloading the other resources (/dbzaura reload). */
    public static int reloadNow() {
        ResourceManager rm = net.minecraft.client.Minecraft.getInstance().getResourceManager();
        Map<String, AuraDef> loaded = INSTANCE.prepare(rm, net.minecraft.util.profiling.InactiveProfiler.INSTANCE);
        INSTANCE.apply(loaded, rm, net.minecraft.util.profiling.InactiveProfiler.INSTANCE);
        return loaded.size();
    }

    @Override
    protected Map<String, AuraDef> prepare(ResourceManager rm, ProfilerFiller profiler) {
        Map<String, JsonObject> raw = new HashMap<>();
        for (Map.Entry<ResourceLocation, Resource> e : rm.listResources("auras", f -> f.getPath().endsWith(".json")).entrySet()) {
            String path = e.getKey().getPath();
            String name = path.substring("auras/".length(), path.length() - ".json".length());
            name = name.substring(name.lastIndexOf('/') + 1);                  // sub-folders (techniques/, families/) only sort files
            String id = e.getKey().getNamespace().equals(DBZenith.MOD_ID) ? name : e.getKey().getNamespace() + ":" + name;
            try (Reader r = e.getValue().openAsReader()) {
                raw.put(id, JsonParser.parseReader(r).getAsJsonObject());
            } catch (Exception ex) {
                LOG.error("Bad aura {}: {}", e.getKey(), ex.toString());
            }
        }
        Map<String, AuraDef> out = new HashMap<>();
        for (String id : raw.keySet()) {
            try {
                out.put(id, AuraDef.parse(id, AuraDef.resolve(id, raw, LOG::error)));
            } catch (Exception ex) {
                LOG.error("Bad aura {}: {}", id, ex.toString());
            }
        }
        return out;
    }

    @Override
    protected void apply(Map<String, AuraDef> loaded, ResourceManager rm, ProfilerFiller profiler) {
        Map<String, AuraDef> forms = new HashMap<>();
        for (AuraDef d : loaded.values()) for (String f : d.forms) forms.put(f, d);
        byId = Map.copyOf(loaded);
        byForm = Map.copyOf(forms);
        LOG.info("Loaded {} auras", loaded.size());
    }
}
