package com.dbzenith.client.screen;

import com.dbzenith.client.ui.DbzTheme;
import com.dbzenith.client.ui.ThemedButton;
import com.dbzenith.client.ClientPlayerData;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.network.QuestPackets;
import com.dbzenith.quest.Quest;
import com.dbzenith.quest.QuestManager;
import com.dbzenith.quest.Quests;
import com.dbzenith.skill.Technique;
import com.dbzenith.skill.Techniques;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/** A quest giver's board: objectives with live progress, accept / turn-in buttons, rewards on hover. */
public class QuestScreen extends Screen {
    private static final int W = 400;
    private static final int H = 236;
    private static final int ROW = 26;
    private static final int HEADER = 0xFFFFB330;
    private static final int TEXT = 0xFFF0F0F0;
    private static final int DIM = 0xFFA0A0B0;
    private static final int GOOD = 0xFF7CFF7C;

    private final Quest.Giver giver;
    private int left;
    private int top;
    private int age;

    public QuestScreen(Quest.Giver giver) {
        super(Component.translatable("screen.dbzenith.quests." + giver.name().toLowerCase()));
        this.giver = giver;
    }

    private List<Quest> shown() {
        return Quests.by(giver);
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        PlayerData d = ClientPlayerData.get();
        List<Quest> quests = shown();
        for (int i = 0; i < quests.size(); i++) {
            Quest q = quests.get(i);
            int y = top + 30 + i * ROW;
            boolean active = d.isQuestActive(q.id());
            boolean complete = active && minecraft.player != null && QuestManager.isComplete(minecraft.player, d, q);
            String why = QuestManager.unavailableReason(d, q);
            Button b;
            if (complete) {
                b = ThemedButton.of(Component.translatable("screen.dbzenith.quest_turn_in"),
                        x -> ModNetwork.sendToServer(new QuestPackets.Action(true, q.id()))).bounds(left + W - 92, y, 84, 18).build();
            } else if (active) {
                b = ThemedButton.of(Component.translatable("screen.dbzenith.quest_in_progress"), x -> {}).bounds(left + W - 92, y, 84, 18).build();
                b.active = false;
            } else if (why == null) {
                b = ThemedButton.of(Component.translatable("screen.dbzenith.quest_accept"),
                        x -> ModNetwork.sendToServer(new QuestPackets.Action(false, q.id()))).bounds(left + W - 92, y, 84, 18).build();
            } else {
                b = ThemedButton.of(Component.translatable(why.equals("quest.dbzenith.why.done") ? "screen.dbzenith.quest_done" : "screen.dbzenith.quest_locked"),
                        x -> {}).bounds(left + W - 92, y, 84, 18).build();
                b.active = false;
            }
            addRenderableWidget(b);
        }
        addRenderableWidget(ThemedButton.of(Component.translatable("gui.done"), x -> onClose()).bounds(left + W - 70, top + H - 24, 62, 18).build());
    }

    @Override
    public void tick() {
        if (++age % 10 == 0) rebuildWidgets(); // follow server-synced progress
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        DbzTheme.screenBackground(g, width, height);
        DbzTheme.panel(g, left, top, W, H);
        PlayerData d = ClientPlayerData.get();
        DbzTheme.header(g, font, title, left + W / 2, top - 6);
        if (giver == Quest.Giver.PATROL) {
            int rank = QuestManager.patrolRank(d);
            g.drawString(font, Component.translatable("screen.dbzenith.patrol_rank",
                    Component.translatable("patrol.dbzenith.rank." + QuestManager.RANK_KEYS[rank]), d.getPatrolRep()), left + 8, top + 18, DIM);
        } else {
            g.drawString(font, Component.translatable("screen.dbzenith.master_sub"), left + 8, top + 18, DIM);
        }

        List<Quest> quests = shown();
        Quest hovered = null;
        for (int i = 0; i < quests.size(); i++) {
            Quest q = quests.get(i);
            int y = top + 30 + i * ROW;
            boolean done = d.timesCompleted(q.id()) > 0 && !q.repeatable();
            g.drawString(font, Component.translatable(q.titleKey()), left + 10, y, done ? DIM : 0xFFFFE080);
            g.drawString(font, objectivesLine(d, q), left + 10, y + 10, d.isQuestActive(q.id()) ? TEXT : DIM);
            if (mouseX >= left + 6 && mouseX < left + W - 96 && mouseY >= y - 2 && mouseY < y + ROW - 4) hovered = q;
        }
        super.render(g, mouseX, mouseY, partialTick);
        if (hovered != null) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable(hovered.descKey()));
            lines.add(rewardsLine(hovered));
            List<net.minecraft.util.FormattedCharSequence> wrapped = new ArrayList<>();
            for (Component c : lines) wrapped.addAll(font.split(c, 240));
            g.renderTooltip(font, wrapped, mouseX, mouseY);
        }
    }

    private Component objectivesLine(PlayerData d, Quest q) {
        MutableComponent line = Component.empty();
        for (int i = 0; i < q.objectives().size(); i++) {
            Quest.Objective o = q.objectives().get(i);
            int have = minecraft.player == null ? 0 : QuestManager.progress(minecraft.player, d, q, i);
            if (i > 0) line.append("   ");
            boolean met = have >= o.amount();
            boolean event = o.type() == Quest.Objective.Type.KILL && !net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.containsKey(new ResourceLocation(o.target()));
            line.append(Component.translatable(event ? "objective.dbzenith.event" : "objective.dbzenith." + o.type().name().toLowerCase(), targetName(o), have, o.amount())
                    .withStyle(s -> s.withColor(met ? GOOD : (d.isQuestActive(q.id()) ? TEXT : DIM))));
        }
        return line;
    }

    private static Component targetName(Quest.Objective o) {
        return switch (o.type()) {
            case KILL -> {
                ResourceLocation id = new ResourceLocation(o.target());
                yield Component.translatable("entity." + id.getNamespace() + "." + id.getPath());
            }
            case COLLECT_ITEM -> {
                ResourceLocation id = new ResourceLocation(o.target());
                yield Component.translatable("item." + id.getNamespace() + "." + id.getPath());
            }
            case LEARN_TECHNIQUE -> {
                Technique t = Techniques.byId(o.target());
                yield t == null ? Component.literal(o.target()) : Component.translatable(t.translationKey());
            }
            case VISIT_DIMENSION -> Component.translatable("dimension." + o.target().replace(':', '.'));
            case HAS_FLAG -> Component.translatable("objective.dbzenith.flag." + o.target());
            default -> Component.empty();
        };
    }

    private static Component rewardsLine(Quest q) {
        Quest.Reward r = q.reward();
        MutableComponent c = Component.translatable("screen.dbzenith.quest_rewards").withStyle(s -> s.withColor(HEADER));
        if (r.tp() > 0) c.append(" ").append(Component.translatable("screen.dbzenith.reward_tp", r.tp()));
        for (Quest.ItemReward ir : r.items()) {
            ResourceLocation id = new ResourceLocation(ir.itemId());
            c.append(", ").append(Component.literal(ir.count() + "x ")).append(Component.translatable("item." + id.getNamespace() + "." + id.getPath()));
        }
        if (r.patrolRep() > 0) c.append(", ").append(Component.translatable("screen.dbzenith.reward_rep", r.patrolRep()));
        if (!r.flag().isEmpty()) c.append(", ").append(Component.translatable("screen.dbzenith.reward_flag." + r.flag()));
        return c;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
