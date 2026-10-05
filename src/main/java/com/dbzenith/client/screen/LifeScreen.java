package com.dbzenith.client.screen;

import com.dbzenith.client.ui.DbzTheme;
import com.dbzenith.client.ui.ThemedButton;
import com.dbzenith.client.ClientPlayerData;
import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.network.PublicStatePacket;
import com.dbzenith.world.Cosmetics;
import com.dbzenith.world.LifeSim;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

/** Life-sim overview (age, wisdom, thirst, temperature, partner) and the scar / tattoo picker with a live preview. */
public class LifeScreen extends Screen {
    private static final int W = 300;
    private static final int H = 180;
    private static final int HEADER = 0xFFFFB330;
    private static final int TEXT = 0xFFF0F0F0;
    private static final int DIM = 0xFFA0A0B0;

    private final Screen parent;
    private int left;
    private int top;
    private int scar;
    private int tattoo;
    private boolean raceLook;
    private int skinTone;
    private static final int SKIN_ROW = 92, SWATCH = 9;

    public LifeScreen(Screen parent) {
        super(Component.translatable("screen.dbzenith.life"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        PlayerData d = ClientPlayerData.get();
        scar = Math.min(d.getScar(), Cosmetics.SCARS.size() - 1);
        tattoo = Math.min(d.getTattoo(), Cosmetics.TATTOOS.size() - 1);
        raceLook = d.isRaceLook();
        skinTone = d.getSkinTone();
        addRenderableWidget(ThemedButton.of(Component.translatable("screen.dbzenith.barber"), b ->
                minecraft.setScreen(new HairEditorScreen(this, d.getHairCode(), d.getHairColor(), (code, color) ->
                        ModNetwork.sendToServer(new com.dbzenith.network.AppearancePacket(code, color, d.getEyeColor(), skinTone)))))
                .bounds(left + 144, top + H - 48, 84, 18).build());
        if (com.dbzenith.client.render.RaceSkinLayer.texture(d.getRace(), d.getVariant()) != null) {
            addRenderableWidget(ThemedButton.of(raceLookLabel(), b -> {
                raceLook = !raceLook;
                b.setMessage(raceLookLabel());
                send();
            }).bounds(left + 8, top + H - 70, 130, 18).build());
        }
        addRenderableWidget(ThemedButton.of(scarLabel(), b -> {
            scar = (scar + 1) % Cosmetics.SCARS.size();
            b.setMessage(scarLabel());
            send();
        }).bounds(left + 8, top + H - 48, 130, 18).build());
        addRenderableWidget(ThemedButton.of(tattooLabel(), b -> {
            tattoo = (tattoo + 1) % Cosmetics.TATTOOS.size();
            b.setMessage(tattooLabel());
            send();
        }).bounds(left + 8, top + H - 26, 130, 18).build());
        addRenderableWidget(ThemedButton.of(Component.translatable("gui.back"), b -> onClose())
                .bounds(left + W - 70, top + H - 24, 62, 18).build());
    }

    private Component scarLabel() {
        return Component.translatable("screen.dbzenith.scar", Component.translatable("cosmetic.dbzenith.scar." + Cosmetics.SCARS.get(scar)));
    }

    private Component raceLookLabel() {
        return Component.translatable(raceLook ? "screen.dbzenith.race_look_on" : "screen.dbzenith.race_look_off");
    }

    private Component tattooLabel() {
        return Component.translatable("screen.dbzenith.tattoo", Component.translatable("cosmetic.dbzenith.tattoo." + Cosmetics.TATTOOS.get(tattoo)));
    }

    private void send() {
        ModNetwork.sendToServer(new Cosmetics.Packet(scar, tattoo, raceLook));
        if (minecraft == null || minecraft.player == null) return;
        PublicStatePacket s = ClientPublicStates.get(minecraft.player.getId());
        if (s != null) { // preview at once; the server's public state confirms it
            ClientPublicStates.put(new PublicStatePacket(s.entityId(), s.flags(), s.release(), s.auraColor(), s.form(), s.overdrive(),
                    s.race(), s.bodyType(), s.hairStyle(), s.hairColor(), s.eyeColor(), s.battlePower(),
                    scar | tattoo << 4 | (raceLook ? PublicStatePacket.RACE_LOOK : 0), s.hairCode(), s.skinTone(), s.height(), s.variant(), s.transformTarget()));
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = 0; i < com.dbzenith.appearance.Palettes.SKIN.length; i++) {
            int x = left + 40 + i * (SWATCH + 2), y = top + SKIN_ROW;
            if (mx >= x && mx < x + SWATCH && my >= y && my < y + SWATCH) {
                skinTone = com.dbzenith.appearance.Palettes.SKIN[i];
                PlayerData d = ClientPlayerData.get();
                ModNetwork.sendToServer(new com.dbzenith.network.AppearancePacket(d.getHairCode(), d.getHairColor(), d.getEyeColor(), skinTone));
                PublicStatePacket s = ClientPublicStates.get(minecraft.player.getId());
                if (s != null) ClientPublicStates.put(s.withAppearance(s.hairCode(), s.hairColor(), s.eyeColor(), skinTone));
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        DbzTheme.screenBackground(g, width, height);
        DbzTheme.panel(g, left, top, W, H);
        PlayerData d = ClientPlayerData.get();
        DbzTheme.header(g, font, title, left + W / 2, top - 6);
        int y = top + 24;
        g.drawString(font, Component.translatable("screen.dbzenith.life_age", (int) d.getPhysicalAge(), (int) d.getMentalAge()), left + 8, y, TEXT);
        y += 12;
        g.drawString(font, Component.translatable("screen.dbzenith.life_wisdom", Math.round((LifeSim.wisdomMultiplier(d) - 1) * 100)), left + 8, y, DIM);
        y += 12;
        g.drawString(font, Component.translatable("screen.dbzenith.life_thirst", (int) d.getThirst()), left + 8, y, 0xFF60B0FF);
        y += 12;
        String temp = d.getTemperature() > 0 ? "hot" : d.getTemperature() < 0 ? "cold" : "comfortable";
        g.drawString(font, Component.translatable("screen.dbzenith.life_temperature", Component.translatable("screen.dbzenith.temperature." + temp)),
                left + 8, y, d.getTemperature() == 0 ? DIM : 0xFFFFA040);
        y += 12;
        Component partner = d.getPartnerId().isEmpty() ? Component.translatable("screen.dbzenith.life_single")
                : Component.translatable(d.isNearPartner() ? "screen.dbzenith.life_partner_near" : "screen.dbzenith.life_partner", d.getPartnerName());
        g.drawString(font, partner, left + 8, y, 0xFFFF90D0);
        DbzTheme.text(g, font, Component.translatable("screen.dbzenith.skin"), left + 8, top + SKIN_ROW + 1, DIM, 0.85f);
        for (int i = 0; i < com.dbzenith.appearance.Palettes.SKIN.length; i++) {
            int x = left + 40 + i * (SWATCH + 2), c = com.dbzenith.appearance.Palettes.SKIN[i];
            g.fill(x - 1, top + SKIN_ROW - 1, x + SWATCH + 1, top + SKIN_ROW + SWATCH + 1, c == skinTone ? 0xFFFFFFFF : 0xFF404048);
            g.fill(x, top + SKIN_ROW, x + SWATCH, top + SKIN_ROW + SWATCH, c < 0 ? 0xFF707078 : 0xFF000000 | c);
            if (c < 0) g.fill(x + 2, top + SKIN_ROW + 4, x + SWATCH - 2, top + SKIN_ROW + 5, 0xFFE0E0E0);
        }
        super.render(g, mouseX, mouseY, partialTick);
        if (minecraft != null && minecraft.player != null) {
            int px = left + W - 50, py = top + H - 34;
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, px, py, 50, px - mouseX, py - 80 - mouseY, minecraft.player);
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
