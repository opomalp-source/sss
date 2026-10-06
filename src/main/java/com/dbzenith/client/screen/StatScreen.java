package com.dbzenith.client.screen;

import com.dbzenith.client.ClientPlayerData;
import com.dbzenith.client.ui.Ui;
import com.dbzenith.client.ui.UiButton;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.network.UpgradeAttributePacket;
import com.dbzenith.stats.Attribute;
import com.dbzenith.stats.DerivedStats;
import com.dbzenith.stats.StatCalculator;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The Stats page of the character menu (UI v3): who you are, your seven attributes with their training cost and a
 * "+" each (shift: +10), and what they add up to. Upgrades are requested from and checked by the server.
 */
public class StatScreen extends MenuScreen {
    private static final int ROW = 17;

    public StatScreen() {
        super(Component.translatable("screen.dbzenith.stats"), Page.STATS, null);
    }

    private int leftW() {
        return (int) (cardW * 0.54f);
    }

    @Override
    protected void initPage() {
        int x = cardX + 10, y = cardY + 26, lw = leftW() - 20;
        Attribute[] attrs = Attribute.values();
        for (int i = 0; i < attrs.length; i++) {
            Attribute a = attrs[i];
            addRenderableWidget(UiButton.of(UiButton.Style.CHIP, Component.literal("+"), x + lw - 16, y + i * ROW + 1, 16, 13,
                    b -> ModNetwork.sendToServer(new UpgradeAttributePacket(a, hasShiftDown() ? 10 : 1)))
                    .tip(Component.translatable("screen.dbzenith.upgrade_tooltip")));
        }
        int by = cardY + cardH - 20, bx = cardX + 10;
        UiButton title = UiButton.of(UiButton.Style.SECONDARY, titleLabel(), bx, by, 120, 14, b -> {
            java.util.List<com.dbzenith.world.LifeSim.Title> earned = com.dbzenith.world.LifeSim.earnedTitles(ClientPlayerData.get());
            String current = ClientPlayerData.get().getTitle();
            int idx = -1;
            for (int i = 0; i < earned.size(); i++) if (earned.get(i).id().equals(current)) idx = i;
            String next = idx + 1 < earned.size() ? earned.get(idx + 1).id() : "";
            ModNetwork.sendToServer(new com.dbzenith.network.SelectTitlePacket(next));
        }).textScale(0.8f);
        addRenderableWidget(title);
        bx += 124;
        addRenderableWidget(UiButton.of(UiButton.Style.SECONDARY, Component.translatable("screen.dbzenith.life_button"), bx, by, 48, 14,
                b -> minecraft.setScreen(new LifeScreen(this))).textScale(0.8f));
        bx += 52;
        if (com.dbzenith.race.Milestones.pathPending(ClientPlayerData.get())) {
            addRenderableWidget(UiButton.of(UiButton.Style.PRIMARY, Component.translatable("screen.dbzenith.path_button"), bx, by, 96, 14,
                    b -> minecraft.setScreen(new PathChoiceScreen(this))).textScale(0.8f));
            bx += 100;
        }
        if (com.dbzenith.stats.Prestige.eligible(ClientPlayerData.get())) {
            addRenderableWidget(UiButton.of(UiButton.Style.PRIMARY, Component.translatable("screen.dbzenith.prestige_button"), bx, by, 80, 14,
                    b -> minecraft.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(yes -> {
                        if (yes) ModNetwork.sendToServer(new com.dbzenith.stats.Prestige.Packet());
                        minecraft.setScreen(this);
                    }, Component.translatable("screen.dbzenith.prestige_title"),
                            Component.translatable("screen.dbzenith.prestige_confirm", ClientPlayerData.get().getPrestige() + 1)))).textScale(0.8f));
        }
    }

    @Override
    protected void renderPage(GuiGraphics g, int mouseX, int mouseY, float partial) {
        PlayerData d = ClientPlayerData.get();
        DerivedStats s = d.getDerived();
        int x = cardX + 10, lw = leftW() - 20;

        // who you are
        Component who = Component.translatable(d.getPrestige() > 0 ? "screen.dbzenith.identity_prestige" : "screen.dbzenith.identity_age",
                Component.translatable(d.getVariant().kind() == com.dbzenith.race.Variant.Kind.DEFAULT ? d.getRace().translationKey()
                        : d.getVariant().translationKey()), Component.translatable(d.getPath().translationKey()),
                (int) d.getPhysicalAge(), d.getPrestige());
        Ui.text(g, font, who, x, cardY + 8, 0xFFC8CEDC, 0.8f);
        com.dbzenith.race.Alignment.Standing standing = com.dbzenith.race.Alignment.of(d);
        Component al = Component.translatable("screen.dbzenith.alignment", Component.translatable(standing.translationKey()), d.getAlignment())
                .withStyle(standing.color);
        Ui.text(g, font, al, cardX + cardW - 10 - font.width(al) * 0.8f, cardY + 8, 0xFFFFFFFF, 0.8f);

        // the attributes
        Ui.section(g, font, Component.translatable("screen.dbzenith.attributes"), x, cardY + 18, lw);
        Attribute[] attrs = Attribute.values();
        int y = cardY + 26;
        for (int i = 0; i < attrs.length; i++) {
            Attribute a = attrs[i];
            int ry = y + i * ROW;
            boolean hot = mouseX >= x && mouseX < x + lw && mouseY >= ry && mouseY < ry + ROW - 2;
            Ui.round(g, x, ry, lw, ROW - 2, 2, hot ? 0x1CFFFFFF : 0x0CFFFFFF);
            Ui.text(g, font, Component.translatable(a.translationKey()), x + 6, ry + 3.5f, Ui.TEXT, 0.85f);
            String v = String.valueOf(d.getAttribute(a));
            Ui.text(g, font, v, x + lw * 0.5f - font.width(v) * 0.9f, ry + 3, 0xFFFFFFFF, 0.9f);
            String cost = costText(d, a);
            Ui.text(g, font, cost, x + lw - 22 - font.width(cost) * 0.7f, ry + 4, Ui.MUTED, 0.7f);
        }

        // what they add up to
        int rx = cardX + leftW() + 4, rw = cardX + cardW - 10 - rx;
        Ui.section(g, font, Component.translatable("screen.dbzenith.power"), rx, cardY + 18, rw);
        String[][] rows = {
                {"stat.dbzenith.body", num(s.maxBody())}, {"stat.dbzenith.ki", num(s.maxKi())}, {"stat.dbzenith.stamina", num(s.maxStamina())},
                {"stat.dbzenith.melee", num(s.meleeDamage())}, {"stat.dbzenith.ki_damage", num(s.kiDamage())}, {"stat.dbzenith.defense", num(s.defense())},
                {"stat.dbzenith.evasion", pct(s.evasion())}, {"stat.dbzenith.ki_control", pct(s.kiControl())},
                {"stat.dbzenith.spirit", String.format("x%.2f", s.spiritModifier())}, {"stat.dbzenith.attack_speed", pct(s.attackSpeed())},
                {"stat.dbzenith.move_speed", pct(s.moveSpeed())}, {"stat.dbzenith.battle_power", num(StatCalculator.battlePower(d))}
        };
        int ry = cardY + 28;
        for (int i = 0; i < rows.length; i++) {
            if (i % 2 == 0) Ui.round(g, rx, ry - 2, rw, 10, 1, 0x0AFFFFFF);
            Ui.text(g, font, Component.translatable(rows[i][0]), rx + 4, ry, Ui.MUTED, 0.75f);
            Ui.text(g, font, rows[i][1], rx + rw - 4 - font.width(rows[i][1]) * 0.8f, ry - 0.5f, i == rows.length - 1 ? Ui.GOLD : Ui.TEXT, 0.8f);
            ry += 10;
        }
        int godKi = com.dbzenith.transform.GodKi.level(d);
        if (godKi > 0) {                                        // divine ki: level and the way to the next
            ry += 4;
            Ui.text(g, font, Component.translatable("screen.dbzenith.god_ki"), rx + 4, ry, 0xFFFFE08A, 0.75f);
            String lv = Component.translatable("screen.dbzenith.god_ki_level", godKi, com.dbzenith.transform.GodKi.MAX).getString();
            Ui.text(g, font, lv, rx + rw - 4 - font.width(lv) * 0.75f, ry, 0xFFFFE08A, 0.75f);
            Ui.round(g, rx + 4, ry + 9, rw - 8, 3, 1, 0x50000000);
            Ui.round(g, rx + 4, ry + 9, (int) ((rw - 8) * com.dbzenith.transform.GodKi.progress(d)), 3, 1, 0xFFFFE08A);
        }
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
}
