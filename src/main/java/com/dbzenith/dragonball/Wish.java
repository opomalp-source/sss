package com.dbzenith.dragonball;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.race.Races;
import com.dbzenith.race.Variant;
import com.dbzenith.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What the dragons can grant. Amounts come from the {@code dragon_balls} config section. Each wish belongs to a set:
 * the Eternal Dragon grants Earth's; the Black Star dragon its own, at the price of the curse; the Super dragon its own
 * and every one of Earth's besides.
 */
public enum Wish {
    POWER(BallSet.EARTH),
    RESTORATION(BallSet.EARTH),
    SENZU(BallSet.EARTH),
    IMMORTALITY(BallSet.EARTH),
    HIDDEN_POTENTIAL(BallSet.EARTH),
    GODLY_KI(BallSet.EARTH),
    ETERNAL_YOUTH(BallSet.EARTH),
    RICHES(BallSet.EARTH),
    /** Bring back the fallen: every soul in the other world returns to the living (CX-12). */
    REVIVE(BallSet.EARTH),

    /** Remake me (12d): a new variant of your race, rare destinies included. */
    REMAKE(BallSet.BLACK_STAR),
    /** Dark power: three times the power wish. */
    BLACK_POWER(BallSet.BLACK_STAR),
    /** Mastery: every form you have unlocked mastered fully. */
    MASTERY(BallSet.BLACK_STAR),
    /** Only from a Black Star dragon raised while the curse is on, and the only thing it grants then. */
    LIFT_CURSE(BallSet.BLACK_STAR),

    /** True immortality (12d): nothing can kill you, for good (until a Super dragon takes it back). */
    TRUE_IMMORTALITY(BallSet.SUPER),
    /** Mortal once more. */
    MORTALITY(BallSet.SUPER),
    /** Divine awakening: godly ki, grown far beyond its first spark. */
    DIVINE_AWAKENING(BallSet.SUPER),
    /** Every soul revived and every living fighter restored, everywhere. */
    UNIVERSAL_RESTORATION(BallSet.SUPER);

    /** Immortality that never runs out. */
    public static final long FOREVER = Long.MAX_VALUE;

    private final BallSet set;

    Wish(BallSet set) {
        this.set = set;
    }

    public BallSet set() {
        return set;
    }

    /** The wishes a dragon of this set offers (the curse-lifting wish only comes when it is due). */
    public static List<Wish> of(BallSet set) {
        List<Wish> out = new ArrayList<>();
        for (Wish w : values()) {
            if (w == LIFT_CURSE) continue;
            if (w.set == set || set == BallSet.SUPER && w.set == BallSet.EARTH) out.add(w);
        }
        return out;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String translationKey() {
        return "wish.dbzenith." + id();
    }

    public void grant(ServerPlayer player) {
        PlayerData d = ModCapabilities.get(player).orElse(null);
        if (d == null) return;
        DBZConfig.Server c = DBZConfig.SERVER;
        switch (this) {
            case POWER -> d.addTrainingPoints(c.wishPowerTp.get());
            case RESTORATION -> restore(player, d);
            case SENZU -> give(player, new ItemStack(ModItems.SENZU_BEAN.get(), c.wishSenzuCount.get()));
            case IMMORTALITY -> {
                if (d.getImmortalUntil() != FOREVER) d.setImmortalUntil(player.level().getGameTime() + c.wishImmortalityTicks.get());
            }
            case HIDDEN_POTENTIAL -> d.setFlag("potential_unlocked", true);
            case GODLY_KI -> d.setFlag("god_ki", true);
            case ETERNAL_YOUTH -> {
                d.setPhysicalAge(20);
                d.recomputeIfStale();
            }
            case RICHES -> give(player, new ItemStack(Items.DIAMOND, c.wishDiamonds.get()));
            case REVIVE -> {
                int n = com.dbzenith.world.Otherworld.reviveAll(player.server);
                player.server.getPlayerList().broadcastSystemMessage(Component.translatable("message.dbzenith.wish_revived", n), false);
            }
            case REMAKE -> {
                Variant v = remake(d, player.getRandom());
                if (v == null) {                                                // a race with one body only: power instead
                    d.addTrainingPoints(c.wishBlackPowerTp.get());
                    player.sendSystemMessage(Component.translatable("message.dbzenith.remake_nothing").withStyle(ChatFormatting.RED));
                } else {
                    d.setVariant(v);
                    d.recomputeIfStale();
                    player.sendSystemMessage(Component.translatable("message.dbzenith.remade", Component.translatable(v.translationKey()))
                            .withStyle(ChatFormatting.RED));
                }
            }
            case BLACK_POWER -> d.addTrainingPoints(c.wishBlackPowerTp.get());
            case MASTERY -> {
                boolean changed = true;                                          // mastering one form can open the next
                for (int pass = 0; pass < 12 && changed; pass++) {
                    changed = false;
                    for (com.dbzenith.transform.Form f : com.dbzenith.transform.Forms.all()) {
                        if (f.isBase() || d.getMastery(f.id()) >= 100 || com.dbzenith.transform.FormHandler.problem(d, f) != null) continue;
                        d.setMastery(f.id(), 100);
                        changed = true;
                    }
                }
            }
            case LIFT_CURSE -> DragonBalls.liftCurse(player.server);
            case TRUE_IMMORTALITY -> d.setImmortalUntil(FOREVER);
            case MORTALITY -> d.setImmortalUntil(-1);
            case DIVINE_AWAKENING -> {
                d.setFlag("god_ki", true);
                d.setGodKiXp(d.getGodKiXp() + 2000);
            }
            case UNIVERSAL_RESTORATION -> {
                int n = com.dbzenith.world.Otherworld.reviveAll(player.server);
                for (ServerPlayer p : player.server.getPlayerList().getPlayers()) {
                    ModCapabilities.get(p).ifPresent(pd -> restore(p, pd));
                }
                player.server.getPlayerList().broadcastSystemMessage(Component.translatable("message.dbzenith.wish_restored_all", n), false);
            }
        }
    }

    private static void restore(ServerPlayer player, PlayerData d) {
        d.refill();
        player.setHealth(player.getMaxHealth());
        if (Races.of(d.getRace()).tail()) d.setTail(true);
        player.removeAllEffects();
    }

    /** A new variant of the same race, never the one you have: creation choices, rare destinies, and paths once on one. */
    public static Variant remake(PlayerData d, net.minecraft.util.RandomSource rnd) {
        Variant now = d.getVariant();
        List<Variant> pool = new ArrayList<>();
        for (Variant v : Variant.values()) {
            if (v.race() != d.getRace() || v == now) continue;
            if (v.kind() == Variant.Kind.PATH && now.kind() != Variant.Kind.PATH) continue;
            if (v.kind() != Variant.Kind.PATH && now.kind() == Variant.Kind.PATH) continue;
            pool.add(v);
        }
        return pool.isEmpty() ? null : pool.get(rnd.nextInt(pool.size()));
    }

    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }
}
