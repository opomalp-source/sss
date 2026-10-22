package com.dbzenith.client.screen;

import com.dbzenith.client.ui.Ui;
import com.dbzenith.nemesis.NemesisPackets;
import com.dbzenith.nemesis.Rank;
import com.dbzenith.nemesis.Relationship;
import com.dbzenith.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Nemesis History (a page of the character menu): every named character you have met, and what you have learned
 * of them: their portrait, rank, where you stand with them (and how hot the rivalry runs), your record against each
 * other, the forms you saw them take, the power they showed last, how and where you last saw them, and your story so
 * far. Only what you discovered: the server sends nothing else ({@link NemesisPackets}).
 */
public class RivalsScreen extends MenuScreen {
    private static final int ROW = 22;
    private static List<NemesisPackets.Entry> entries;
    private static RivalsScreen open;
    private final Map<String, LivingEntity> portraits = new HashMap<>();
    private UUID selected;
    private int scroll;

    public RivalsScreen(Screen parent) {
        super(Component.translatable("screen.dbzenith.rivals"), Page.RIVALS, parent);
    }

    /** The server's answer. */
    public static void receive(List<NemesisPackets.Entry> list) {
        entries = list;
        if (open != null && open.selected == null && !list.isEmpty()) open.selected = list.get(0).id();
    }

    @Override
    protected void initPage() {
        open = this;
        entries = null;
        ModNetwork.sendToServer(new NemesisPackets.Request());
    }

    @Override
    public void removed() {
        if (open == this) open = null;
        super.removed();
    }

    private int listW() { return Math.min(170, cardW / 3); }
    private int listY() { return cardY + 22; }
    private int visible() { return Math.max(3, (cardH - 30) / ROW); }

    private int rowAt(double mx, double my) {
        if (entries == null || mx < cardX + 6 || mx > cardX + 6 + listW() || my < listY()) return -1;
        int i = (int) ((my - listY()) / ROW);
        if (i >= visible()) return -1;
        i += scroll;
        return i < entries.size() ? i : -1;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (entries != null) scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, Math.max(0, entries.size() - visible()));
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int i = rowAt(mx, my);
        if (i >= 0) {
            selected = entries.get(i).id();
            com.dbzenith.client.ClientSounds.uiClick();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    static int relationColor(Relationship r) {
        return switch (r) {
            case SWORN_ENEMY -> 0xFFE04848;
            case BITTER_RIVAL -> 0xFFE07848;
            case COMPETITIVE_RIVAL -> 0xFFFFB547;
            case FRIENDLY_RIVAL -> 0xFFE8D860;
            case RESPECTFUL -> 0xFF9CC8F0;
            case ALLY, TRAINING_PARTNER -> 0xFF8CE08C;
            default -> Ui.MUTED;
        };
    }

    @Override
    protected void renderPage(GuiGraphics g, int mouseX, int mouseY, float partial) {
        int x = cardX + 6, lw = listW();
        Ui.section(g, font, Component.translatable("screen.dbzenith.rivals_known", entries == null ? 0 : entries.size()), x + 2, cardY + 8, lw);
        if (entries == null) {
            Ui.text(g, font, Component.translatable("screen.dbzenith.rivals_loading"), x + 4, listY() + 4, Ui.MUTED, 0.8f);
            return;
        }
        if (entries.isEmpty()) {
            Ui.paragraph(g, font, Component.translatable("screen.dbzenith.rivals_none"), x + 4, listY() + 4, cardW - 24, Ui.MUTED, 0.8f, 4);
            return;
        }
        int hover = rowAt(mouseX, mouseY);
        NemesisPackets.Entry sel = null;
        for (NemesisPackets.Entry e : entries) if (e.id().equals(selected)) sel = e;
        if (sel == null) {
            sel = entries.get(0);
            selected = sel.id();
        }
        for (int i = scroll; i < Math.min(entries.size(), scroll + visible()); i++) {
            NemesisPackets.Entry e = entries.get(i);
            int y = listY() + (i - scroll) * ROW;
            Relationship r = Relationship.values()[Mth.clamp(e.relation(), 0, Relationship.values().length - 1)];
            Ui.tile(g, x, y, lw, ROW - 2, e.id().equals(selected), i == hover);
            Ui.round(g, x + 4, y + 4, 3, ROW - 10, 1, relationColor(r));
            Ui.text(g, font, font.plainSubstrByWidth(e.name(), (int) ((lw - 14) / 0.8f)), x + 10, y + 3, Ui.TEXT, 0.8f);
            Ui.text(g, font, Component.translatable("nemesis.dbzenith.relation." + r.id()), x + 10, y + 12, relationColor(r), 0.6f);
        }
        details(g, sel, x + lw + 8, cardY + 8, cardX + cardW - (x + lw + 8) - 8, mouseX, mouseY);
    }

    private void details(GuiGraphics g, NemesisPackets.Entry e, int x, int y, int w, int mouseX, int mouseY) {
        Relationship r = Relationship.values()[Mth.clamp(e.relation(), 0, Relationship.values().length - 1)];
        Rank rank = Rank.values()[Mth.clamp(e.rank(), 0, Rank.values().length - 1)];
        int portraitW = 64;
        LivingEntity body = portrait(e.type(), e.name());
        if (body != null) {
            Ui.round(g, x, y, portraitW, 86, 4, 0x40000000);
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, x + portraitW / 2, y + 80, 34, x + portraitW / 2f - mouseX, y + 30 - mouseY, body);
        }
        int tx = x + portraitW + 8, tw = w - portraitW - 8;
        Ui.text(g, font, e.name(), tx, y, Ui.GOLD, 1.0f);
        Component race = e.race().isEmpty() ? Component.translatable("nemesis.dbzenith.race_unknown") : Component.translatable("race.dbzenith." + e.race());
        Ui.text(g, font, Component.translatable("screen.dbzenith.rival_line", race, Component.translatable(rank.translationKey())), tx, y + 12, Ui.MUTED, 0.75f);
        Ui.text(g, font, Component.translatable("nemesis.dbzenith.relation." + r.id()), tx, y + 24, relationColor(r), 0.9f);
        Ui.text(g, font, Component.translatable("screen.dbzenith.rivalry"), tx, y + 37, Ui.MUTED, 0.7f);
        int bx = tx + 48, bw = Math.max(40, tw - 52);
        Ui.round(g, bx, y + 38, bw, 4, 1, 0x50000000);
        Ui.round(g, bx, y + 38, (int) (bw * e.rivalry() / 100f), 4, 1, 0xFFE07848);
        Ui.text(g, font, Component.translatable("screen.dbzenith.rival_record", e.losses(), e.wins(), e.encounters()), tx, y + 48, Ui.TEXT, 0.75f);
        if (e.sparedThem() > 0 || e.sparedByThem() > 0) {
            Ui.text(g, font, Component.translatable("screen.dbzenith.rival_mercy", e.sparedThem(), e.sparedByThem()), tx, y + 58, Ui.MUTED, 0.7f);
        }
        Ui.text(g, font, Component.translatable("screen.dbzenith.rival_power", e.power() > 0 ? String.format("%,d", e.power()) : "?"), tx, y + 68, Ui.TEXT, 0.75f);
        Component forms;
        if (e.forms().isEmpty()) forms = Component.translatable("screen.dbzenith.rival_no_forms");
        else {
            var c = Component.empty();
            for (int i = 0; i < e.forms().size(); i++) {
                if (i > 0) c.append(", ");
                c.append(Component.translatable("form.dbzenith." + e.forms().get(i)));
            }
            forms = c;
        }
        Ui.text(g, font, Component.translatable("screen.dbzenith.rival_forms", forms), tx, y + 78, Ui.TEXT, 0.7f);
        int sy = y + 94;
        Component when = e.daysAgo() < 0 ? Component.translatable("screen.dbzenith.rival_never")
                : Component.translatable("screen.dbzenith.rival_days_ago", String.format("%.1f", e.daysAgo()));
        Ui.text(g, font, Component.translatable("screen.dbzenith.rival_status", Component.translatable("nemesis.dbzenith.status." + e.status()), when), x, sy, Ui.TEXT, 0.75f);
        if (!e.place().isEmpty()) Ui.text(g, font, Component.translatable("screen.dbzenith.rival_place", e.place()), x, sy + 10, Ui.MUTED, 0.7f);
        Ui.section(g, font, Component.translatable("screen.dbzenith.rival_history"), x, sy + 24, w);
        int hy = sy + 38;
        for (String h : e.history()) {
            if (hy > cardY + cardH - 12) break;
            String[] parts = h.split("\\|", 3);
            Component line = Component.translatable("nemesis.dbzenith.memory." + parts[0], detail(parts[0], parts.length > 1 ? parts[1] : ""));
            String ago = parts.length > 2 ? parts[2] : "";
            Ui.text(g, font, Component.translatable("screen.dbzenith.rival_ago", ago), x, hy, Ui.FAINT, 0.65f);
            Ui.text(g, font, line, x + 42, hy, Ui.TEXT, 0.7f);
            hy += 10;
        }
    }

    /** A memory's detail made readable (a form or technique by its name). */
    static Component detail(String kind, String detail) {
        return switch (kind) {
            case "saw_form", "unlocked_form" -> Component.translatable("form.dbzenith." + detail);
            case "saw_technique" -> {
                var t = com.dbzenith.skill.Techniques.byId(detail);
                yield t != null ? t.name() : Component.translatable("nemesis.dbzenith.ki_blasts");
            }
            case "relation_changed" -> Component.translatable("nemesis.dbzenith.relation." + detail.substring(detail.indexOf('>') + 1));
            default -> Component.literal(detail.startsWith("encounter:") ? "" : detail);
        };
    }

    /** A body to show as a portrait: a client-side copy of its kind (never added to the world). */
    private LivingEntity portrait(String type, String name) {
        return portraits.computeIfAbsent(type + "|" + name, k -> {
            Minecraft mc = Minecraft.getInstance();
            ResourceLocation id = ResourceLocation.tryParse(type);
            EntityType<?> t = id == null ? null : ForgeRegistries.ENTITY_TYPES.getValue(id);
            if (t == null || mc.level == null) return null;
            return t.create(mc.level) instanceof LivingEntity le ? le : null;
        });
    }
}
