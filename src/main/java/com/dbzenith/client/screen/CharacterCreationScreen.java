package com.dbzenith.client.screen;

import com.dbzenith.appearance.HairCode;
import com.dbzenith.appearance.Palettes;
import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.client.ui.DbzTheme;
import com.dbzenith.client.ui.ThemedButton;
import com.dbzenith.client.ui.ThemedSlider;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.CreateCharacterPacket;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.network.PublicStatePacket;
import com.dbzenith.race.CharacterCreation;
import com.dbzenith.race.Races;
import com.dbzenith.stats.FightingPath;
import com.dbzenith.stats.Race;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * First-join character creation: race, path, build, hair (presets or the barber), hair and eye colour, skin tone,
 * height and alignment, with a live preview (the local player's public state is overridden while the screen is
 * open). Esc / "Decide later" closes it; it opens again on the next join until a character is created.
 */
public class CharacterCreationScreen extends Screen {
    private static final int W = 400;
    private static final int H = 244;
    private static final int HEADER = 0xFFFFB330;
    private static final int TEXT = 0xFFF0F0F0;
    private static final int DIM = 0xFFA0A0B0;
    private static final int SWATCH = 9;
    private static final int ROW_COLOR = 154, ROW_EYES = 167, ROW_SKIN = 180;

    private Race race = Race.SAIYAN;
    private com.dbzenith.race.Variant variant = com.dbzenith.race.Variant.SAIYAN;
    private FightingPath path = FightingPath.HYBRID;
    private PlayerData.BodyType body = PlayerData.BodyType.NORMAL;
    private int preset = HairCode.Preset.SPIKY.ordinal();
    private String hairCode = HairCode.Preset.SPIKY.code();
    private int hairColor = Palettes.HAIR[0];
    private int eyeColor = -1;
    private int skinTone = -1;
    private int stature = 100;            // height percent
    private int alignment;
    private boolean faceTab;              // the Face page instead of the Body page
    private int face = com.dbzenith.client.ClientPlayerData.get().getFace();
    private int highlight = com.dbzenith.client.ClientPlayerData.get().getHighlightColor();
    private int aura = com.dbzenith.client.ClientPlayerData.get().getAuraColor();
    private static final int[] COLORS = {0xFFFFFF, 0xFFD040, 0xFF9A2A, 0xFF4040, 0xFF70D0, 0xB070FF, 0x5A6AFF, 0x3CC8FF, 0x40D0A0,
            0x7CFF7C, 0xC0C0C8, 0x202028};
    private static final int FACE_ROW = 86, ROW_TIPS = 182, ROW_AURA = 196;

    private PublicStatePacket originalState;
    private boolean confirmed;
    private boolean toBarber;          // leaving for the hair editor keeps the preview
    private int left;
    private int top;

    public CharacterCreationScreen() {
        super(Component.translatable("screen.dbzenith.create"));
    }

    /** Open on the Face page. */
    public CharacterCreationScreen face() {
        faceTab = true;
        return this;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        if (originalState == null && minecraft.player != null) originalState = ClientPublicStates.get(minecraft.player.getId());

        Race[] races = Race.values();
        for (int i = 0; i < races.length; i++) {
            Race r = races[i];
            addRenderableWidget(ThemedButton.of(Component.translatable(r.translationKey()), b -> {
                race = r;
                rebuild();
            }).bounds(left + 8, top + 28 + i * 15, 96, 14).build().selected(r == race));
        }

        addRenderableWidget(ThemedButton.of(Component.translatable("screen.dbzenith.create_tab_body"), b -> {
            faceTab = false;
            rebuild();
        }).bounds(left + W - 97, top + 31, 44, 12).build().selected(!faceTab));
        addRenderableWidget(ThemedButton.of(Component.translatable("screen.dbzenith.create_tab_face"), b -> {
            faceTab = true;
            rebuild();
        }).bounds(left + W - 51, top + 31, 44, 12).build().selected(faceTab));
        if (faceTab) {
            initFace();
            addRenderableWidget(ThemedButton.of(Component.translatable("screen.dbzenith.create_confirm"), b -> confirm())
                    .bounds(left + W - 150, top + H - 24, 142, 18).build());
            addRenderableWidget(ThemedButton.of(Component.translatable("screen.dbzenith.create_later"), b -> onClose())
                    .bounds(left + W - 246, top + H - 24, 92, 18).build());
            updatePreview();
            return;
        }

        int vx = left + 112;
        java.util.List<com.dbzenith.race.Variant> lineages = com.dbzenith.race.Variant.creationChoices(race);
        if (!lineages.contains(variant)) variant = com.dbzenith.race.Variant.defaultFor(race);
        if (lineages.size() > 1) {
            for (int i = 0; i < lineages.size(); i++) {
                com.dbzenith.race.Variant v = lineages.get(i);
                addRenderableWidget(ThemedButton.of(Component.translatable(v.translationKey()), b -> {
                    variant = v;
                    rebuild();
                }).bounds(vx + 40 + i * 58, top + 82, 56, 15).tooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.translatable(v.descriptionKey()))).build().selected(v == variant));
            }
        }

        int mx = left + 112;
        int y = top + 100;
        FightingPath[] paths = FightingPath.values();
        for (int i = 0; i < paths.length; i++) {
            FightingPath p = paths[i];
            addRenderableWidget(ThemedButton.of(Component.translatable(p.translationKey()), b -> {
                path = p;
                rebuild();
            }).bounds(mx + 40 + i * 58, y, 56, 15).build().selected(p == path));
        }
        y += 18;
        PlayerData.BodyType[] bodies = PlayerData.BodyType.values();
        for (int i = 0; i < bodies.length; i++) {
            PlayerData.BodyType bt = bodies[i];
            addRenderableWidget(ThemedButton.of(Component.translatable("screen.dbzenith.body." + bt.name().toLowerCase()), b -> {
                body = bt;
                rebuild();
            }).bounds(mx + 40 + i * 58, y, 56, 15).build().selected(bt == body));
        }
        y += 18;
        addRenderableWidget(ThemedButton.of(Component.literal("<"), b -> cyclePreset(-1)).bounds(mx + 40, y, 15, 15).build());
        addRenderableWidget(ThemedButton.of(Component.literal(">"), b -> cyclePreset(1)).bounds(mx + 124, y, 15, 15).build());
        addRenderableWidget(ThemedButton.of(Component.translatable("screen.dbzenith.hair_edit"), b -> {
            toBarber = true;
            minecraft.setScreen(new HairEditorScreen(this, hairCode, hairColor, (code, color) -> {
                hairCode = code;
                hairColor = color;
            }));
        }).bounds(mx + 142, y, 72, 15).build());

        int sy = top + 197;
        addRenderableWidget(new HeightSlider(mx + 40, sy, 84, 15));
        addRenderableWidget(new AlignmentSlider(mx + 128, sy, 86, 15));

        addRenderableWidget(ThemedButton.of(Component.translatable("screen.dbzenith.create_confirm"), b -> confirm())
                .bounds(left + W - 150, top + H - 24, 142, 18).build());
        addRenderableWidget(ThemedButton.of(Component.translatable("screen.dbzenith.create_later"), b -> onClose())
                .bounds(left + W - 246, top + H - 24, 92, 18).build());
        updatePreview();
    }

    /** The Face page: a row of arrows for each part, under the race description. */
    private void initFace() {
        int mx = left + 112;
        com.dbzenith.appearance.FaceParts.Part[] parts = com.dbzenith.appearance.FaceParts.Part.values();
        for (int i = 0; i < parts.length; i++) {
            com.dbzenith.appearance.FaceParts.Part p = parts[i];
            int y = top + FACE_ROW + i * 15;
            addRenderableWidget(ThemedButton.of(Component.literal("<"), b -> changeFace(p, -1)).bounds(mx + 40, y, 14, 13).build());
            addRenderableWidget(ThemedButton.of(Component.literal(">"), b -> changeFace(p, 1)).bounds(mx + 160, y, 14, 13).build());
        }
        addRenderableWidget(ThemedButton.of(Component.translatable("screen.dbzenith.face_random"), b -> {
            java.util.Random r = new java.util.Random();
            for (com.dbzenith.appearance.FaceParts.Part p : parts) {
                if (p != com.dbzenith.appearance.FaceParts.Part.EXTRA || r.nextInt(3) == 0) face = com.dbzenith.appearance.FaceParts.with(face, p, r.nextInt(p.options));
            }
            showFace();
        }).bounds(mx + 40, top + 208, 90, 11).build());
    }

    private void changeFace(com.dbzenith.appearance.FaceParts.Part p, int step) {
        face = com.dbzenith.appearance.FaceParts.with(face, p, com.dbzenith.appearance.FaceParts.get(face, p) + step);
        showFace();
    }

    /** Faces are drawn on generated bodies and race skins: a race without a skin of its own gets a skin tone to show it. */
    private void showFace() {
        if (skinTone < 0 && com.dbzenith.client.render.RaceSkinLayer.texture(race, variant) == null) skinTone = Palettes.SKIN[3];
        updatePreview();
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private void cyclePreset(int dir) {
        HairCode.Preset[] all = HairCode.Preset.values();
        preset = Math.floorMod(preset + dir, all.length);
        hairCode = all[preset].code();
        updatePreview();
    }

    /** Shows the choices on the local player model by overriding its public state while the screen is open. */
    private void updatePreview() {
        if (minecraft == null || minecraft.player == null) return;
        int flags = Races.of(race).tail() ? PublicStatePacket.TAIL : 0;
        ClientPublicStates.put(new PublicStatePacket(minecraft.player.getId(), flags, 50, Races.of(race).auraColor(),
                PlayerData.BASE_FORM, race.ordinal(), body.ordinal(), 0, hairColor, eyeColor, 0L, PublicStatePacket.RACE_LOOK,
                hairCode, skinTone, stature, variant.ordinal(), "", face, highlight, "").withFace(face, highlight, aura >= 0 ? aura : Races.of(race).auraColor()));
        minecraft.player.refreshDimensions();
    }

    private void confirm() {
        confirmed = true;
        ModNetwork.sendToServer(new CreateCharacterPacket(new CharacterCreation.Choices(race, path, body, hairCode, hairColor, eyeColor,
                alignment, skinTone, stature, variant.id())));
        ModNetwork.sendToServer(new com.dbzenith.network.FacePacket(face, highlight, aura));
        minecraft.setScreen(null);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (faceTab) {
            int fx = left + 112 + 40;
            for (int row = 0; row < 2; row++) {
                int sy = top + (row == 0 ? ROW_TIPS : ROW_AURA);
                if (mouseY < sy || mouseY >= sy + SWATCH) continue;
                for (int i = 0; i <= COLORS.length; i++) {
                    int sx = fx + i * (SWATCH + 2);
                    if (mouseX >= sx && mouseX < sx + SWATCH) {
                        int c = i == 0 ? -1 : COLORS[i - 1];
                        if (row == 0) highlight = c;
                        else aura = c;
                        updatePreview();
                        return true;
                    }
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int sx = left + 112 + 40;
        int pick = swatchAt(mouseX, mouseY, sx, top + ROW_COLOR, Palettes.HAIR.length);
        if (pick >= 0) {
            hairColor = Palettes.HAIR[pick];
            updatePreview();
            return true;
        }
        pick = swatchAt(mouseX, mouseY, sx, top + ROW_EYES, Palettes.EYES.length);
        if (pick >= 0) {
            eyeColor = Palettes.EYES[pick];
            updatePreview();
            return true;
        }
        pick = swatchAt(mouseX, mouseY, sx, top + ROW_SKIN, Palettes.SKIN.length);
        if (pick >= 0) {
            skinTone = Palettes.SKIN[pick];
            updatePreview();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static int swatchAt(double mx, double my, int x, int y, int count) {
        for (int i = 0; i < count; i++) {
            int sx = x + i * (SWATCH + 2);
            if (mx >= sx && mx < sx + SWATCH && my >= y && my < y + SWATCH) return i;
        }
        return -1;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        DbzTheme.screenBackground(g, width, height);
        DbzTheme.panel(g, left, top, W, H);
        DbzTheme.header(g, font, title, left + W / 2, top - 6);
        g.drawString(font, Component.translatable("screen.dbzenith.create_sub"), left + 8, top + 18, DIM);

        int mx = left + 112;
        g.drawString(font, Component.translatable(race.translationKey()), mx, top + 30, HEADER);
        List<FormattedCharSequence> lines = font.split(Component.translatable(Races.of(race).descriptionKey()), 170);
        for (int i = 0; i < Math.min(lines.size(), 4); i++) g.drawString(font, lines.get(i), mx, top + 41 + i * 10, TEXT);
        if (faceTab) {
            com.dbzenith.appearance.FaceParts.Part[] parts = com.dbzenith.appearance.FaceParts.Part.values();
            for (int i = 0; i < parts.length; i++) {
                int y = top + FACE_ROW + i * 15;
                label(g, "face.dbzenith.part." + parts[i].key(), mx, y + 3);
                Component v = Component.translatable(parts[i].optionKey(com.dbzenith.appearance.FaceParts.get(face, parts[i])));
                g.drawCenteredString(font, v, mx + 107, y + 3, TEXT);
            }
            label(g, "screen.dbzenith.face_highlight", mx, top + ROW_TIPS + 1);
            label(g, "screen.dbzenith.face_aura", mx, top + ROW_AURA + 1);
            int fx = mx + 40;
            swatch(g, fx, top + ROW_TIPS, -1, highlight < 0);
            swatch(g, fx, top + ROW_AURA, -1, aura < 0);
            for (int i = 0; i < COLORS.length; i++) {
                swatch(g, fx + (i + 1) * (SWATCH + 2), top + ROW_TIPS, COLORS[i], COLORS[i] == highlight);
                swatch(g, fx + (i + 1) * (SWATCH + 2), top + ROW_AURA, COLORS[i], COLORS[i] == aura);
            }
            // the preview zooms in on the face
            int bx = left + W - 100, by = top + 28, bw = 94, bh = H - 58;
            g.fill(bx, by, bx + bw, by + bh, 0x50060A14);
            if (minecraft.player != null) {
                g.enableScissor(bx, by, bx + bw, by + bh);
                int cx = bx + bw / 2;
                InventoryScreen.renderEntityInInventoryFollowsMouse(g, cx, by + 250, 125, (cx - mouseX) * 0.15f, (by + 40 - mouseY) * 0.15f, minecraft.player);
                g.disableScissor();
            }
            super.render(g, mouseX, mouseY, partialTick);
            return;
        }
        if (com.dbzenith.race.Variant.creationChoices(race).size() > 1) label(g, "screen.dbzenith.lineage", mx, top + 86);

        label(g, "screen.dbzenith.path", mx, top + 104);
        label(g, "screen.dbzenith.body", mx, top + 122);
        label(g, "screen.dbzenith.hair", mx, top + 140);
        g.drawCenteredString(font, Component.translatable(HairCode.Preset.values()[preset].translationKey()), mx + 90, top + 140, TEXT);
        label(g, "screen.dbzenith.hair_color", mx, top + ROW_COLOR + 1);
        label(g, "screen.dbzenith.eyes", mx, top + ROW_EYES + 1);
        label(g, "screen.dbzenith.skin", mx, top + ROW_SKIN + 1);

        int sx = mx + 40;
        for (int i = 0; i < Palettes.HAIR.length; i++) swatch(g, sx + i * (SWATCH + 2), top + ROW_COLOR, Palettes.HAIR[i], Palettes.HAIR[i] == hairColor);
        for (int i = 0; i < Palettes.EYES.length; i++) swatch(g, sx + i * (SWATCH + 2), top + ROW_EYES, Palettes.EYES[i], Palettes.EYES[i] == eyeColor);
        for (int i = 0; i < Palettes.SKIN.length; i++) swatch(g, sx + i * (SWATCH + 2), top + ROW_SKIN, Palettes.SKIN[i], Palettes.SKIN[i] == skinTone);

        // live preview
        int px = left + W - 50;
        int py = top + 178;
        g.fill(left + W - 100, top + 28, left + W - 6, top + H - 30, 0x50060A14);
        if (minecraft.player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, px, py, 42, px - mouseX, py - 70 - mouseY, minecraft.player);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void label(GuiGraphics g, String key, int x, int y) {
        DbzTheme.text(g, font, Component.translatable(key), x, y, DIM, 0.85f);
    }

    /** A colour square; -1 draws the "keep your own" square (a dash). */
    private static void swatch(GuiGraphics g, int x, int y, int color, boolean selected) {
        g.fill(x - 1, y - 1, x + SWATCH + 1, y + SWATCH + 1, selected ? 0xFFFFFFFF : 0xFF404048);
        if (color < 0) {
            g.fill(x, y, x + SWATCH, y + SWATCH, 0xFF707078);
            g.fill(x + 2, y + 4, x + SWATCH - 2, y + 5, 0xFFE0E0E0);
        } else {
            g.fill(x, y, x + SWATCH, y + SWATCH, 0xFF000000 | color);
        }
    }

    @Override
    public void removed() {
        // Leaving without confirming restores the real look; after confirming, the server sync takes over.
        if (!confirmed && minecraft != null && minecraft.player != null && originalState != null && !toBarber) {
            ClientPublicStates.put(originalState);
            minecraft.player.refreshDimensions();
        }
        toBarber = false;
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private class HeightSlider extends ThemedSlider {
        HeightSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), (stature - PlayerData.MIN_HEIGHT) / (double) (PlayerData.MAX_HEIGHT - PlayerData.MIN_HEIGHT));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("screen.dbzenith.height", String.format("%.2f", 1.8 * stature / 100.0)));
        }

        @Override
        protected void applyValue() {
            stature = PlayerData.MIN_HEIGHT + (int) Math.round(value * (PlayerData.MAX_HEIGHT - PlayerData.MIN_HEIGHT));
            updatePreview();
        }
    }

    private class AlignmentSlider extends ThemedSlider {
        AlignmentSlider(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty(), (alignment + 100) / 200.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            String key = alignment > 33 ? "good" : alignment < -33 ? "evil" : "neutral";
            setMessage(Component.translatable("screen.dbzenith.alignment." + key, alignment));
        }

        @Override
        protected void applyValue() {
            alignment = Mth.clamp((int) Math.round(value * 200 - 100), -100, 100);
        }
    }
}
