package com.dbzenith.client.screen;

import com.dbzenith.appearance.HairCode;
import com.dbzenith.appearance.HairCode.Bend;
import com.dbzenith.appearance.HairCode.Face;
import com.dbzenith.appearance.HairCode.Strand;
import com.dbzenith.appearance.Palettes;
import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.client.ui.DbzTheme;
import com.dbzenith.client.ui.PortraitRenderer;
import com.dbzenith.client.ui.ThemedButton;
import com.dbzenith.network.PublicStatePacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * The barber: build hair strand by strand on an unfolded head (top in the middle, the face below, the back above,
 * the sides left and right). Click a square to plant a strand or pick one, right-click to pull it; tune the picked
 * strand's angle, length, thickness and curve; start from a preset; mirror one side onto the other; copy the hair code
 * to share it or paste one in. The live preview on the right turns when dragged.
 */
public class HairEditorScreen extends Screen {
    private static final int W = 404, H = 236, CELL = 6, FACE = CELL * 8;
    private static final Component[] FACE_NAMES = {Component.translatable("hair.dbzenith.face.top"), Component.translatable("hair.dbzenith.face.front"),
            Component.translatable("hair.dbzenith.face.back"), Component.translatable("hair.dbzenith.face.left"), Component.translatable("hair.dbzenith.face.right")};

    private final Screen parent;
    private final BiConsumer<String, Integer> onDone;
    private final List<Strand> strands = new ArrayList<>();
    private final PublicStatePacket original;
    private int selected = -1;
    private int color;
    private int preset;
    private float turn = 25;
    private boolean dragging;
    private Component status = Component.empty();
    private int left, top;
    private ThemedButton curveButton;

    /** @param onDone receives the finished hair code and colour (not called on cancel) */
    public HairEditorScreen(Screen parent, String code, int color, BiConsumer<String, Integer> onDone) {
        super(Component.translatable("screen.dbzenith.hair_editor"));
        this.parent = parent;
        this.onDone = onDone;
        this.color = color;
        List<Strand> s = HairCode.decode(code);
        if (s != null) strands.addAll(s);
        var mc = net.minecraft.client.Minecraft.getInstance();
        original = mc.player == null ? null : ClientPublicStates.get(mc.player.getId());
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        int cx = left + 168;
        int y = top + 40;
        String[] rows = {"yaw", "pitch", "length", "width"};
        for (int i = 0; i < rows.length; i++) {
            int field = i;
            addRenderableWidget(ThemedButton.of(Component.literal("-"), b -> adjust(field, -1)).bounds(cx + 52, y, 16, 14).build());
            addRenderableWidget(ThemedButton.of(Component.literal("+"), b -> adjust(field, 1)).bounds(cx + 100, y, 16, 14).build());
            y += 17;
        }
        curveButton = addRenderableWidget(ThemedButton.of(Component.empty(), b -> adjust(4, 1)).bounds(cx, y, 116, 14).build());
        y += 20;
        addRenderableWidget(ThemedButton.of(Component.translatable("hair.dbzenith.delete"), b -> delete()).bounds(cx, y, 56, 14).build());
        addRenderableWidget(ThemedButton.of(Component.translatable("hair.dbzenith.mirror"), b -> mirror()).bounds(cx + 60, y, 56, 14).build());
        y += 18;
        addRenderableWidget(ThemedButton.of(Component.translatable("hair.dbzenith.clear"), b -> {
            strands.clear();
            selected = -1;
            preview();
        }).bounds(cx, y, 56, 14).build());

        // presets
        int py = top + H - 46;
        addRenderableWidget(ThemedButton.of(Component.literal("<"), b -> applyPreset(preset - 1)).bounds(left + 8, py, 14, 14).build());
        addRenderableWidget(ThemedButton.of(Component.literal(">"), b -> applyPreset(preset + 1)).bounds(left + 118, py, 14, 14).build());
        // code sharing and finishing
        int by = top + H - 24;
        addRenderableWidget(ThemedButton.of(Component.translatable("hair.dbzenith.copy"), b -> {
            minecraft.keyboardHandler.setClipboard(HairCode.encode(strands));
            status = Component.translatable("hair.dbzenith.copied");
        }).bounds(left + 8, by, 62, 18).build());
        addRenderableWidget(ThemedButton.of(Component.translatable("hair.dbzenith.paste"), b -> paste()).bounds(left + 74, by, 62, 18).build());
        addRenderableWidget(ThemedButton.of(Component.translatable("gui.cancel"), b -> onClose()).bounds(left + W - 142, by, 64, 18).build());
        addRenderableWidget(ThemedButton.of(Component.translatable("gui.done"), b -> {
            onDone.accept(HairCode.encode(strands), color);
            minecraft.setScreen(parent);
        }).bounds(left + W - 74, by, 66, 18).build());
        preview();
    }

    // ------------------------------------------------------------------ editing

    private void adjust(int field, int delta) {
        if (selected < 0 || selected >= strands.size()) return;
        Strand s = strands.get(selected);
        Strand n = switch (field) {
            case 0 -> s.with(s.yaw() + delta, s.pitch(), s.length(), s.width(), s.bend());
            case 1 -> s.with(s.yaw(), s.pitch() + delta, s.length(), s.width(), s.bend());
            case 2 -> s.with(s.yaw(), s.pitch(), s.length() + delta, s.width(), s.bend());
            case 3 -> s.with(s.yaw(), s.pitch(), s.length(), s.width() + delta, s.bend());
            default -> s.with(s.yaw(), s.pitch(), s.length(), s.width(), Bend.values()[(s.bend().ordinal() + 1) % Bend.values().length]);
        };
        strands.set(selected, n);
        preview();
    }

    private void delete() {
        if (selected < 0 || selected >= strands.size()) return;
        strands.remove(selected);
        selected = Math.min(selected, strands.size() - 1);
        preview();
    }

    /** Copies every strand onto the opposite side (left and right swap; top, face and back flip across the middle). */
    private void mirror() {
        List<Strand> add = new ArrayList<>();
        for (Strand s : strands) {
            Strand m = switch (s.face()) {
                // the two sides run in opposite directions along z, so the mirror flips u and yaw
                case LEFT -> new Strand(Face.RIGHT, 7 - s.u(), s.v(), -s.yaw(), s.pitch(), s.length(), s.width(), s.bend());
                case RIGHT -> new Strand(Face.LEFT, 7 - s.u(), s.v(), -s.yaw(), s.pitch(), s.length(), s.width(), s.bend());
                default -> new Strand(s.face(), 7 - s.u(), s.v(), -s.yaw(), s.pitch(), s.length(), s.width(), s.bend());
            };
            if (strandAt(m.face(), m.u(), m.v()) < 0 && !add.contains(m)) add.add(m);
        }
        for (Strand s : add) if (strands.size() < HairCode.MAX_STRANDS) strands.add(s);
        preview();
    }

    private void applyPreset(int index) {
        HairCode.Preset[] all = HairCode.Preset.values();
        preset = Math.floorMod(index, all.length);
        strands.clear();
        strands.addAll(HairCode.strands(all[preset]));
        selected = -1;
        preview();
    }

    private void paste() {
        List<Strand> s = HairCode.decode(minecraft.keyboardHandler.getClipboard());
        if (s == null) {
            status = Component.translatable("hair.dbzenith.bad_code");
            return;
        }
        strands.clear();
        strands.addAll(s);
        selected = -1;
        status = Component.translatable("hair.dbzenith.pasted", s.size());
        preview();
    }

    private void preview() {
        if (minecraft == null || minecraft.player == null) return;
        PublicStatePacket s = ClientPublicStates.get(minecraft.player.getId());
        if (s != null) ClientPublicStates.put(s.withAppearance(HairCode.encode(strands), color, s.eyeColor(), s.skinTone()));
    }

    private int strandAt(Face f, int u, int v) {
        for (int i = 0; i < strands.size(); i++) {
            Strand s = strands.get(i);
            if (s.face() == f && s.u() == u && s.v() == v) return i;
        }
        return -1;
    }

    /** New strands start pointing the way hair usually grows on that part of the head. */
    private Strand fresh(Face f, int u, int v) {
        if (selected >= 0 && selected < strands.size() && strands.get(selected).face() == f) {
            Strand s = strands.get(selected);
            return new Strand(f, u, v, s.yaw(), s.pitch(), s.length(), s.width(), s.bend());
        }
        return switch (f) {
            case TOP -> new Strand(f, u, v, 0, 0, 6, 3, Bend.STRAIGHT);
            case FRONT -> new Strand(f, u, v, 0, -5, 4, 2, Bend.HANG);
            default -> new Strand(f, u, v, 0, -2, 5, 3, Bend.DROOP);
        };
    }

    // ------------------------------------------------------------------ the unfolded head

    private int netX() {
        return left + 10;
    }

    private int netY() {
        return top + 22;
    }

    /** Screen position of a cell's top left corner. */
    private int[] cell(Face f, int u, int v) {
        int ox = netX(), oy = netY();
        return switch (f) {
            case TOP -> new int[]{ox + FACE + u * CELL, oy + FACE + (7 - v) * CELL};
            case FRONT -> new int[]{ox + FACE + u * CELL, oy + FACE * 2 + v * CELL};
            case BACK -> new int[]{ox + FACE + (7 - u) * CELL, oy + (7 - v) * CELL};
            case RIGHT -> new int[]{ox + (7 - v) * CELL, oy + FACE + u * CELL};
            case LEFT -> new int[]{ox + FACE * 2 + v * CELL, oy + FACE + (7 - u) * CELL};
        };
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (Face f : Face.values()) for (int u = 0; u < 8; u++) for (int v = 0; v < 8; v++) {
            int[] c = cell(f, u, v);
            if (mx < c[0] || mx >= c[0] + CELL || my < c[1] || my >= c[1] + CELL) continue;
            int at = strandAt(f, u, v);
            if (button == 1) {
                if (at >= 0) {
                    strands.remove(at);
                    selected = -1;
                    preview();
                }
            } else if (at >= 0) {
                selected = at;
            } else if (strands.size() < HairCode.MAX_STRANDS) {
                strands.add(fresh(f, u, v));
                selected = strands.size() - 1;
                preview();
            }
            return true;
        }
        // hair colour swatches
        int sx = left + 8, sy = top + H - 64;
        for (int i = 0; i < Palettes.HAIR.length; i++) {
            if (mx >= sx + i * 11 && mx < sx + i * 11 + 9 && my >= sy && my < sy + 9) {
                color = Palettes.HAIR[i];
                preview();
                return true;
            }
        }
        if (mx >= previewX0() && mx < left + W - 6 && my >= top + 20 && my < top + H - 30) dragging = true;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            turn = (float) ((turn - dx * 2) % 360);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    private int previewX0() {
        return left + 296;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        DbzTheme.screenBackground(g, width, height);
        DbzTheme.window(g, font, title, left, top, W, H);

        // the net: each face a grid; planted squares in the hair colour, the picked one framed in gold
        for (Face f : Face.values()) {
            int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE;
            for (int u = 0; u < 8; u++) for (int v = 0; v < 8; v++) {
                int[] c = cell(f, u, v);
                x0 = Math.min(x0, c[0]);
                y0 = Math.min(y0, c[1]);
                int at = strandAt(f, u, v);
                int fill = at >= 0 ? 0xFF000000 | color : ((u + v) % 2 == 0 ? 0xFF4A4058 : 0xFF40384E);
                g.fill(c[0], c[1], c[0] + CELL - 1, c[1] + CELL - 1, fill);
                if (at >= 0) g.renderOutline(c[0] - 1, c[1] - 1, CELL + 1, CELL + 1, at == selected ? 0xFFFFE6A0 : 0xFFB08A50);
            }
            g.renderOutline(x0 - 1, y0 - 1, FACE + 1, FACE + 1, 0xFF6A5A30);
            // labels where the net leaves room: beside the face and back, under the sides, top in the corner
            float lx = switch (f) { case FRONT, BACK -> x0 + FACE + 3; case TOP -> netX(); default -> x0; };
            float ly = switch (f) { case FRONT, BACK -> y0 + 2; case TOP -> netY(); default -> y0 - 7; };
            DbzTheme.text(g, font, FACE_NAMES[f.ordinal()], lx, ly, DbzTheme.DIM, 0.55f);
        }
        // face orientation hint on the front: a little nose mark
        int[] nose = cell(Face.FRONT, 3, 5);
        g.fill(nose[0] + CELL - 2, nose[1] + 1, nose[0] + CELL + 1, nose[1] + CELL - 1, 0x60FFFFFF);

        // the picked strand
        int cx = left + 168;
        Component head = selected >= 0 && selected < strands.size()
                ? Component.translatable("hair.dbzenith.strand", selected + 1, strands.size(), FACE_NAMES[strands.get(selected).face().ordinal()])
                : Component.translatable("hair.dbzenith.none_selected", strands.size(), HairCode.MAX_STRANDS);
        g.drawString(font, head, cx, top + 26, DbzTheme.TITLE, true);
        String[] labels = {"hair.dbzenith.yaw", "hair.dbzenith.pitch", "hair.dbzenith.length", "hair.dbzenith.width"};
        int y = top + 43;
        Strand s = selected >= 0 && selected < strands.size() ? strands.get(selected) : null;
        for (int i = 0; i < labels.length; i++) {
            g.drawString(font, Component.translatable(labels[i]), cx, y, DbzTheme.DIM, false);
            String value = s == null ? "-" : switch (i) {
                case 0 -> s.yaw() * 15 + "°";
                case 1 -> s.pitch() * 15 + "°";
                case 2 -> String.valueOf(s.length());
                default -> String.valueOf(s.width());
            };
            g.drawCenteredString(font, value, cx + 84, y, DbzTheme.TEXT);
            y += 17;
        }
        curveButton.setMessage(Component.translatable("hair.dbzenith.curve",
                s == null ? Component.literal("-") : Component.translatable("hair.dbzenith.bend." + s.bend().name().toLowerCase())));

        // presets and colours
        HairCode.Preset p = HairCode.Preset.values()[preset];
        boolean custom = !HairCode.strands(p).equals(strands);
        g.drawCenteredString(font, custom ? Component.translatable("hair.dbzenith.custom") : Component.translatable(p.translationKey()),
                left + 70, top + H - 43, custom ? DbzTheme.DIM : DbzTheme.TEXT);
        int sx = left + 8, sy = top + H - 64;
        for (int i = 0; i < Palettes.HAIR.length; i++) {
            boolean sel = Palettes.HAIR[i] == color;
            g.fill(sx + i * 11 - 1, sy - 1, sx + i * 11 + 10, sy + 10, sel ? 0xFFFFFFFF : 0xFF404048);
            g.fill(sx + i * 11, sy, sx + i * 11 + 9, sy + 9, 0xFF000000 | Palettes.HAIR[i]);
        }
        DbzTheme.text(g, font, status, left + 142, top + H - 18, DbzTheme.GOOD, 0.7f);

        // live preview, turned by dragging
        int px0 = previewX0(), px1 = left + W - 8, py0 = top + 22, py1 = top + H - 32;
        g.fill(px0, py0, px1, py1, 0x60060A14);
        if (minecraft.player != null) {
            PortraitRenderer.draw(g, minecraft.player, (px0 + px1) / 2, py0 + 74, 70f, turn, new int[]{px0, py0, px1, py1});
        }
        DbzTheme.text(g, font, Component.translatable("hair.dbzenith.drag"), px0 + 2, py1 + 2, DbzTheme.DIM, 0.55f);
        super.render(g, mouseX, mouseY, partial);
    }

    @Override
    public void onClose() {
        if (original != null) ClientPublicStates.put(original);   // cancel: the look before the barber
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
