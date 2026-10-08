package com.dbzenith.client.screen;

import com.dbzenith.client.ClientPlayerData;
import com.dbzenith.client.ui.Ui;
import com.dbzenith.client.ui.UiButton;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.style.StyleLogic;
import com.dbzenith.style.StylePackets;
import com.dbzenith.style.StyleSlot;
import com.dbzenith.style.Styles;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

/**
 * A master's screen (CX-20): the master, their greeting, your affinity with them (hearts), the minutes you have trained
 * with them and a Train button, what they like as a gift, and each style they teach: what it changes (its slots),
 * every requirement ticked off or not, and Learn. The server checks everything again.
 */
public class MasterScreen extends Screen {
    private static final int W = 380, H = 236;
    private final int entityId;
    private final String master;
    private int ox, oy;
    private String shown = "";

    public MasterScreen(int entityId, String master) {
        super(Component.translatable("entity.dbzenith." + master));
        this.entityId = entityId;
        this.master = master;
    }

    private String state() {
        PlayerData d = ClientPlayerData.get();
        StringBuilder b = new StringBuilder(d.getTrainingWith());
        for (Styles.Style s : Styles.taughtBy(master)) {
            b.append('|').append(d.hasStyle(s.id())).append(StyleLogic.meets(minecraft.player, d, s));
        }
        return b.toString();
    }

    @Override
    protected void init() {
        ox = (width - W) / 2;
        oy = (height - H) / 2;
        shown = state();
        PlayerData d = ClientPlayerData.get();
        boolean training = master.equals(d.getTrainingWith());
        addRenderableWidget(UiButton.of(training ? UiButton.Style.SECONDARY : UiButton.Style.PRIMARY,
                Component.translatable(training ? "master.dbzenith.button_stop" : "master.dbzenith.button_train"), ox + 10, oy + H - 24, 112, 16,
                b -> ModNetwork.sendToServer(new StylePackets.Action(StylePackets.Action.Kind.TRAIN, "", "", entityId)))
                .tip(Component.translatable("master.dbzenith.train_tip", (int) StyleLogic.TRAIN_RANGE)));
        List<Styles.Style> taught = Styles.taughtBy(master);
        int y = oy + 30, colX = ox + 134, colW = W - 144, each = taught.isEmpty() ? 0 : (H - 40) / taught.size();
        for (Styles.Style s : taught) {
            boolean has = d.hasStyle(s.id());
            boolean ready = StyleLogic.meets(minecraft.player, d, s);
            UiButton learn = UiButton.of(has ? UiButton.Style.GHOST : ready ? UiButton.Style.PRIMARY : UiButton.Style.SECONDARY,
                    Component.translatable(has ? "master.dbzenith.button_learned" : "master.dbzenith.button_learn"),
                    colX + colW - 70, y + each - 22, 64, 15,
                    b -> ModNetwork.sendToServer(new StylePackets.Action(StylePackets.Action.Kind.LEARN, s.id(), "", entityId)));
            learn.active = !has && ready;
            addRenderableWidget(learn.textScale(0.85f));
            y += each;
        }
    }

    @Override
    public void tick() {
        if (!state().equals(shown)) rebuildWidgets();
        Entity e = minecraft.level == null ? null : minecraft.level.getEntity(entityId);
        if (e == null || e.distanceTo(minecraft.player) > StyleLogic.TALK_RANGE + 4) onClose();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        Styles.Master def = Styles.master(master);
        int glow = def == null ? 0xFFC040 : def.color();
        Ui.backdrop(g, width, height, width / 2f, height * 0.45f, glow);
        Ui.card(g, ox, oy, W, H);
        Ui.text(g, font, Component.literal(title.getString().toUpperCase()), ox + 10, oy + 9, Ui.GOLD, 0.9f);
        g.fill(ox + 8, oy + 22, ox + W - 8, oy + 23, Ui.LINE_SOFT);

        // the master and you
        Entity e = minecraft.level == null ? null : minecraft.level.getEntity(entityId);
        if (e instanceof LivingEntity le) {
            Ui.platform(g, ox + 66, oy + 128, 34, 7, glow);
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, ox + 66, oy + 126, 40, ox + 66 - mouseX, oy + 70 - mouseY, le);
        }
        PlayerData d = ClientPlayerData.get();
        Ui.paragraph(g, font, Component.translatable("master.dbzenith." + master + ".greet"), ox + 10, oy + 136, 116, Ui.TEXT, 0.68f, 4);
        int aff = d.getAffinity(master);
        StringBuilder hearts = new StringBuilder();
        for (int i = 0; i < StyleLogic.MAX_AFFINITY; i++) hearts.append(i < aff ? '❤' : '♡');
        Ui.text(g, font, hearts.toString(), ox + 10, oy + 172, 0xFFFF7AB0, 0.7f);
        Ui.text(g, font, Component.translatable("master.dbzenith.trained", d.getTrainedSeconds(master) / 60), ox + 10, oy + 182, Ui.MUTED, 0.68f);
        if (def != null && !def.likes().isEmpty()) {
            String likes = String.join(", ", def.likes().stream().map(MasterScreen::itemName).toList());
            Ui.paragraph(g, font, Component.translatable("master.dbzenith.likes", likes), ox + 10, oy + 192, 116, Ui.FAINT, 0.62f, 2);
        }

        // the styles
        List<Styles.Style> taught = Styles.taughtBy(master);
        int y = oy + 30, colX = ox + 134, colW = W - 144, each = taught.isEmpty() ? 0 : (H - 40) / taught.size();
        if (taught.isEmpty()) Ui.text(g, font, Component.translatable("master.dbzenith.nothing"), colX, y, Ui.MUTED, 0.75f);
        for (Styles.Style s : taught) {
            Ui.round(g, colX, y, colW, each - 4, 4, 0x24000000 | (s.color() & 0xFFFFFF));
            Ui.text(g, font, Component.translatable(s.nameKey()), colX + 6, y + 5, 0xFF000000 | s.color(), 0.9f);
            int ty = y + 16;
            ty += Ui.paragraph(g, font, Component.translatable(s.descKey()), colX + 6, ty, colW - 12, Ui.MUTED, 0.62f, 3) + 2;
            StringBuilder slots = new StringBuilder();
            for (StyleSlot slot : s.clips().keySet()) slots.append(slots.length() == 0 ? "" : " · ").append(Component.translatable(slot.nameKey()).getString());
            ty += Ui.paragraph(g, font, Component.translatable("master.dbzenith.slots", slots.toString()), colX + 6, ty, colW - 12, 0xFFB8C8E8, 0.58f, 2) + 3;
            for (StyleLogic.Line l : StyleLogic.check(minecraft.player, d, s)) {
                if (ty > y + each - 14) break;
                Ui.text(g, font, (l.met() ? "✔ " : "✘ ") + l.text().getString(), colX + 8, ty, l.met() ? 0xFF8CE08C : 0xFFE07A6A, 0.62f);
                ty += 7;
            }
            y += each;
        }
        super.render(g, mouseX, mouseY, partial);
    }

    private static String itemName(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        var item = rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
        return item == null ? id : new ItemStack(item).getHoverName().getString();
    }

    @Override
    public void renderBackground(GuiGraphics g) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
