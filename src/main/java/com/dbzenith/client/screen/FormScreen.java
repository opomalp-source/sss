package com.dbzenith.client.screen;

import com.dbzenith.client.ui.DbzTheme;
import com.dbzenith.client.ui.ThemedButton;
import com.dbzenith.client.ClientPlayerData;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.network.SelectFormPacket;
import com.dbzenith.stats.Attribute;
import com.dbzenith.transform.Form;
import com.dbzenith.transform.FormHandler;
import com.dbzenith.transform.FormMath;
import com.dbzenith.transform.Forms;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The form tree for your race: status, requirements, mastery, multiplier and drain.
 * Click a form to make it the transform key's target; the server validates everything.
 */
public class FormScreen extends Screen {
    private static final int W = 360;
    private static final int H = 220;
    private static final int ROW = 18;
    private static final int HEADER = 0xFFFFB330;
    private static final int TEXT = 0xFFF0F0F0;
    private static final int DIM = 0xFFA0A0B0;
    private static final int BAD = 0xFFFF7070;
    private static final int GOOD = 0xFF7CFF7C;

    private final Screen parent;
    private final List<Entry> entries = new ArrayList<>();
    private int left;
    private int top;

    private record Entry(Form form, int depth) {}

    public FormScreen(Screen parent) {
        super(Component.translatable("screen.dbzenith.forms"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        entries.clear();
        collect(Forms.BASE.id(), 0, ClientPlayerData.get());
        addRenderableWidget(ThemedButton.of(Component.translatable("screen.dbzenith.clear_target"),
                b -> ModNetwork.sendToServer(new SelectFormPacket(""))).bounds(left + W - 170, top + H - 26, 80, 18).build());
        addRenderableWidget(ThemedButton.of(Component.translatable("gui.back"), b -> onClose())
                .bounds(left + W - 86, top + H - 26, 78, 18).build());
    }

    private void collect(String parentId, int depth, PlayerData d) {
        for (Form f : Forms.children(parentId, d.getRace())) {
            entries.add(new Entry(f, depth));
            collect(f.id(), depth + 1, d);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int i = rowAt(mouseX, mouseY);
        if (i >= 0) {
            Form f = entries.get(i).form();
            if (f.trigger() == Form.Trigger.MANUAL) {
                ModNetwork.sendToServer(new SelectFormPacket(f.id()));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int rowAt(double mx, double my) {
        int y0 = top + 34;
        if (mx < left + 6 || mx > left + W - 6 || my < y0) return -1;
        int i = (int) ((my - y0) / ROW);
        return i < entries.size() ? i : -1;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        DbzTheme.screenBackground(g, width, height);
        DbzTheme.panel(g, left, top, W, H);
        PlayerData d = ClientPlayerData.get();
        DbzTheme.header(g, font, title, left + W / 2, top - 6);
        Form current = Forms.byId(d.getFormId());
        g.drawString(font, Component.translatable("screen.dbzenith.current_form", Component.translatable(current.translationKey())),
                left + 8, top + 20, DIM);

        if (entries.isEmpty()) {
            g.drawString(font, Component.translatable("screen.dbzenith.no_forms"), left + 10, top + 40, DIM);
        }
        int hover = rowAt(mouseX, mouseY);
        Component tooltip = null;
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            Form f = e.form();
            int y = top + 34 + i * ROW;
            boolean selected = f.id().equals(d.getTargetForm());
            if (selected) DbzTheme.row(g, left + 4, y - 3, W - 12, ROW - 1, true, false);
            else if (i == hover) DbzTheme.row(g, left + 4, y - 3, W - 12, ROW - 1, false, true);
            if (f.id().equals(d.getFormId())) g.drawString(font, ">", left + 6, y, GOOD);

            int x = left + 14 + e.depth() * 10;
            int color = 0xFF000000 | (f.hairColor() >= 0 ? f.hairColor() : f.auraColor());
            g.drawString(font, Component.translatable(f.translationKey()), x, y, color);

            Component problem = problem(d, f);
            int sx = left + 170;
            if (f.trigger() == Form.Trigger.MOON) g.drawString(font, Component.translatable("screen.dbzenith.moon_only"), sx, y, DIM);
            else if (problem == null) g.drawString(font, Component.translatable("screen.dbzenith.ready"), sx, y, GOOD);
            else {
                int maxW = left + W - 98 - sx;
                String text = problem.getString();
                String clipped = font.plainSubstrByWidth(text, maxW);
                if (!clipped.equals(text)) {
                    clipped = font.plainSubstrByWidth(text, maxW - font.width("...")) + "...";
                    if (i == hover) tooltip = problem;
                }
                g.drawString(font, clipped, sx, y, BAD);
            }

            double mastery = d.getMastery(f.id());
            int mx = left + W - 92;
            g.fill(mx, y + 2, mx + 40, y + 6, 0xFF2A2A33);
            g.fill(mx, y + 2, mx + (int) (40 * mastery / 100), y + 6, 0xFFFFD040);
            g.drawString(font, String.format("x%.1f", f.multiplier(Attribute.STRENGTH)), mx + 46, y, TEXT);
        }

        int fy = top + H - 46;
        g.drawString(font, Component.translatable("screen.dbzenith.form_keys"), left + 8, fy, DIM);
        int odMax = safeMaxOverdrive(d);
        g.drawString(font, Component.translatable("screen.dbzenith.overdrive_info", odMax,
                String.format("%.0f", d.getMastery(FormMath.OVERDRIVE_MASTERY))), left + 8, fy + 11, DIM);
        super.render(g, mouseX, mouseY, partialTick);
        if (tooltip != null) g.renderTooltip(font, tooltip, mouseX, mouseY);
    }

    private static Component problem(PlayerData d, Form f) {
        try {
            return FormHandler.problem(d, f);
        } catch (IllegalStateException e) { // server config not synced yet
            return Component.literal("?");
        }
    }

    private static int safeMaxOverdrive(PlayerData d) {
        try {
            return FormMath.maxOverdriveLevel(d);
        } catch (IllegalStateException e) {
            return 0;
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
