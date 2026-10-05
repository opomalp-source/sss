package com.dbzenith.client.screen;

import com.dbzenith.appearance.FaceParts;
import com.dbzenith.appearance.FaceParts.Part;
import com.dbzenith.client.ClientPlayerData;
import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.client.ui.DbzTheme;
import com.dbzenith.client.ui.ThemedButton;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.FacePacket;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.network.PublicStatePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

/**
 * The face: eyes, brows, mouth, nose, ears and an extra, plus a highlight colour for the hair tips and your own
 * base-form aura colour. A close-up of your head shows every change at once; Done keeps it. Faces are drawn on the
 * generated bodies (pick a skin tone); a full race skin keeps its own painted face.
 */
public class FaceScreen extends Screen {
    private static final int W = 380, H = 214, ROW = 17;
    private static final int[] COLORS = {0xFFFFFF, 0xFFD040, 0xFF9A2A, 0xFF4040, 0xFF70D0, 0xB070FF, 0x5A6AFF, 0x3CC8FF, 0x40D0A0,
            0x7CFF7C, 0xC0C0C8, 0x202028};

    private final Screen parent;
    private final PublicStatePacket original;
    private int face, highlight, aura;
    private boolean kept;
    private int left, top;

    public FaceScreen(Screen parent) {
        super(Component.translatable("screen.dbzenith.face"));
        this.parent = parent;
        PlayerData d = ClientPlayerData.get();
        face = d.getFace();
        highlight = d.getHighlightColor();
        aura = d.getAuraColor();
        original = net.minecraft.client.Minecraft.getInstance().player == null ? null
                : ClientPublicStates.get(net.minecraft.client.Minecraft.getInstance().player.getId());
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        int rx = left + 150;
        Part[] parts = Part.values();
        for (int i = 0; i < parts.length; i++) {
            Part p = parts[i];
            int y = top + 24 + i * ROW;
            addRenderableWidget(ThemedButton.of(Component.literal("<"), b -> change(p, -1)).bounds(rx + 60, y, 14, 14).build());
            addRenderableWidget(ThemedButton.of(Component.literal(">"), b -> change(p, 1)).bounds(rx + 196, y, 14, 14).build());
        }
        addRenderableWidget(ThemedButton.of(Component.translatable("screen.dbzenith.face_random"), b -> {
            java.util.Random r = new java.util.Random();
            for (Part p : Part.values()) if (p != Part.EXTRA || r.nextInt(3) == 0) face = FaceParts.with(face, p, r.nextInt(p.options));
            preview();
        }).bounds(rx, top + H - 22, 70, 16).build());
        addRenderableWidget(ThemedButton.of(Component.translatable("gui.done"), b -> {
            kept = true;
            ModNetwork.sendToServer(new FacePacket(face, highlight, aura));
            PlayerData d = ClientPlayerData.get();                       // at once; the server confirms it
            d.setFace(face);
            d.setHighlightColor(highlight);
            d.setAuraColor(aura);
            onClose();
        }).bounds(left + W - 136, top + H - 22, 62, 16).build());
        addRenderableWidget(ThemedButton.of(Component.translatable("gui.cancel"), b -> onClose()).bounds(left + W - 70, top + H - 22, 62, 16).build());
        preview();
    }

    private void change(Part p, int step) {
        face = FaceParts.with(face, p, FaceParts.get(face, p) + step);
        preview();
    }

    /** Show the choice on your own model while the screen is open. */
    private void preview() {
        if (original == null) return;
        ClientPublicStates.put(original.withFace(face, highlight, aura >= 0 ? aura : original.auraColor()));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int rx = left + 150;
        for (int row = 0; row < 2; row++) {
            int sy = top + 132 + row * 22;
            if (my < sy || my >= sy + 10) continue;
            for (int i = 0; i <= COLORS.length; i++) {
                int sx = rx + 60 + i * 12;
                if (mx >= sx && mx < sx + 10) {
                    int c = i == 0 ? -1 : COLORS[i - 1];
                    if (row == 0) highlight = c;
                    else aura = c;
                    preview();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        DbzTheme.screenBackground(g, width, height);
        DbzTheme.window(g, font, title, left, top, W, H);
        // the close-up: the model drawn large, clipped to the head
        int bx = left + 10, by = top + 22, bw = 130, bh = 150;
        g.fill(bx, by, bx + bw, by + bh, 0xC0080A14);
        if (minecraft.player != null) {
            g.enableScissor(bx, by, bx + bw, by + bh);
            int cx = bx + bw / 2;
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, cx, by + 300, 150, (cx - mouseX) * 0.15f, (by + 40 - mouseY) * 0.15f, minecraft.player);
            g.disableScissor();
        }
        if (!com.dbzenith.client.render.FaceLayer.active(ClientPublicStates.get(minecraft.player.getId()))) {
            DbzTheme.wrapped(g, font, Component.translatable("screen.dbzenith.face_needs_body"), bx + 4, by + bh + 4, bw, DbzTheme.BAD);
        }

        int rx = left + 150;
        Part[] parts = Part.values();
        for (int i = 0; i < parts.length; i++) {
            Part p = parts[i];
            int y = top + 24 + i * ROW;
            DbzTheme.text(g, font, Component.translatable("face.dbzenith.part." + p.key()), rx, y + 3, DbzTheme.DIM, 0.85f);
            Component v = Component.translatable(p.optionKey(FaceParts.get(face, p)));
            DbzTheme.text(g, font, v, rx + 135 - font.width(v) * 0.9f / 2, y + 3, DbzTheme.TEXT, 0.9f);
        }
        swatches(g, rx, top + 132, "screen.dbzenith.face_highlight", highlight, "screen.dbzenith.face_none");
        swatches(g, rx, top + 154, "screen.dbzenith.face_aura", aura, "screen.dbzenith.face_race");
        super.render(g, mouseX, mouseY, partial);
    }

    private void swatches(GuiGraphics g, int rx, int y, String label, int chosen, String noneKey) {
        DbzTheme.text(g, font, Component.translatable(label), rx, y + 1, DbzTheme.DIM, 0.85f);
        int sx = rx + 60;
        g.fill(sx - 1, y - 1, sx + 11, y + 11, chosen < 0 ? 0xFFFFFFFF : 0xFF404048);      // "none": a crossed box
        g.fill(sx, y, sx + 10, y + 10, 0xFF181820);
        for (int k = 1; k < 9; k++) g.fill(sx + k, y + k, sx + k + 1, y + k + 1, 0xFFB04040);
        for (int i = 0; i < COLORS.length; i++) {
            int x = sx + (i + 1) * 12;
            g.fill(x - 1, y - 1, x + 11, y + 11, COLORS[i] == chosen ? 0xFFFFFFFF : 0xFF404048);
            g.fill(x, y, x + 10, y + 10, 0xFF000000 | COLORS[i]);
        }
    }

    @Override
    public void removed() {
        if (!kept && original != null) ClientPublicStates.put(original);   // cancelled: put the old face back
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
