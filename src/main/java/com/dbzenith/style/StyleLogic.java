package com.dbzenith.style;

import com.dbzenith.data.PlayerData;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.stats.StatCalculator;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Learning styles from masters (CX-20), on the server; the requirement check is shared with the screens.
 * <ul>
 *   <li><b>Affinity</b> (0..10 per master): +1 for the first talk of each day, +2 for a gift they like (three a day),
 *       +1 for every five minutes trained with them.</li>
 *   <li><b>Training:</b> "Train" in the master's screen starts a session; every second you stay within
 *       {@link #TRAIN_RANGE} blocks of them counts (the session ends if you leave).</li>
 *   <li><b>Learning:</b> once every requirement is met, the items asked for are handed over and the style is yours. It
 *       goes straight into every slot you had left on the default; the K menu's Styles page changes any slot.</li>
 * </ul>
 */
public final class StyleLogic {
    public static final int MAX_AFFINITY = 10, GIFTS_PER_DAY = 3, GIFT_AFFINITY = 2, TRAIN_AFFINITY_SECONDS = 300;
    public static final double TRAIN_RANGE = 10, TALK_RANGE = 8;

    /** One requirement and whether it is met: a lang key with its arguments. */
    public record Line(String key, Object[] args, boolean met) {
        public Component text() {
            return Component.translatable(key, args);
        }
    }

    private StyleLogic() {}

    // ------------------------------------------------------------------ requirements

    public static List<Line> check(Player p, PlayerData d, Styles.Style s) {
        Styles.Requirements r = s.requirements();
        List<Line> out = new ArrayList<>();
        if (r.battlePower() > 0) {
            long bp = StatCalculator.battlePower(d);
            out.add(new Line("style.dbzenith.req.bp", new Object[]{String.format("%,d", r.battlePower())}, bp < 0 || bp >= r.battlePower()));
        }
        if (r.affinity() > 0) {
            out.add(new Line("style.dbzenith.req.affinity", new Object[]{r.affinity(), d.getAffinity(s.master())}, d.getAffinity(s.master()) >= r.affinity()));
        }
        if (r.trainingMinutes() > 0) {
            int done = d.getTrainedSeconds(s.master()) / 60;
            out.add(new Line("style.dbzenith.req.training", new Object[]{r.trainingMinutes(), Math.min(done, r.trainingMinutes())}, done >= r.trainingMinutes()));
        }
        for (Styles.ItemReq i : r.items()) {
            Item item = item(i.item());
            int have = item == null || p == null ? 0 : p.getInventory().countItem(item);
            out.add(new Line("style.dbzenith.req.item", new Object[]{i.count(), item == null ? Component.literal(i.item()) : new ItemStack(item).getHoverName(),
                    Math.min(have, i.count())}, have >= i.count()));
        }
        if (!r.quest().isEmpty()) {
            out.add(new Line("style.dbzenith.req.quest", new Object[]{Component.translatable("quest." + r.quest().replace(':', '.'))}, d.timesCompleted(r.quest()) > 0));
        }
        if (!r.races().isEmpty()) {
            String race = d.getRace().id();
            Object names = Component.literal(String.join(", ", r.races().stream()
                    .map(id -> Component.translatable("race.dbzenith." + id).getString()).toList()));
            out.add(new Line("style.dbzenith.req.race", new Object[]{names}, r.races().contains(race)));
        }
        if (!r.flag().isEmpty()) out.add(new Line("style.dbzenith.req.flag." + r.flag(), new Object[0], d.hasFlag(r.flag())));
        return out;
    }

    public static boolean meets(Player p, PlayerData d, Styles.Style s) {
        for (Line l : check(p, d, s)) if (!l.met()) return false;
        return true;
    }

    static Item item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? null : ForgeRegistries.ITEMS.getValue(rl);
    }

    // ------------------------------------------------------------------ the master

    /** A right-click on a master: a gift if you hold one they like, else a talk (affinity once a day) and their screen. */
    public static void talk(ServerPlayer p, PlayerData d, StyleMaster m, ItemStack held) {
        String id = m.masterId();
        Styles.Master def = Styles.master(id);
        if (def == null) return;
        String heldId = held.isEmpty() ? "" : String.valueOf(ForgeRegistries.ITEMS.getKey(held.getItem()));
        long day = p.level().getDayTime() / 24000L;
        if (d.getMasterDay(id) != day) {
            d.setMasterDay(id, day);
            if (d.getAffinity(id) < MAX_AFFINITY) {
                d.setAffinity(id, d.getAffinity(id) + 1);
                p.displayClientMessage(Component.translatable("master.dbzenith.affinity_up", Component.translatable(def.nameKey()), d.getAffinity(id))
                        .withStyle(ChatFormatting.LIGHT_PURPLE), true);
                hearts(p, m);
            }
        }
        if (!heldId.isEmpty() && def.likes().contains(heldId)) {
            if (d.getGiftsToday(id) >= GIFTS_PER_DAY) {
                p.displayClientMessage(Component.translatable("master.dbzenith.gift_enough", Component.translatable(def.nameKey())), true);
            } else {
                if (!p.getAbilities().instabuild) held.shrink(1);
                d.addGiftToday(id);
                d.setAffinity(id, d.getAffinity(id) + GIFT_AFFINITY);
                p.displayClientMessage(Component.translatable("master.dbzenith.gift_liked", Component.translatable(def.nameKey()), d.getAffinity(id))
                        .withStyle(ChatFormatting.LIGHT_PURPLE), true);
                hearts(p, m);
            }
            return;
        }
        ModNetwork.sendTo(p, new StylePackets.OpenMaster(m.getId(), id));
    }

    private static void hearts(ServerPlayer p, Entity m) {
        if (p.level() instanceof ServerLevel lvl) {
            lvl.sendParticles(ParticleTypes.HEART, m.getX(), m.getY() + m.getBbHeight() + 0.3, m.getZ(), 4, 0.3, 0.2, 0.3, 0);
            lvl.playSound(null, m.getX(), m.getY(), m.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.NEUTRAL, 0.4f, 1.6f);
        }
    }

    private static StyleMaster near(ServerPlayer p, int entityId, double range) {
        Entity e = p.level().getEntity(entityId);
        return e instanceof StyleMaster m && m.isAlive() && m.distanceTo(p) <= range ? m : null;
    }

    /** "Train" in a master's screen: starts a session with them (or ends the one with them). */
    public static void train(ServerPlayer p, PlayerData d, int entityId) {
        StyleMaster m = near(p, entityId, TALK_RANGE);
        if (m == null) return;
        Styles.Master def = Styles.master(m.masterId());
        if (def == null) return;
        if (m.masterId().equals(d.getTrainingWith())) {
            d.setTrainingWith("");
            p.displayClientMessage(Component.translatable("master.dbzenith.train_stop", Component.translatable(def.nameKey())), true);
        } else {
            d.setTrainingWith(m.masterId());
            p.displayClientMessage(Component.translatable("master.dbzenith.train_start", Component.translatable(def.nameKey()),
                    (int) TRAIN_RANGE).withStyle(ChatFormatting.GOLD), true);
        }
    }

    /** Every second from KiTicker: the training session. */
    public static void tick(ServerPlayer p, PlayerData d, long now) {
        String id = d.getTrainingWith();
        if (id.isEmpty() || now % 20 != 0) return;
        Styles.Master def = Styles.master(id);
        boolean near = def != null && !p.level().getEntitiesOfClass(StyleMaster.class, p.getBoundingBox().inflate(TRAIN_RANGE),
                m -> m.masterId().equals(id) && m.isAlive()).isEmpty();
        if (!near) {
            d.setTrainingWith("");
            if (def != null) p.displayClientMessage(Component.translatable("master.dbzenith.train_left", Component.translatable(def.nameKey())), true);
            return;
        }
        d.addTrainedSeconds(id, 1);
        int s = d.getTrainedSeconds(id);
        if (s % TRAIN_AFFINITY_SECONDS == 0) d.setAffinity(id, d.getAffinity(id) + 1);
        if (s % 60 == 0) {
            p.displayClientMessage(Component.translatable("master.dbzenith.train_progress", Component.translatable(def.nameKey()), s / 60)
                    .withStyle(ChatFormatting.GOLD), true);
        }
    }

    /** "Learn" in a master's screen: checked again here; the items asked for are handed over. */
    public static boolean learn(ServerPlayer p, PlayerData d, int entityId, String styleId) {
        Styles.Style s = Styles.style(styleId);
        StyleMaster m = near(p, entityId, TALK_RANGE);
        if (s == null || m == null || !s.master().equals(m.masterId()) || d.hasStyle(styleId)) return false;
        if (!meets(p, d, s)) {
            p.displayClientMessage(Component.translatable("master.dbzenith.not_ready"), true);
            return false;
        }
        if (!p.getAbilities().instabuild) {
            for (Styles.ItemReq i : s.requirements().items()) {
                Item item = item(i.item());
                int left = i.count();
                for (int k = 0; k < p.getInventory().getContainerSize() && left > 0 && item != null; k++) {
                    ItemStack st = p.getInventory().getItem(k);
                    if (st.is(item)) {
                        int take = Math.min(left, st.getCount());
                        st.shrink(take);
                        left -= take;
                    }
                }
            }
        }
        grant(d, styleId);
        p.displayClientMessage(Component.translatable("master.dbzenith.learned", Component.translatable(s.nameKey())).withStyle(ChatFormatting.GOLD), false);
        hearts(p, m);
        return true;
    }

    /** Gives a style and puts it in every slot it fills that is still on the default. */
    public static void grant(PlayerData d, String styleId) {
        Styles.Style s = Styles.style(styleId);
        d.learnStyle(styleId, true);
        if (s == null) return;
        for (StyleSlot slot : s.clips().keySet()) if (d.getStyleSlot(slot.id).isEmpty()) d.setStyleSlot(slot.id, styleId);
    }

    /** The Styles page: a slot set to a learned style that fills it, or "" for the default. */
    public static boolean equip(ServerPlayer p, PlayerData d, String slotId, String styleId) {
        StyleSlot slot = StyleSlot.byId(slotId);
        if (slot == null) return false;
        if (styleId.isEmpty()) {
            d.setStyleSlot(slot.id, "");
            return true;
        }
        Styles.Style s = Styles.style(styleId);
        if (s == null || !d.hasStyle(styleId) || !s.has(slot)) return false;
        d.setStyleSlot(slot.id, styleId);
        return true;
    }
}
