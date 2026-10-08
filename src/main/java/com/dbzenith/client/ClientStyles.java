package com.dbzenith.client;

import com.dbzenith.style.StyleSlot;
import com.dbzenith.style.Styles;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * The fighting styles each figure moves in, on the client (CX-20): players' chosen styles per slot (from
 * {@code StylePackets.Slots}), and the NPCs' from the style data (a master moves in their own style; a style may name
 * NPC types). The motion engine asks for the clip a slot plays.
 */
public final class ClientStyles {
    private static final Map<Integer, String> CODES = new HashMap<>();
    private static final Map<Integer, Map<StyleSlot, String>> PARSED = new HashMap<>();

    private ClientStyles() {}

    public static void setSlots(int entityId, String code) {
        CODES.put(entityId, code);
        PARSED.remove(entityId);
    }

    public static void clear() {
        CODES.clear();
        PARSED.clear();
    }

    /** The styles a player chose, slot by slot (only the slots not on the default). */
    public static Map<StyleSlot, String> slots(int entityId) {
        return PARSED.computeIfAbsent(entityId, id -> {
            Map<StyleSlot, String> out = new EnumMap<>(StyleSlot.class);
            String code = CODES.getOrDefault(id, "");
            for (String part : code.split(",")) {
                int eq = part.indexOf('=');
                if (eq <= 0) continue;
                StyleSlot slot = StyleSlot.byId(part.substring(0, eq));
                if (slot != null) out.put(slot, part.substring(eq + 1));
            }
            return out;
        });
    }

    /** What decides a figure's style clips, so the motion engine knows when to look them up again. */
    public static String key(LivingEntity e) {
        if (e instanceof Player) return Styles.version() + "|" + CODES.getOrDefault(e.getId(), "");
        return Styles.version() + "|" + npcStyle(e);
    }

    private static String npcStyle(LivingEntity e) {
        ResourceLocation type = ForgeRegistries.ENTITY_TYPES.getKey(e.getType());
        String s = type == null ? null : Styles.npcStyle(type.toString());
        return s == null ? "" : s;
    }

    /** The clip a figure's style plays in {@code slot}, or null for the figure's default. */
    public static String clip(LivingEntity e, StyleSlot slot) {
        String styleId = e instanceof Player ? slots(e.getId()).get(slot) : npcStyle(e);
        Styles.Style s = Styles.style(styleId);
        return s == null ? null : s.clips().get(slot);
    }

    /** A master's screen, opened by the server after a talk. */
    public static void openMaster(int entityId, String master) {
        Minecraft.getInstance().setScreen(new com.dbzenith.client.screen.MasterScreen(entityId, master));
    }
}
