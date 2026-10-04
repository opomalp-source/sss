package com.dbzenith.client.screen;

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
    private static final int PANEL = 0xE0101018;
    private static final int HEADER = 0xFFFFB330;
    private static final int TEXT = 0xFFF0F0F0;
    private static final int DIM = 0xFFA0A0B0;

    private final Screen parent;
    private int left;
    private int top;
    private int scar;
    private int tattoo;

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
        addRenderableWidget(Button.builder(scarLabel(), b -> {
            scar = (scar + 1) % Cosmetics.SCARS.size();
            b.setMessage(scarLabel());
            send();
        }).bounds(left + 8, top + H - 48, 130, 18).build());
        addRenderableWidget(Button.builder(tattooLabel(), b -> {
            tattoo = (tattoo + 1) % Cosmetics.TATTOOS.size();
            b.setMessage(tattooLabel());
            send();
        }).bounds(left + 8, top + H - 26, 130, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(left + W - 70, top + H - 24, 62, 18).build());
    }

    private Component scarLabel() {
        return Component.translatable("screen.dbzenith.scar", Component.translatable("cosmetic.dbzenith.scar." + Cosmetics.SCARS.get(scar)));
    }

    private Component tattooLabel() {
        return Component.translatable("screen.dbzenith.tattoo", Component.translatable("cosmetic.dbzenith.tattoo." + Cosmetics.TATTOOS.get(tattoo)));
    }

    private void send() {
        ModNetwork.sendToServer(new Cosmetics.Packet(scar, tattoo));
        if (minecraft == null || minecraft.player == null) return;
        PublicStatePacket s = ClientPublicStates.get(minecraft.player.getId());
        if (s != null) { // preview at once; the server's public state confirms it
            ClientPublicStates.put(new PublicStatePacket(s.entityId(), s.flags(), s.release(), s.auraColor(), s.form(), s.overdrive(),
                    s.race(), s.bodyType(), s.hairStyle(), s.hairColor(), s.eyeColor(), s.battlePower(), scar | tattoo << 4));
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.fill(left, top, left + W, top + H, PANEL);
        PlayerData d = ClientPlayerData.get();
        g.drawString(font, title, left + 8, top + 8, HEADER);
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
