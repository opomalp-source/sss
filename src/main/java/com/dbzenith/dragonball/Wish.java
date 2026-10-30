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
 * and every one of Earth's besides; Porunga (Namek's, v0.167.0) his own and a few of Earth's, three wishes a time.
 */
public enum Wish {
    POWER(BallSet.EARTH, true),
    RESTORATION(BallSet.EARTH, true),
    SENZU(BallSet.EARTH),
    IMMORTALITY(BallSet.EARTH, true),
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
    UNIVERSAL_RESTORATION(BallSet.SUPER),
    RESTORE_PLANET(BallSet.EARTH),

    /** Porunga's (v0.167.0): one soul back from the other world, whoever has waited longest (one at a time, as in canon). */
    REVIVE_ONE(BallSet.NAMEK),
    /** Porunga sends the summoner to Earth. */
    SEND_TO_EARTH(BallSet.NAMEK),
    /** Porunga brings the summoner's party to them, wherever they are (as Piccolo was brought to Namek). */
    GATHER_PARTY(BallSet.NAMEK);

    /** Immortality that never runs out. */
    public static final long FOREVER = Long.MAX_VALUE;

    private final BallSet set;
    /** One of Earth's that Porunga grants too. */
    private final boolean porunga;

    Wish(BallSet set) {
        this(set, false);
    }

    Wish(BallSet set, boolean porunga) {
        this.set = set;
        this.porunga = porunga;
    }

    public BallSet set() {
        return set;
    }

    /** The wishes a dragon of this set offers (the curse-lifting wish only comes when it is due). */
    public static List<Wish> of(BallSet set) {
        List<Wish> out = new ArrayList<>();
        if (set == BallSet.NAMEK) {                                                       // Porunga: his own first
            for (Wish w : values()) if (w.set == BallSet.NAMEK) out.add(w);
            for (Wish w : values()) if (w.porunga) out.add(w);
            return out;
        }
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

    public boolean canGrant(ServerPlayer p) {
        return switch (this) {
            case RESTORE_PLANET -> com.dbzenith.galaxy.planet.PlanetDestruction.wishTarget(p) != null;
            case REVIVE_ONE -> com.dbzenith.world.Otherworld.anyDead(p.server);              // someone must be waiting
            case SEND_TO_EARTH -> p.level().dimension() != net.minecraft.world.level.Level.OVERWORLD;
            case GATHER_PARTY -> !partyAway(p).isEmpty();
            default -> true;
        };
    }

    /** The summoner's party-mates online, alive and not already beside them. */
    static List<ServerPlayer> partyAway(ServerPlayer p) {
        List<ServerPlayer> out = new ArrayList<>();
        var party = com.dbzenith.social.SocialData.get(p.server).membership(p.getUUID(), false);
        if (party == null) return out;
        for (java.util.UUID id : party.members.keySet()) {
            ServerPlayer m = p.server.getPlayerList().getPlayer(id);
            if (m == null || m == p || m.isSpectator() || ModCapabilities.get(m).map(PlayerData::isDead).orElse(false)) continue;
            if (m.level() == p.level() && m.distanceToSqr(p) < 16 * 16) continue;
            out.add(m);
        }
        return out;
    }

    public void grant(ServerPlayer player) {
        PlayerData d = ModCapabilities.get(player).orElse(null);
        if (d == null) return;
        DBZConfig.Server c = DBZConfig.SERVER;
        switch (this) {
            case RESTORE_PLANET -> {var ref=com.dbzenith.galaxy.planet.PlanetDestruction.wishTarget(player);if(ref!=null)com.dbzenith.galaxy.planet.PlanetDestruction.restore(player.server,ref);}
            case REVIVE_ONE -> {
                ServerPlayer back = com.dbzenith.world.Otherworld.reviveOne(player.server);
                if (back != null) player.server.getPlayerList().broadcastSystemMessage(
                        Component.translatable("message.dbzenith.wish_revived_one", back.getDisplayName()), false);
            }
            case SEND_TO_EARTH -> {
                net.minecraft.server.level.ServerLevel earth = player.server.overworld();
                net.minecraft.core.BlockPos spawn = earth.getSharedSpawnPos();
                earth.getChunk(spawn.getX() >> 4, spawn.getZ() >> 4);
                int y = earth.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
                player.teleportTo(earth, spawn.getX() + 0.5, y, spawn.getZ() + 0.5, player.getYRot(), 0f);
                player.fallDistance = 0;
            }
            case GATHER_PARTY -> {
                for (ServerPlayer m : partyAway(player)) {
                    m.teleportTo(player.serverLevel(), player.getX() + m.getRandom().nextInt(5) - 2, player.getY(), player.getZ() + m.getRandom().nextInt(5) - 2,
                            m.getYRot(), 0f);
                    m.fallDistance = 0;
                    m.sendSystemMessage(Component.translatable("message.dbzenith.wish_gathered", player.getDisplayName()).withStyle(ChatFormatting.GREEN));
                }
            }
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
        com.dbzenith.combat.Limbs.restore(player);                        // lost limbs back too (CX-54)
        com.dbzenith.combat.Scars.clear(player);                          // and made whole: the scars go (the limbs rework)
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
