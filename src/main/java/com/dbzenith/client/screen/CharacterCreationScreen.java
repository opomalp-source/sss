package com.dbzenith.client.screen;

import com.dbzenith.appearance.FaceParts;
import com.dbzenith.appearance.HairCode;
import com.dbzenith.appearance.Palettes;
import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.client.ui.PortraitRenderer;
import com.dbzenith.client.ui.Ui;
import com.dbzenith.client.ui.UiButton;
import com.dbzenith.client.ui.UiSlider;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.CreateCharacterPacket;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.network.PublicStatePacket;
import com.dbzenith.race.CharacterCreation;
import com.dbzenith.race.Races;
import com.dbzenith.race.Variant;
import com.dbzenith.stats.FightingPath;
import com.dbzenith.stats.Race;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * First-join character creation, UI v3 (CX-16a). The left of the screen is a large live preview standing on a pool of
 * light (drag to turn it, scroll to zoom; the Face tab zooms in on the face); the right is a card under five tabs:
 * <ul>
 * <li><b>Race</b>: a grid of the races, the lineage, what the race is like.</li>
 * <li><b>Body</b>: the build (three silhouettes), the height, the skin.</li>
 * <li><b>Face</b>: each part of the face, the eye colour, "surprise me".</li>
 * <li><b>Hair</b>: the styles, the barber, the colour and the tips.</li>
 * <li><b>Path</b>: fighter, spiritualist or hybrid, the alignment, the aura colour.</li>
 * </ul>
 * The local player's public state is overridden while the screen is open so the preview shows the choices; leaving
 * without confirming restores it. New characters start in training shorts and nothing else.
 */
public class CharacterCreationScreen extends Screen {
    private enum Tab { RACE, BODY, FACE, HAIR, PATH }

    private static final int SW = 12, GAP = 4;                       // colour swatches
    private static final int[] COLORS = {0xFFFFFF, 0xFFD040, 0xFF9A2A, 0xFF4040, 0xFF70D0, 0xB070FF, 0x5A6AFF, 0x3CC8FF, 0x40D0A0,
            0x7CFF7C, 0xC0C0C8, 0x202028};

    private Race race = Race.SAIYAN;
    private Variant variant = Variant.SAIYAN;
    private FightingPath path = FightingPath.HYBRID;
    private PlayerData.BodyType body = PlayerData.BodyType.NORMAL;
    private int preset = HairCode.Preset.SPIKY.ordinal();
    private String hairCode = HairCode.Preset.SPIKY.code();
    private int hairColor = Palettes.HAIR[0];
    private int eyeColor = -1;
    private int skinTone = Palettes.SKIN[3];                          // a body of your own from the start: in shorts
    private int stature = 100;
    private int alignment;
    private int face = com.dbzenith.client.ClientPlayerData.get().getFace();
    private int highlight = com.dbzenith.client.ClientPlayerData.get().getHighlightColor();
    private int aura = com.dbzenith.client.ClientPlayerData.get().getAuraColor();
    private Tab tab = Tab.RACE;

    private PublicStatePacket originalState;
    private boolean confirmed;
    private boolean toBarber;

    // layout
    private int pw, rx, rw, cardY, cardH, cx, cw, cy;
    /** The composition: at most 620 x 350, centred on bigger screens so nothing stretches into empty space. */
    private int ox, oy, W, H;
    // preview
    private float yaw = 22, zoom = 1, zoomTarget = 1;
    private boolean dragging;
    private double lastDragX;
    /** Swatch rows on the current tab: y, colours, what they set. */
    private final List<SwatchRow> rows = new ArrayList<>();

    private record SwatchRow(int x, int y, int[] colors, java.util.function.IntSupplier get, java.util.function.IntConsumer set) {}

    public CharacterCreationScreen() {
        super(Component.translatable("screen.dbzenith.create"));
    }

    /** Dev automation: open on a tab by name (race, body, face, hair, path). */
    public CharacterCreationScreen tab(String name) {
        for (Tab t : Tab.values()) if (name.contains("create_" + t.name().toLowerCase())) {
            tab = t;
            zoomTarget = zoom = t == Tab.FACE ? 2.2f : t == Tab.HAIR ? 1.6f : 1f;
        }
        for (Race r : Race.values()) if (name.contains("race" + r.name().toLowerCase().replace("_", "") + "_")) {
            race = r;
            variant = Variant.defaultFor(r);
            if (bornBald(r)) hairCode = HairCode.Preset.BALD.code();
        }
        return this;
    }

    /** Open on the Face tab. */
    public CharacterCreationScreen face() {
        tab = Tab.FACE;
        zoomTarget = zoom = 2.2f;
        return this;
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        if (originalState == null && minecraft.player != null) originalState = ClientPublicStates.get(minecraft.player.getId());
        rows.clear();
        W = Math.min(width, 620);
        H = Math.min(height, 350);
        ox = (width - W) / 2;
        oy = (height - H) / 2;
        pw = Mth.clamp((int) (W * 0.36f), 130, 260);
        rx = ox + pw + 4;
        rw = ox + W - rx - 10;
        cardY = oy + 42;
        cardH = oy + H - cardY - 32;
        cx = rx + 10;
        cw = rw - 20;
        cy = cardY + 9;

        int tx = rx;
        for (Tab t : Tab.values()) {
            Component label = Component.translatable("screen.dbzenith.create_tab_" + t.name().toLowerCase());
            int w = (int) (font.width(label.getString().toUpperCase()) * 0.85f) + 16;
            addRenderableWidget(UiButton.of(UiButton.Style.TAB, label, tx, oy + 22, w, 16, b -> {
                tab = t;
                zoomTarget = t == Tab.FACE ? 2.2f : t == Tab.HAIR ? 1.6f : 1f;
                rebuild();
            }).selected(t == tab));
            tx += w + 2;
        }
        switch (tab) {
            case RACE -> initRace();
            case BODY -> initBody();
            case FACE -> initFace();
            case HAIR -> initHair();
            case PATH -> initPath();
        }
        addRenderableWidget(UiButton.of(UiButton.Style.PRIMARY, Component.translatable("screen.dbzenith.create_confirm"),
                ox + W - 10 - 128, oy + H - 25, 128, 18, b -> confirm()));
        addRenderableWidget(UiButton.of(UiButton.Style.GHOST, Component.translatable("screen.dbzenith.create_later"),
                ox + W - 10 - 128 - 84, oy + H - 25, 80, 18, b -> onClose()));
        updatePreview();
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    /** A tile: a clickable button that draws itself. */
    private UiButton tile(int x, int y, int w, int h, Runnable onClick, TileArt art, boolean selected) {
        UiButton b = new UiButton(x, y, w, h, Component.empty(), UiButton.Style.GHOST, btn -> onClick.run()) {
            @Override
            protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partial) {
                Ui.tile(g, getX(), getY(), getWidth(), getHeight(), selected, isHoveredOrFocused());
                art.draw(g, getX(), getY(), getWidth(), getHeight(), selected, isHoveredOrFocused());
            }
        };
        return addRenderableWidget(b);
    }

    private interface TileArt {
        void draw(GuiGraphics g, int x, int y, int w, int h, boolean selected, boolean hovered);
    }

    private void initRace() {
        Race[] races = Race.values();
        int cols = cw >= 240 ? 3 : 2, gap = 2, tw = (cw - (cols - 1) * gap) / cols, th = 14;
        for (int i = 0; i < races.length; i++) {
            Race r = races[i];
            int x = cx + (i % cols) * (tw + gap), y = cy + 12 + (i / cols) * (th + gap);
            tile(x, y, tw, th, () -> {
                race = r;
                if (!Variant.creationChoices(race).contains(variant)) variant = Variant.defaultFor(race);
                boolean bald = bornBald(r);                                    // hairless races start bald; the others get the spikes back
                if (bald || hairCode.equals(HairCode.Preset.BALD.code())) {
                    preset = (bald ? HairCode.Preset.BALD : HairCode.Preset.SPIKY).ordinal();
                    hairCode = HairCode.Preset.values()[preset].code();
                }
                rebuild();
            }, (g, bx, by, bw, bh, sel, hov) -> {
                Ui.round(g, bx + 5, by + 4, 6, 6, 1, 0xFF000000 | Races.of(r).auraColor());
                Ui.text(g, font, Component.translatable(r.translationKey()), bx + 15, by + 3.5f, sel ? 0xFFFFE2B0 : hov ? Ui.TEXT : 0xFFC8CEDC, 0.85f);
            }, r == race);
        }
        int y = cy + 12 + ((races.length + cols - 1) / cols) * (th + gap) + 6;
        List<Variant> lineages = Variant.creationChoices(race);
        if (!lineages.contains(variant)) variant = Variant.defaultFor(race);
        if (lineages.size() > 1) {
            int x = cx;
            for (Variant v : lineages) {
                Component label = Component.translatable(v.translationKey());
                int w = (int) (font.width(label) * 0.85f) + 14;
                addRenderableWidget(UiButton.of(UiButton.Style.CHIP, label, x, y + 11, w, 14, b -> {
                    variant = v;
                    rebuild();
                }).selected(v == variant).tip(Component.translatable(v.descriptionKey())));
                x += w + 4;
            }
        }
    }

    /** Races with no hair, or hair of their own painted on: they start bald (hair can still be chosen). */
    private static boolean bornBald(Race r) {
        return switch (r) {
            case NAMEKIAN, FROST_DEMON, MAJIN, BIO_ANDROID, VAMPIRE, TUFFLE, CORE_PERSON -> true;
            default -> false;
        };
    }

    private void initBody() {
        PlayerData.BodyType[] bodies = PlayerData.BodyType.values();
        int gap = 6, tw = (cw - 2 * gap) / 3, th = Math.min(66, cardH / 3);
        for (int i = 0; i < bodies.length; i++) {
            PlayerData.BodyType bt = bodies[i];
            int build = i;
            tile(cx + i * (tw + gap), cy + 12, tw, th, () -> {
                body = bt;
                rebuild();
            }, (g, bx, by, bw, bh, sel, hov) -> {
                silhouette(g, bx + bw / 2, by + bh - 15, build, sel ? Ui.GOLD : hov ? 0xFFC8CEDC : 0xFF7A8396);
                Ui.centered(g, font, Component.translatable("screen.dbzenith.body." + bt.name().toLowerCase()), bx + bw / 2f, by + bh - 11,
                        sel ? 0xFFFFE2B0 : Ui.MUTED, 0.85f);
            }, bt == body);
        }
        int y = cy + 12 + th + 8;
        addRenderableWidget(new HeightSlider(cx, y, cw));
        y += 28;
        if (com.dbzenith.client.render.RaceSkinLayer.texture(race, variant) == null) {
            rows.add(new SwatchRow(cx, y + 11, skinPalette(), () -> skinTone, c -> skinTone = c));
        }
    }

    /** The skin tones offered (not "your Minecraft skin": a new character has a body of their own). */
    private static int[] skinPalette() {
        int[] all = Palettes.SKIN;
        int n = 0;
        for (int c : all) if (c >= 0) n++;
        int[] out = new int[n];
        int i = 0;
        for (int c : all) if (c >= 0) out[i++] = c;
        return out;
    }

    private void initFace() {
        FaceParts.Part[] parts = FaceParts.Part.values();
        int colW = (cw - 10) / 2;
        for (int i = 0; i < parts.length; i++) {
            FaceParts.Part p = parts[i];
            int x = cx + (i % 2) * (colW + 10), y = cy + 12 + (i / 2) * 26 + 10;
            addRenderableWidget(UiButton.of(UiButton.Style.CHIP, Component.literal("<"), x, y, 14, 14, b -> changeFace(p, -1)));
            addRenderableWidget(UiButton.of(UiButton.Style.CHIP, Component.literal(">"), x + colW - 14, y, 14, 14, b -> changeFace(p, 1)));
        }
        int y = cy + 12 + ((parts.length + 1) / 2) * 26 + 4;
        rows.add(new SwatchRow(cx, y + 11, Palettes.EYES, () -> eyeColor, c -> eyeColor = c));
        addRenderableWidget(UiButton.of(UiButton.Style.SECONDARY, Component.translatable("screen.dbzenith.face_random"),
                cx + cw - 84, cy, 84, 14, b -> {
                    java.util.Random r = new java.util.Random();
                    for (FaceParts.Part p : parts) {
                        if (p != FaceParts.Part.EXTRA || r.nextInt(3) == 0) face = FaceParts.with(face, p, r.nextInt(p.options));
                    }
                    showFace();
                }).textScale(0.8f));
    }

    private void initHair() {
        HairCode.Preset[] all = HairCode.Preset.values();
        int cols = cw >= 260 ? 5 : 4, gap = 3, w = (cw - (cols - 1) * gap) / cols, h = 13;
        for (int i = 0; i < all.length; i++) {
            int idx = i;
            addRenderableWidget(UiButton.of(UiButton.Style.CHIP, Component.translatable(all[i].translationKey()),
                    cx + (i % cols) * (w + gap), cy + 12 + (i / cols) * (h + gap), w, h, b -> {
                        preset = idx;
                        hairCode = all[idx].code();
                        rebuild();
                    }).selected(hairCode.equals(all[i].code())).textScale(0.75f));
        }
        addRenderableWidget(UiButton.of(UiButton.Style.SECONDARY, Component.translatable("screen.dbzenith.hair_edit"), cx + cw - 70, cy - 2, 70, 13, b -> {
            toBarber = true;
            minecraft.setScreen(new HairEditorScreen(this, hairCode, hairColor, (code, color) -> {
                hairCode = code;
                hairColor = color;
            }));
        }).textScale(0.8f));
        int y = cy + 12 + ((all.length + cols - 1) / cols) * (h + gap) + 4;
        rows.add(new SwatchRow(cx, y + 11, Palettes.HAIR, () -> hairColor, c -> hairColor = c));
        rows.add(new SwatchRow(cx, y + 11 + 30, withNone(COLORS), () -> highlight, c -> highlight = c));
    }

    private static int[] withNone(int[] colors) {
        int[] out = new int[colors.length + 1];
        out[0] = -1;
        System.arraycopy(colors, 0, out, 1, colors.length);
        return out;
    }

    private void initPath() {
        FightingPath[] paths = FightingPath.values();
        int gap = 6, tw = (cw - 2 * gap) / 3, th = 66;
        for (int i = 0; i < paths.length; i++) {
            FightingPath p = paths[i];
            tile(cx + i * (tw + gap), cy + 12, tw, th, () -> {
                path = p;
                rebuild();
            }, (g, bx, by, bw, bh, sel, hov) -> {
                Ui.text(g, font, Component.translatable(p.translationKey()), bx + 6, by + 6, sel ? 0xFFFFE2B0 : Ui.TEXT, 0.95f);
                Ui.paragraph(g, font, Component.translatable(p.translationKey() + ".short"), bx + 6, by + 18, bw - 10, Ui.MUTED, 0.7f, 6);
            }, p == path);
        }
        int y = cy + 12 + th + 8;
        addRenderableWidget(new AlignmentSlider(cx, y, cw));
        y += 28;
        rows.add(new SwatchRow(cx, y + 11, withNone(COLORS), () -> aura, c -> aura = c));
    }

    private void changeFace(FaceParts.Part p, int step) {
        face = FaceParts.with(face, p, FaceParts.get(face, p) + step);
        showFace();
    }

    private void showFace() {
        updatePreview();
    }

    // ------------------------------------------------------------------ the preview and the result

    private void updatePreview() {
        if (minecraft == null || minecraft.player == null) return;
        int flags = Races.of(race).tail() ? PublicStatePacket.TAIL : 0;
        ClientPublicStates.put(new PublicStatePacket(minecraft.player.getId(), flags, 50, Races.of(race).auraColor(),
                PlayerData.BASE_FORM, race.ordinal(), body.ordinal(), 0, hairColor, eyeColor, 0L, PublicStatePacket.RACE_LOOK,
                hairCode, skin(), stature, variant.ordinal(), "", face, highlight, "").withFace(face, highlight, aura >= 0 ? aura : Races.of(race).auraColor()));
        minecraft.player.refreshDimensions();
    }

    /** The skin tone to send: none for a race with its own skin (its parts then take the race's colours). */
    private int skin() {
        return com.dbzenith.client.render.RaceSkinLayer.texture(race, variant) == null ? skinTone : -1;
    }

    private void confirm() {
        confirmed = true;
        ModNetwork.sendToServer(new CreateCharacterPacket(new CharacterCreation.Choices(race, path, body, hairCode, hairColor, eyeColor,
                alignment, skin(), stature, variant.id())));
        ModNetwork.sendToServer(new com.dbzenith.network.FacePacket(face, highlight, aura));
        minecraft.setScreen(null);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int glow = aura >= 0 ? aura : Races.of(race).auraColor();
        Ui.backdrop(g, width, height, ox + pw / 2f, oy + H * 0.55f, glow);
        drawPreview(g, glow);

        Ui.text(g, font, Component.literal(title.getString().toUpperCase()), rx + 2, oy + 9, Ui.GOLD, 0.85f);
        Ui.text(g, font, Component.translatable("screen.dbzenith.create_sub"), rx + 2 + font.width(title.getString().toUpperCase()) * 0.85f + 8, oy + 9.5f,
                Ui.FAINT, 0.7f);
        g.fill(rx, oy + 39, rx + rw, oy + 40, Ui.LINE_SOFT);
        Ui.card(g, rx, cardY, rw, cardH);
        switch (tab) {
            case RACE -> drawRace(g);
            case BODY -> drawBody(g);
            case FACE -> drawFace(g);
            case HAIR -> drawHair(g);
            case PATH -> drawPath(g);
        }
        for (SwatchRow r : rows) Ui.swatches(g, r.x, r.y, SW, GAP, r.colors, r.get.getAsInt(), mouseX, mouseY);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void drawRace(GuiGraphics g) {
        Ui.section(g, font, Component.translatable("screen.dbzenith.create_tab_race"), cx, cy, cw);
        int cols = cw >= 240 ? 3 : 2;
        int y = cy + 12 + ((Race.values().length + cols - 1) / cols) * 16 + 6;
        if (Variant.creationChoices(race).size() > 1) {
            Ui.section(g, font, Component.translatable("screen.dbzenith.lineage"), cx, y, cw);
            y += 30;
        }
        Ui.paragraph(g, font, Component.translatable(Races.of(race).descriptionKey()), cx, y, cw, 0xFFB8C0D0, 0.8f,
                Math.max(1, (cardY + cardH - 2 - y) / 8));
    }

    private void drawBody(GuiGraphics g) {
        Ui.section(g, font, Component.translatable("screen.dbzenith.body"), cx, cy, cw);
        int th = Math.min(66, cardH / 3);
        int y = cy + 12 + th + 8 + 28;
        if (com.dbzenith.client.render.RaceSkinLayer.texture(race, variant) == null) {
            Ui.section(g, font, Component.translatable("screen.dbzenith.skin"), cx, y, cw);
        } else {
            Ui.section(g, font, Component.translatable("screen.dbzenith.skin"), cx, y, cw);
            Ui.paragraph(g, font, Component.translatable("screen.dbzenith.skin_race"), cx, y + 12, cw, Ui.MUTED, 0.75f, 2);
        }
    }

    private void drawFace(GuiGraphics g) {
        Ui.section(g, font, Component.translatable("screen.dbzenith.create_tab_face"), cx, cy, cw - 92);
        FaceParts.Part[] parts = FaceParts.Part.values();
        int colW = (cw - 10) / 2;
        for (int i = 0; i < parts.length; i++) {
            int x = cx + (i % 2) * (colW + 10), y = cy + 12 + (i / 2) * 26;
            Ui.text(g, font, Component.translatable("face.dbzenith.part." + parts[i].key()), x, y, Ui.MUTED, 0.75f);
            Ui.round(g, x + 16, y + 10, colW - 32, 14, 2, 0x10FFFFFF);
            Ui.centered(g, font, Component.translatable(parts[i].optionKey(FaceParts.get(face, parts[i]))), x + colW / 2f, y + 13.5f, Ui.TEXT, 0.8f);
        }
        int y = cy + 12 + ((parts.length + 1) / 2) * 26 + 4;
        Ui.section(g, font, Component.translatable("screen.dbzenith.eyes"), cx, y, cw);
    }

    private void drawHair(GuiGraphics g) {
        Ui.section(g, font, Component.translatable("screen.dbzenith.hair"), cx, cy, cw - 76);
        int cols = cw >= 260 ? 5 : 4;
        int y = cy + 12 + ((HairCode.Preset.values().length + cols - 1) / cols) * 16 + 4;
        Ui.section(g, font, Component.translatable("screen.dbzenith.hair_color"), cx, y, cw);
        Ui.section(g, font, Component.translatable("screen.dbzenith.face_highlight"), cx, y + 30, cw);
    }

    private void drawPath(GuiGraphics g) {
        Ui.section(g, font, Component.translatable("screen.dbzenith.path"), cx, cy, cw);
        int y = cy + 12 + 66 + 8 + 28;
        Ui.section(g, font, Component.translatable("screen.dbzenith.face_aura"), cx, y, cw);
    }

    /** A build's silhouette: head, shoulders, arms and legs, wider and heavier from lean to bulky. */
    private static void silhouette(GuiGraphics g, int cx, int feet, int build, int color) {
        int torso = 9 + build * 3, arm = 3 + build, leg = 4 + build, s = 1;
        int legH = 14, torsoH = 14, head = 8;
        int top = feet - legH - torsoH - head - 1;
        Ui.round(g, cx - head / 2, top, head, head, 1, color);                                   // head
        Ui.round(g, cx - torso / 2, top + head + 1, torso, torsoH, 1, color);                    // torso
        g.fill(cx - torso / 2 - arm - s, top + head + 2, cx - torso / 2 - s, top + head + 2 + 13, color);   // arms
        g.fill(cx + torso / 2 + s, top + head + 2, cx + torso / 2 + arm + s, top + head + 2 + 13, color);
        g.fill(cx - leg - 1, top + head + 1 + torsoH, cx - 1, feet, color);                     // legs
        g.fill(cx + 1, top + head + 1 + torsoH, cx + leg + 1, feet, color);
    }

    /** The character, standing on a pool of light in the left part of the screen. */
    private void drawPreview(GuiGraphics g, int glow) {
        zoom += (zoomTarget - zoom) * 0.18f;
        int floor = oy + H - 46, cxp = ox + pw / 2;
        Ui.platform(g, cxp, floor, Math.min(70, pw / 2 - 8), 9, glow);
        if (minecraft.player != null) {
            float tall = com.dbzenith.appearance.Stature.heightScale(minecraft.player);
            float base = (H - 120) / (2.15f * Math.max(0.8f, tall));
            float s = base * zoom;
            float headFull = floor - 1.62f * base * tall;                   // where the head is at full length
            float headY = Mth.lerp(Mth.clamp((zoom - 1) / 1.2f, 0, 1), headFull, oy + H * 0.42f);
            float feetY = headY + 1.62f * s * tall;
            PortraitRenderer.draw(g, minecraft.player, cxp, (int) (feetY - 1.5f * s * tall), s, yaw, new int[]{ox, oy, ox + pw, oy + H - 22});
        }
        Component name = Component.translatable(race.translationKey());
        Ui.centered(g, font, Component.literal(name.getString().toUpperCase()), cxp, oy + H - 32, Ui.GOLD, 1.15f);
        if (Variant.creationChoices(race).size() > 1) {
            Ui.centered(g, font, Component.translatable(variant.translationKey()), cxp, oy + H - 20, Ui.MUTED, 0.75f);
        }
        Ui.centered(g, font, Component.translatable("screen.dbzenith.preview_hint"), cxp, oy + 8, Ui.FAINT, 0.65f);
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (SwatchRow r : rows) {
            int i = Ui.swatchAt(mouseX, mouseY, r.x, r.y, SW, GAP, r.colors.length);
            if (i >= 0) {
                r.set.accept(r.colors[i]);
                com.dbzenith.client.ClientSounds.uiClick();
                updatePreview();
                return true;
            }
        }
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (mouseX >= ox && mouseX < ox + pw && mouseY < oy + H - 24) {
            dragging = true;
            lastDragX = mouseX;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragging) {
            yaw -= (float) (mouseX - lastDragX) * 1.6f;
            lastDragX = mouseX;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= ox && mouseX < ox + pw) {
            zoomTarget = Mth.clamp(zoomTarget + (float) delta * 0.25f, 1f, 2.8f);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public void removed() {
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

    @Override
    public void renderBackground(GuiGraphics g) {
    }

    private class HeightSlider extends UiSlider {
        HeightSlider(int x, int y, int w) {
            super(x, y, w, Component.translatable("screen.dbzenith.height_label"),
                    (stature - PlayerData.MIN_HEIGHT) / (double) (PlayerData.MAX_HEIGHT - PlayerData.MIN_HEIGHT));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            double m = 1.8 * stature / 100.0;
            int inches = (int) Math.round(m / 0.0254);
            setMessage(Component.literal(String.format("%.2f m  ·  %d ft %d in", m, inches / 12, inches % 12)));
        }

        @Override
        protected void applyValue() {
            stature = PlayerData.MIN_HEIGHT + (int) Math.round(value * (PlayerData.MAX_HEIGHT - PlayerData.MIN_HEIGHT));
            updatePreview();
        }
    }

    private class AlignmentSlider extends UiSlider {
        AlignmentSlider(int x, int y, int w) {
            super(x, y, w, Component.translatable("screen.dbzenith.alignment_label"), (alignment + 100) / 200.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            String key = alignment > 33 ? "good" : alignment < -33 ? "evil" : "neutral";
            setMessage(Component.translatable("screen.dbzenith.alignment." + key, alignment));
            fillColor(alignment > 33 ? 0xFF7CD8FF : alignment < -33 ? 0xFFE0485A : Ui.GOLD);
        }

        @Override
        protected void applyValue() {
            alignment = Mth.clamp((int) Math.round(value * 200 - 100), -100, 100);
        }
    }
}
