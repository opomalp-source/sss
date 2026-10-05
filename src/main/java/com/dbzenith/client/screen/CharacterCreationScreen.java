package com.dbzenith.client.screen;

import com.dbzenith.client.ui.DbzTheme;
import com.dbzenith.client.ui.ThemedButton;
import com.dbzenith.client.ClientPlayerData;
import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.CreateCharacterPacket;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.network.PublicStatePacket;
import com.dbzenith.race.CharacterCreation;
import com.dbzenith.race.Races;
import com.dbzenith.stats.FightingPath;
import com.dbzenith.stats.Race;
import com.dbzenith.transform.Form;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * First-join character creation: race, path, body type, hair, eyes and alignment, with a live preview
 * (the local player's public state is overridden while the screen is open). Esc / "Decide later" closes it;
 * it opens again on the next join until a character is created.
 */
public class CharacterCreationScreen extends Screen {
    private static final int W = 400;
    private static final int H = 244;
    private static final int HEADER = 0xFFFFB330;
    private static final int TEXT = 0xFFF0F0F0;
    private static final int DIM = 0xFFA0A0B0;
    private static final int[] HAIR_COLORS = {0x1C1A1A, 0x5A3820, 0xE8C860, 0xB03020, 0xEEEEEE, 0x3050C0, 0x3C8C3C, 0xE070A0, 0x7040A0};
    private static final int[] EYE_COLORS = {-1, 0x101010, 0x5A3820, 0x3070E0, 0x30A040, 0xD02020, 0xE0B020, 0x9040E0};
    private static final int SWATCH = 11;

    private Race race = Race.SAIYAN;
    private FightingPath path = FightingPath.HYBRID;
    private PlayerData.BodyType body = PlayerData.BodyType.NORMAL;
    private int hairStyle = Form.HairStyle.SPIKY.ordinal();
    private int hairColor = HAIR_COLORS[0];
    private int eyeColor = -1;
    private int alignment;

    private PublicStatePacket originalState;
    private boolean confirmed;
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
        int y = top + 114;
        FightingPath[] paths = FightingPath.values();
        for (int i = 0; i < paths.length; i++) {
            FightingPath p = paths[i];
            addRenderableWidget(ThemedButton.of(Component.translatable(p.translationKey()), b -> {
                path = p;
                rebuild();
            }).bounds(mx + 44 + i * 58, y, 56, 16).build().selected(p == path));
        }
        y += 20;
        PlayerData.BodyType[] bodies = PlayerData.BodyType.values();
        for (int i = 0; i < bodies.length; i++) {
            PlayerData.BodyType bt = bodies[i];
            addRenderableWidget(ThemedButton.of(Component.translatable("screen.dbzenith.body." + bt.name().toLowerCase()), b -> {
                body = bt;
                rebuild();
            }).bounds(mx + 44 + i * 58, y, 56, 16).build().selected(bt == body));
        }
        y += 20;
        addRenderableWidget(ThemedButton.of(Component.literal("<"), b -> cycleHair(-1)).bounds(mx + 44, y, 16, 16).build());
        addRenderableWidget(ThemedButton.of(Component.literal(">"), b -> cycleHair(1)).bounds(mx + 146, y, 16, 16).build());
        y += 54;
        addRenderableWidget(new AlignmentSlider(mx + 44, y, 174, 16));

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

    private void cycleHair(int dir) {
        hairStyle = Math.floorMod(hairStyle + dir, Form.HairStyle.values().length);
        updatePreview();
    }

    /** Shows the choices on the local player model by overriding its public state while the screen is open. */
    private void updatePreview() {
        if (minecraft == null || minecraft.player == null) return;
        int flags = Races.of(race).tail() ? PublicStatePacket.TAIL : 0;
        ClientPublicStates.put(new PublicStatePacket(minecraft.player.getId(), flags, 50, Races.of(race).auraColor(),
                PlayerData.BASE_FORM, 0, race.ordinal(), body.ordinal(), hairStyle, hairColor, eyeColor, 0L, PublicStatePacket.RACE_LOOK));
    }

    private void confirm() {
        confirmed = true;
        ModNetwork.sendToServer(new CreateCharacterPacket(new CharacterCreation.Choices(race, path, body, hairStyle, hairColor, eyeColor, alignment)));
        minecraft.setScreen(null);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = left + 112 + 44;
        int hy = top + 174;
        for (int i = 0; i < HAIR_COLORS.length; i++) {
            if (inside(mouseX, mouseY, mx + i * (SWATCH + 2), hy)) {
                hairColor = HAIR_COLORS[i];
                updatePreview();
                return true;
            }
        }
        int ey = hy + 16;
        for (int i = 0; i < EYE_COLORS.length; i++) {
            if (inside(mouseX, mouseY, mx + i * (SWATCH + 2), ey)) {
                eyeColor = EYE_COLORS[i];
                updatePreview();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static boolean inside(double mx, double my, int x, int y) {
        return mx >= x && mx < x + SWATCH && my >= y && my < y + SWATCH;
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
        for (int i = 0; i < Math.min(lines.size(), 7); i++) g.drawString(font, lines.get(i), mx, top + 42 + i * 10, TEXT);

        int y = top + 118;
        g.drawString(font, Component.translatable("screen.dbzenith.path"), mx, y, DIM);
        g.drawString(font, Component.translatable("screen.dbzenith.body"), mx, y + 20, DIM);
        g.drawString(font, Component.translatable("screen.dbzenith.hair"), mx, y + 40, DIM);
        g.drawString(font, Component.translatable("screen.dbzenith.hair_style." + Form.HairStyle.values()[hairStyle].name().toLowerCase()),
                mx + 66, y + 40, TEXT);
        g.drawString(font, Component.translatable("screen.dbzenith.hair_color"), mx, y + 58, DIM);
        g.drawString(font, Component.translatable("screen.dbzenith.eyes"), mx, y + 74, DIM);
        g.drawString(font, Component.translatable("screen.dbzenith.alignment"), mx, y + 94, DIM);

        int sx = mx + 44;
        for (int i = 0; i < HAIR_COLORS.length; i++) swatch(g, sx + i * (SWATCH + 2), top + 174, HAIR_COLORS[i], HAIR_COLORS[i] == hairColor);
        for (int i = 0; i < EYE_COLORS.length; i++) swatch(g, sx + i * (SWATCH + 2), top + 190, EYE_COLORS[i], EYE_COLORS[i] == eyeColor);

        // live preview
        int px = left + W - 50;
        int py = top + 170;
        g.fill(left + W - 100, top + 28, left + W - 6, top + H - 30, 0x50060A14);
        if (minecraft.player != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, px, py, 45, px - mouseX, py - 70 - mouseY, minecraft.player);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private static void swatch(GuiGraphics g, int x, int y, int color, boolean selected) {
        g.fill(x - 1, y - 1, x + SWATCH + 1, y + SWATCH + 1, selected ? 0xFFFFFFFF : 0xFF404048);
        if (color < 0) {
            g.fill(x, y, x + SWATCH, y + SWATCH, 0xFF707078);
            g.fill(x + 2, y + 5, x + SWATCH - 2, y + 6, 0xFFE0E0E0); // "default" dash
        } else {
            g.fill(x, y, x + SWATCH, y + SWATCH, 0xFF000000 | color);
        }
    }

    @Override
    public void removed() {
        // Leaving without confirming restores the real look; after confirming, the server sync takes over.
        if (!confirmed && minecraft != null && minecraft.player != null && originalState != null) ClientPublicStates.put(originalState);
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private class AlignmentSlider extends com.dbzenith.client.ui.ThemedSlider {
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
