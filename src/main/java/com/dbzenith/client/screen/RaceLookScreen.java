package com.dbzenith.client.screen;

import com.dbzenith.appearance.RaceCustom;
import com.dbzenith.client.ClientPlayerData;
import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.client.ui.PortraitRenderer;
import com.dbzenith.client.ui.Ui;
import com.dbzenith.client.ui.UiButton;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.network.PublicStatePacket;
import com.dbzenith.network.RaceLookPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * The race's own look, changed after creation (CX-16b, from the Life screen): skin, markings, the signature part's
 * style and colour, with the character previewed on the left. Save sends it; leaving restores the old look.
 */
public class RaceLookScreen extends Screen {
    private static final int SW = 12, GAP = 4;
    private final Screen parent;
    private int style, skin, mark, part;
    private PublicStatePacket original;
    private boolean saved;
    private int ox, oy, W, H, pw, rx, rw, cx, cw, cy;
    private float yaw = 22;
    private boolean dragging;
    private double lastX;
    private final List<int[]> rowsXY = new ArrayList<>();
    private final List<int[]> rowsColors = new ArrayList<>();
    private final List<java.util.function.IntConsumer> rowsSet = new ArrayList<>();
    private final List<java.util.function.IntSupplier> rowsGet = new ArrayList<>();
    private final List<Object[]> labels = new ArrayList<>();

    public RaceLookScreen(Screen parent) {
        super(Component.translatable("screen.dbzenith.race_look"));
        this.parent = parent;
        PlayerData d = ClientPlayerData.get();
        style = d.getRaceStyle();
        skin = d.getRaceSkinColor();
        mark = d.getRaceMarkColor();
        part = d.getRacePartColor();
    }

    @Override
    protected void init() {
        if (original == null && minecraft.player != null) original = ClientPublicStates.get(minecraft.player.getId());
        W = Math.min(width, 560);
        H = Math.min(height, 300);
        ox = (width - W) / 2;
        oy = (height - H) / 2;
        pw = Mth.clamp((int) (W * 0.38f), 120, 220);
        rx = ox + pw + 4;
        rw = ox + W - rx - 10;
        cx = rx + 10;
        cw = rw - 20;
        cy = oy + 34;
        rowsXY.clear();
        rowsColors.clear();
        rowsSet.clear();
        rowsGet.clear();
        labels.clear();
        PlayerData d = ClientPlayerData.get();
        RaceCustom.Options o = RaceCustom.of(d.getRace(), d.getVariant());
        int y = cy;
        if (o.skin().length > 0) {
            row(Component.translatable("screen.dbzenith.custom_skin"), y, o.skin(), () -> skin, c -> skin = c);
            y += 30;
        }
        if (o.marks().length > 0) {
            row(Component.translatable(o.marksKey()), y, o.marks(), () -> mark, c -> mark = c);
            y += 30;
        }
        int styles = RaceCustom.styles(o.part());
        if (styles > 0) {
            labels.add(new Object[]{Component.translatable("screen.dbzenith.custom_part." + o.part().name().toLowerCase()), y});
            int gap = 4, w = (cw - (styles - 1) * gap) / styles;
            for (int i = 0; i < styles; i++) {
                int s = i;
                addRenderableWidget(UiButton.of(UiButton.Style.CHIP, Component.translatable(RaceCustom.styleKey(o.part(), i)), cx + i * (w + gap), y + 11, w, 14, b -> {
                    style = s;
                    preview();
                    rebuild();
                }).selected(style == i));
            }
            y += 32;
        }
        if (o.partColors().length > 0) {
            row(Component.translatable(o.part() == RaceCustom.Part.TAIL ? "screen.dbzenith.custom_tail" : "screen.dbzenith.custom_horns"), y,
                    o.partColors(), () -> part, c -> part = c);
        }
        addRenderableWidget(UiButton.of(UiButton.Style.PRIMARY, Component.translatable("screen.dbzenith.save"), ox + W - 10 - 100, oy + H - 25, 100, 18, b -> {
            saved = true;
            ModNetwork.sendToServer(new RaceLookPacket(style, skin, mark, part));
            onClose();
        }));
        addRenderableWidget(UiButton.of(UiButton.Style.GHOST, Component.translatable("gui.back"), ox + W - 10 - 100 - 74, oy + H - 25, 70, 18, b -> onClose()));
        preview();
    }

    private void row(Component label, int y, int[] colors, java.util.function.IntSupplier get, java.util.function.IntConsumer set) {
        labels.add(new Object[]{label, y});
        int[] all = new int[colors.length + 1];
        all[0] = -1;
        System.arraycopy(colors, 0, all, 1, colors.length);
        rowsXY.add(new int[]{cx, y + 11});
        rowsColors.add(all);
        rowsGet.add(get);
        rowsSet.add(set);
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private void preview() {
        if (minecraft.player == null || original == null) return;
        ClientPublicStates.put(original.withRaceCustom(style, skin, mark, part));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        int glow = original == null ? 0x5A7ACA : original.auraColor();
        Ui.backdrop(g, width, height, ox + pw / 2f, oy + H * 0.55f, glow);
        int cxp = ox + pw / 2, floor = oy + H - 34;
        Ui.platform(g, cxp, floor, Math.min(60, pw / 2 - 8), 8, glow);
        if (minecraft.player != null) {
            float tall = com.dbzenith.appearance.Stature.heightScale(minecraft.player);
            float s = (H - 90) / (2.15f * Math.max(0.8f, tall));
            PortraitRenderer.draw(g, minecraft.player, cxp, (int) (floor - 1.5f * s * tall), s, yaw, new int[]{ox, oy, ox + pw, oy + H - 20});
        }
        Ui.text(g, font, Component.literal(title.getString().toUpperCase()), rx + 2, oy + 10, Ui.GOLD, 0.85f);
        Ui.card(g, rx, oy + 24, rw, H - 24 - 32);
        for (Object[] l : labels) Ui.section(g, font, (Component) l[0], cx, (int) l[1], cw);
        for (int i = 0; i < rowsXY.size(); i++) {
            Ui.swatches(g, rowsXY.get(i)[0], rowsXY.get(i)[1], SW, GAP, rowsColors.get(i), rowsGet.get(i).getAsInt(), mouseX, mouseY);
        }
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = 0; i < rowsXY.size(); i++) {
            int k = Ui.swatchAt(mx, my, rowsXY.get(i)[0], rowsXY.get(i)[1], SW, GAP, rowsColors.get(i).length);
            if (k >= 0) {
                rowsSet.get(i).accept(rowsColors.get(i)[k]);
                com.dbzenith.client.ClientSounds.uiClick();
                preview();
                return true;
            }
        }
        if (super.mouseClicked(mx, my, button)) return true;
        if (mx >= ox && mx < ox + pw) {
            dragging = true;
            lastX = mx;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            yaw -= (float) (mx - lastX) * 1.6f;
            lastX = mx;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public void renderBackground(GuiGraphics g) {
    }

    @Override
    public void removed() {
        if (!saved && original != null) ClientPublicStates.put(original);
        super.removed();
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
