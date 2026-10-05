package com.dbzenith.client.screen;

import com.dbzenith.client.ui.DbzTheme;
import com.dbzenith.client.ui.ThemedButton;
import com.dbzenith.client.ClientPlayerData;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.network.UpgradeAttributePacket;
import com.dbzenith.stats.Attribute;
import com.dbzenith.stats.DerivedStats;
import com.dbzenith.stats.StatCalculator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Training screen: attributes, TP cost per point and "+" buttons (shift = +10), plus derived stats.
 * Reads live from {@link ClientPlayerData}; upgrades are requested from and validated by the server.
 */
public class StatScreen extends Screen {
    private static final int W = 320;
    private static final int H = 196;
    private static final int HEADER = 0xFFFFB330;
    private static final int TEXT = 0xFFF0F0F0;
    private static final int DIM = 0xFFA0A0B0;

    private int left;
    private int top;

    public StatScreen() {
        super(Component.translatable("screen.dbzenith.stats"));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        addRenderableWidget(ThemedButton.of(Component.translatable("screen.dbzenith.forms_button"), b -> minecraft.setScreen(new FormScreen(this)))
                .bounds(left + W - 70, top + H - 24, 62, 18).build());
        addRenderableWidget(ThemedButton.of(titleLabel(), b -> {
            java.util.List<com.dbzenith.world.LifeSim.Title> earned = com.dbzenith.world.LifeSim.earnedTitles(ClientPlayerData.get());
            String current = ClientPlayerData.get().getTitle();
            int idx = -1;
            for (int i = 0; i < earned.size(); i++) if (earned.get(i).id().equals(current)) idx = i;
            String next = idx + 1 < earned.size() ? earned.get(idx + 1).id() : "";
            ModNetwork.sendToServer(new com.dbzenith.network.SelectTitlePacket(next));
        }).bounds(left + 8, top + H - 24, 104, 18).build());
        addRenderableWidget(ThemedButton.of(Component.translatable("screen.dbzenith.life_button"), b -> minecraft.setScreen(new LifeScreen(this)))
                .bounds(left + 116, top + H - 24, 44, 18).build());
        addRenderableWidget(ThemedButton.of(Component.translatable("screen.dbzenith.techniques_button"), b -> minecraft.setScreen(new DeckScreen(this)))
                .bounds(left + W - 156, top + H - 24, 82, 18).build());
        if (com.dbzenith.race.Milestones.pathPending(ClientPlayerData.get())) {
            addRenderableWidget(ThemedButton.of(Component.translatable("screen.dbzenith.path_button"), b -> minecraft.setScreen(new PathChoiceScreen(this)))
                    .bounds(left + W - 100, top + H - 68, 92, 18).build().selected(true));
        }
        if (com.dbzenith.stats.Prestige.eligible(ClientPlayerData.get())) {
            addRenderableWidget(ThemedButton.of(Component.translatable("screen.dbzenith.prestige_button"), b -> minecraft.setScreen(
                    new net.minecraft.client.gui.screens.ConfirmScreen(yes -> {
                        if (yes) ModNetwork.sendToServer(new com.dbzenith.stats.Prestige.Packet());
                        minecraft.setScreen(this);
                    }, Component.translatable("screen.dbzenith.prestige_title"),
                            Component.translatable("screen.dbzenith.prestige_confirm", ClientPlayerData.get().getPrestige() + 1))))
                    .bounds(left + W - 80, top + H - 46, 72, 18).build());
        }
        Attribute[] attrs = Attribute.values();
        for (int i = 0; i < attrs.length; i++) {
            Attribute a = attrs[i];
            addRenderableWidget(ThemedButton.of(Component.literal("+"),
                            b -> ModNetwork.sendToServer(new UpgradeAttributePacket(a, hasShiftDown() ? 10 : 1)))
                    .bounds(left + 140, top + 42 + i * 18, 18, 16)
                    .tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("screen.dbzenith.upgrade_tooltip")))
                    .build());
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        DbzTheme.screenBackground(g, width, height);
        DbzTheme.panel(g, left, top, W, H);
        PlayerData d = ClientPlayerData.get();
        DerivedStats s = d.getDerived();

        DbzTheme.header(g, font, title, left + W / 2, top - 6);
        g.drawString(font, Component.translatable(d.getPrestige() > 0 ? "screen.dbzenith.identity_prestige" : "screen.dbzenith.identity_age",
                Component.translatable(d.getVariant().kind() == com.dbzenith.race.Variant.Kind.DEFAULT ? d.getRace().translationKey()
                        : d.getVariant().translationKey()), Component.translatable(d.getPath().translationKey()),
                (int) d.getPhysicalAge(), d.getPrestige()), left + 8, top + 20, DIM);
        g.drawString(font, Component.translatable("screen.dbzenith.tp", String.format("%,d", d.getTrainingPoints())), left + W - 110, top + 8, 0xFF7CFF7C);
        com.dbzenith.race.Alignment.Standing standing = com.dbzenith.race.Alignment.of(d);
        g.drawString(font, Component.translatable("screen.dbzenith.alignment", Component.translatable(standing.translationKey()), d.getAlignment())
                .withStyle(standing.color), left + W - 110, top + 20, 0xFFFFFFFF);

        Attribute[] attrs = Attribute.values();
        g.drawString(font, Component.translatable("screen.dbzenith.cost"), left + 104, top + 32, DIM);
        for (int i = 0; i < attrs.length; i++) {
            Attribute a = attrs[i];
            int y = top + 46 + i * 18;
            g.drawString(font, Component.translatable(a.translationKey()), left + 10, y, TEXT);
            g.drawString(font, String.valueOf(d.getAttribute(a)), left + 76, y, 0xFFFFFFFF);
            g.drawString(font, costText(d, a), left + 104, y, DIM);
        }

        int rx = left + 172;
        int ry = top + 34;
        String[][] rows = {
                {"Body", num(s.maxBody())}, {"Ki", num(s.maxKi())}, {"Stamina", num(s.maxStamina())},
                {"Melee dmg", num(s.meleeDamage())}, {"Ki dmg", num(s.kiDamage())}, {"Defense", num(s.defense())},
                {"Evasion", pct(s.evasion())}, {"Ki control", pct(s.kiControl())},
                {"Spirit", String.format("x%.2f", s.spiritModifier())}, {"Atk speed", pct(s.attackSpeed())},
                {"Move speed", pct(s.moveSpeed())}, {"Power", num(StatCalculator.battlePower(d))}
        };
        for (String[] row : rows) {
            g.drawString(font, row[0], rx, ry, DIM);
            g.drawString(font, row[1], rx + 120 - font.width(row[1]), ry, TEXT);
            ry += 12;
        }
        int godKi = com.dbzenith.transform.GodKi.level(d);
        if (godKi > 0) {                                        // divine ki: level and the way to the next
            int gold = 0xFFFFE08A;
            g.drawString(font, Component.translatable("screen.dbzenith.god_ki"), rx, ry, gold);
            String lv = Component.translatable("screen.dbzenith.god_ki_level", godKi, com.dbzenith.transform.GodKi.MAX).getString();
            g.drawString(font, lv, rx + 120 - font.width(lv), ry, gold);
            DbzTheme.slant(g, rx, ry + 10, 120, 3, 1, 0xC0101018, 0xC0101018);
            DbzTheme.slantBar(g, rx, ry + 10, 120, 3, 1, (float) com.dbzenith.transform.GodKi.progress(d), gold);
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    private static Component titleLabel() {
        String t = ClientPlayerData.get().getTitle();
        return Component.translatable("screen.dbzenith.title_button", t.isEmpty() ? Component.translatable("screen.dbzenith.title_none")
                : Component.translatable("title.dbzenith." + t));
    }

    @Override
    public void tick() {
        rebuildWidgets(); // keep the title button label in sync with the server
    }

    private static String costText(PlayerData d, Attribute a) {
        try {
            return StatCalculator.tpCost(d, a) + " TP";
        } catch (IllegalStateException e) { // server config not available on this client yet
            return "? TP";
        }
    }

    private static String num(double v) {
        return String.format("%,.0f", v);
    }

    private static String pct(double v) {
        return String.format("%.1f%%", v * 100);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
