package com.dbzenith.client.screen;

import com.dbzenith.client.ClientPlayerData;
import com.dbzenith.client.ui.Ui;
import com.dbzenith.client.ui.UiButton;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.network.SelectFormPacket;
import com.dbzenith.stats.Attribute;
import com.dbzenith.transform.Form;
import com.dbzenith.transform.FormHandler;
import com.dbzenith.transform.Forms;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * The Forms page of the character menu (UI v3): your race's form tree, each with a colour chip, whether it is ready
 * (or what it still needs), its mastery and its multiplier. Click a form to make it the transform key's target; the
 * server checks everything.
 */
public class FormScreen extends MenuScreen {
    private static final int ROW = 16;
    private final List<Entry> entries = new ArrayList<>();
    private int scroll;

    private record Entry(Form form, int depth) {}

    public FormScreen(Screen parent) {
        super(Component.translatable("screen.dbzenith.forms"), Page.FORMS, parent);
    }

    private int listY() { return cardY + 22; }
    private int visible() { return Math.max(3, (cardH - 22 - 30) / ROW); }

    @Override
    protected void initPage() {
        entries.clear();
        collect(Forms.BASE.id(), 0, ClientPlayerData.get());
        addRenderableWidget(UiButton.of(UiButton.Style.SECONDARY, Component.translatable("screen.dbzenith.clear_target"),
                cardX + cardW - 8 - 90, cardY + 5, 90, 13, b -> ModNetwork.sendToServer(new SelectFormPacket(""))).textScale(0.75f));
    }

    private void collect(String parentId, int depth, PlayerData d) {
        for (Form f : Forms.children(parentId, d.getRace(), d.getVariant())) {
            entries.add(new Entry(f, depth));
            collect(f.id(), depth + 1, d);
        }
    }

    private int rowAt(double mx, double my) {
        if (mx < cardX + 8 || mx > cardX + cardW - 8 || my < listY()) return -1;
        int i = (int) ((my - listY()) / ROW);
        if (i >= visible()) return -1;
        i += scroll;
        return i < entries.size() ? i : -1;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, Math.max(0, entries.size() - visible()));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int i = rowAt(mouseX, mouseY);
        if (i >= 0) {
            Form f = entries.get(i).form();
            if (f.trigger() == Form.Trigger.MANUAL) {
                ModNetwork.sendToServer(new SelectFormPacket(f.id()));
                com.dbzenith.client.ClientSounds.uiClick();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderPage(GuiGraphics g, int mouseX, int mouseY, float partial) {
        PlayerData d = ClientPlayerData.get();
        Form current = Forms.byId(d.getFormId());
        Ui.section(g, font, Component.translatable("screen.dbzenith.current_form", Component.translatable(current.translationKey())),
                cardX + 8, cardY + 8, cardW - 16 - 96);
        int x = cardX + 8, w = cardW - 16;
        if (entries.isEmpty()) Ui.paragraph(g, font, Component.translatable("screen.dbzenith.no_forms"), x, listY() + 4, w, Ui.MUTED, 0.8f, 3);
        int hover = rowAt(mouseX, mouseY);
        Component tooltip = null;
        int statusX = x + (int) (w * 0.44f), masteryX = x + w - 74;
        for (int i = scroll; i < Math.min(entries.size(), scroll + visible()); i++) {
            Entry e = entries.get(i);
            Form f = e.form();
            int y = listY() + (i - scroll) * ROW;
            boolean target = f.id().equals(d.getTargetForm()), now = f.id().equals(d.getFormId());
            Ui.tile(g, x, y, w, ROW - 2, target, i == hover);
            int nx = x + 6 + e.depth() * 9;
            if (e.depth() > 0) g.fill(nx - 6, y + 6, nx - 2, y + 7, 0x40FFFFFF);          // a twig of the tree
            int color = 0xFF000000 | (f.hairColor() >= 0 ? f.hairColor() : f.auraColor());
            Ui.round(g, nx, y + 4, 6, 6, 1, color);
            Ui.text(g, font, Component.translatable(f.translationKey()), nx + 9, y + 3.5f, now ? 0xFFFFFFFF : Ui.TEXT, 0.8f);
            if (now) Ui.text(g, font, Component.translatable("screen.dbzenith.form_now"), nx + 11 + font.width(Component.translatable(f.translationKey())) * 0.8f,
                    y + 4, 0xFF8CE08C, 0.6f);
            Component problem = problem(d, f);
            if (f.trigger() == Form.Trigger.MOON) Ui.text(g, font, Component.translatable("screen.dbzenith.moon_only"), statusX, y + 4, Ui.MUTED, 0.7f);
            else if (problem == null) Ui.text(g, font, Component.translatable("screen.dbzenith.ready"), statusX, y + 4, 0xFF8CE08C, 0.7f);
            else {
                int maxW = (int) ((masteryX - 8 - statusX) / 0.7f);
                String text = problem.getString(), clipped = font.plainSubstrByWidth(text, maxW);
                if (!clipped.equals(text)) {
                    clipped = font.plainSubstrByWidth(text, maxW - font.width("...")) + "...";
                    if (i == hover) tooltip = problem;
                }
                Ui.text(g, font, clipped, statusX, y + 4, 0xFFE07068, 0.7f);
            }
            double mastery = d.getMastery(f.id());
            Ui.round(g, masteryX, y + 5, 40, 3, 1, 0x50000000);
            Ui.round(g, masteryX, y + 5, (int) (40 * mastery / 100), 3, 1, Ui.GOLD);
            Ui.text(g, font, String.format("x%.1f", f.multiplier(Attribute.STRENGTH)), masteryX + 46, y + 3.5f, Ui.TEXT, 0.75f);
        }
        int fy = cardY + cardH - 22;
        g.fill(x, fy - 3, x + w, fy - 2, Ui.LINE_SOFT);
        Ui.text(g, font, Component.translatable("screen.dbzenith.form_keys"), x, fy, Ui.MUTED, 0.7f);
        int kk = com.dbzenith.transform.Kaioken.maxStage(d);
        Ui.text(g, font, kk > 0 ? Component.translatable("screen.dbzenith.kaioken_info", kk) : Component.translatable("screen.dbzenith.kaioken_unlearned"),
                x, fy + 9, Ui.MUTED, 0.7f);
        if (tooltip != null) g.renderTooltip(font, tooltip, mouseX, mouseY);
    }

    private static Component problem(PlayerData d, Form f) {
        try {
            return FormHandler.problem(d, f);
        } catch (IllegalStateException e) { // server config not synced yet
            return Component.literal("?");
        }
    }
}
