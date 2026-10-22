package com.dbzenith.client.screen;

import com.dbzenith.client.ui.Ui;
import com.dbzenith.client.ui.UiButton;
import com.dbzenith.living.ConversationPackets;
import com.dbzenith.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Talking with a townsperson (docs/LIVING_WORLD.md): who they are (name, trade, mood, how they feel about you, their
 * town) over what they say, and your answers under it, picked with the mouse or the number keys. The world stays in
 * view; nothing pauses. The server sends every line and every answer you may give, and checks what you pick.
 */
public class DialogueScreen extends Screen {
    private static final int MAX_W = 360, OPTION_H = 14, GAP = 2;
    private ConversationPackets.Show shown;
    private int left, top, panelW, textH;

    DialogueScreen(ConversationPackets.Show shown) {
        super(Component.translatable("screen.dbzenith.dialogue"));
        this.shown = shown;
    }

    /** A line from the server: shown (the open screen updated), or the conversation closed (an empty one). */
    public static void receive(ConversationPackets.Show m) {
        Minecraft mc = Minecraft.getInstance();
        boolean open = mc.screen instanceof DialogueScreen;
        if (m.text().isEmpty() && m.options().isEmpty()) {
            if (open) mc.setScreen(null);
            return;
        }
        if (open && ((DialogueScreen) mc.screen).shown.entity() == m.entity()) {
            DialogueScreen d = (DialogueScreen) mc.screen;
            if (!m.name().isEmpty()) d.shown = m;
            else d.shown = new ConversationPackets.Show(m.entity(), d.shown.name(), d.shown.subtitle(), m.text(), m.options());
            d.rebuildWidgets();
            return;
        }
        mc.setScreen(new DialogueScreen(m));
    }

    @Override
    protected void init() {
        panelW = Math.min(MAX_W, width - 20);
        left = (width - panelW) / 2;
        List<String> opts = shown.options();
        textH = Math.max(20, font.split(Component.literal(shown.text()), panelW - 20).size() * 10 + 6);
        int cols = opts.size() > 3 && panelW >= 300 ? 2 : 1;                           // two columns keep the panel low
        int rows = (Math.max(1, opts.size()) + cols - 1) / cols;
        int optsH = rows * (OPTION_H + GAP);
        int total = 24 + textH + 6 + optsH + 6;
        top = Math.max(8, height - total - 8);
        int y = top + 24 + textH + 6, colW = (panelW - 20 - (cols - 1) * 4) / cols;
        for (int i = 0; i < opts.size(); i++) {
            int index = i, col = cols == 1 ? 0 : i / rows, row = cols == 1 ? i : i % rows;
            addRenderableWidget(UiButton.of(UiButton.Style.SECONDARY, Component.literal((i + 1) + ".  " + opts.get(i)), left + 10 + col * (colW + 4),
                    y + row * (OPTION_H + GAP), colW, OPTION_H, b -> pick(index)).textScale(0.85f));
        }
        if (opts.isEmpty()) {                                                            // a last word: nothing to answer
            addRenderableWidget(UiButton.of(UiButton.Style.GHOST, Component.translatable("screen.dbzenith.dialogue_leave"), left + 10, y,
                    panelW - 20, OPTION_H, b -> onClose()));
        }
    }

    void pick(int index) {
        if (index < 0 || index >= shown.options().size()) return;
        ModNetwork.sendToServer(new ConversationPackets.Choose(shown.entity(), index));
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_9) {
            pick(key - GLFW.GLFW_KEY_1);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void onClose() {
        if (!shown.options().isEmpty()) ModNetwork.sendToServer(new ConversationPackets.Leave());
        super.onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fillGradient(0, height / 2, width, height, 0x00000000, 0xA0000000);         // the world stays in view
        int h = height - top - 12;
        Ui.card(g, left, top, panelW, h);
        Ui.text(g, font, shown.name(), left + 10, top + 7, Ui.GOLD, 1f);
        Ui.text(g, font, subtitle(), left + 12 + font.width(shown.name()), top + 8.5f, Ui.MUTED, 0.75f);
        g.fill(left + 10, top + 20, left + panelW - 10, top + 21, Ui.LINE_SOFT);
        Ui.paragraph(g, font, Component.literal(shown.text()), left + 10, top + 24, panelW - 20, Ui.TEXT, 1f, 12);
        super.render(g, mouseX, mouseY, partial);
    }

    /** "Merchant · relaxed · friendly rival · Ginger Town". */
    String subtitle() {
        String[] p = shown.subtitle().split("\\|", -1);
        if (p.length < 4) return "";
        StringBuilder b = new StringBuilder();
        b.append(Component.translatable("living.dbzenith.occupation." + p[0]).getString());
        b.append("  ·  ").append(Component.translatable("living.dbzenith.mood." + p[1]).getString());
        b.append("  ·  ").append(Component.translatable("nemesis.dbzenith.relation." + p[2]).getString());
        if (!p[3].isEmpty()) b.append("  ·  ").append(p[3]);
        return b.toString();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
