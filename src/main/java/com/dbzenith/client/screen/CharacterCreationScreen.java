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
    private FightingPath path = FightingPath.HYBRID;
    private PlayerData.BodyType body = PlayerData.BodyType.NORMAL;
    private int preset = HairCode.Preset.SPIKY.ordinal();
    private String hairCode = HairCode.Preset.SPIKY.code();
    private int hairColor = Palettes.HAIR[0];
    private int eyeColor = -1;
    private int skinTone = -1;
    private int stature = 100;            // height percent
    private int alignment;

    private PublicStatePacket originalState;
    private boolean confirmed;
    private boolean toBarber;          // leaving for the hair editor keeps the preview
    private int left;
    private int top;

    public CharacterCreationScreen() {
        super(Component.translatable("screen.dbzenith.create"));
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
            }).bounds(left + 8, top + 30 + i * 21, 96, 18).build().selected(r == race));
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
                PlayerData.BASE_FORM, 0, race.ordinal(), body.ordinal(), 0, hairColor, eyeColor, 0L, PublicStatePacket.RACE_LOOK,
                hairCode, skinTone, stature));
        minecraft.player.refreshDimensions();
    }

    private void confirm() {
        confirmed = true;
        ModNetwork.sendToServer(new CreateCharacterPacket(new CharacterCreation.Choices(race, path, body, hairCode, hairColor, eyeColor,
                alignment, skinTone, stature)));
        minecraft.setScreen(null);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
        for (int i = 0; i < Math.min(lines.size(), 5); i++) g.drawString(font, lines.get(i), mx, top + 41 + i * 10, TEXT);

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
