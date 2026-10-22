package com.dbzenith.living;

import com.dbzenith.data.ModCapabilities;
import com.dbzenith.nemesis.Bond;
import com.dbzenith.nemesis.Memory;
import com.dbzenith.nemesis.NamedFighter;
import com.dbzenith.nemesis.Nemesis;
import com.dbzenith.nemesis.NemesisData;
import com.dbzenith.npc.ai.AiRandom;
import com.dbzenith.npc.ai.Faction;
import com.dbzenith.world.landmark.Landmark;
import com.dbzenith.world.landmark.LandmarkPlan;
import com.dbzenith.world.landmark.LandmarkSites;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Errands: the town's own quests (docs/LIVING_WORLD.md), made from what is really going on there, not from a list.
 * <ul>
 *   <li>a master wants its students tested: spar with two of them;</li>
 *   <li>a student wants to train with you;</li>
 *   <li>a guard knows of a real threat: a named fighter of the Frieza Force, the damned or the Red Ribbon about, or a
 *       character with a grudge against the town; with none, the monsters prowling round it at night;</li>
 *   <li>someone wants a message taken to someone (in another town, if there is one);</li>
 *   <li>the cook needs ingredients, the merchant goods;</li>
 *   <li>someone has fallen out with a neighbour (their bond says so) and asks you to talk to them;</li>
 *   <li>the curious want to know what a real landmark nearby is like (Kami's Lookout, the tournament grounds...).</li>
 * </ul>
 * Up to three are offered per town at a time, one per resident; one player takes one; it is done when the world says
 * so (spars counted on bonds, a named fighter's defeat by you in its memory, monsters you killed near town, the
 * person you talked to, the place you reached, the goods in your bag) and turned in to whoever asked, once: training
 * points, their good opinion and a little fame. Untaken errands lapse after three days; taken ones after five.
 */
public final class Errands {
    static final int OFFERS = 3;
    static final long OFFER_DAYS = 3, TAKEN_DAYS = 5;
    /** Goods: what the cook and the merchant ask for (vanilla items), and how many. */
    static final String[][] COOK = {{"minecraft:wheat", "12"}, {"minecraft:carrot", "8"}, {"minecraft:potato", "8"}, {"minecraft:beef", "4"}, {"minecraft:cod", "4"}};
    static final String[][] MERCHANT = {{"minecraft:iron_ingot", "6"}, {"minecraft:leather", "6"}, {"minecraft:white_wool", "8"}, {"minecraft:glass", "8"}};
    static final Landmark[] SIGHTS = {Landmark.LOOKOUT, Landmark.TOURNAMENT, Landmark.CELL_GAMES, Landmark.FRYPAN};

    private Errands() {}

    // ------------------------------------------------------------------ what is on offer

    public static Errand offeredBy(LivingData ld, UUID giver) {
        for (Errand e : ld.errands()) if (giver.equals(e.giver) && e.taker == null) return e;
        return null;
    }

    public static Errand takenFrom(LivingData ld, UUID giver, UUID player) {
        for (Errand e : ld.errands()) if (giver.equals(e.giver) && player.equals(e.taker)) return e;
        return null;
    }

    /** The errands a player has taken. */
    public static List<Errand> of(LivingData ld, UUID player) {
        List<Errand> out = new ArrayList<>();
        for (Errand e : ld.errands()) if (player.equals(e.taker)) out.add(e);
        return out;
    }

    /** Housekeeping: lapsed errands go; each town is offered new ones from its state, up to three. */
    static void refresh(MinecraftServer server, LivingData ld, long now) {
        for (Errand e : new ArrayList<>(ld.errands())) if (now > e.expires) {
            if (e.taker != null) {
                NamedFighter g = Nemesis.data().get(e.giver);
                if (g != null) g.player(e.taker, "").shift(-3, 0);
            }
            ld.removeErrand(e.id);
        }
        for (Settlement s : ld.settlements()) {
            long open = ld.errands().stream().filter(e -> s.id.equals(e.settlement) && e.taker == null).count();
            if (open >= OFFERS) continue;
            List<NamedFighter> people = Residents.of(s);
            people.removeIf(c -> c.status != NamedFighter.Status.PRESENT || c.companionOf != null || has(ld, c.id));
            if (people.isEmpty()) continue;
            long day = Math.floorDiv(now, 24000L);
            NamedFighter giver = people.get(AiRandom.pick(s.id.getMostSignificantBits(), day * 7 + open, 61, people.size()));
            Errand e = make(server, ld, s, giver, now);
            if (e != null) ld.add(e);
        }
    }

    static boolean has(LivingData ld, UUID giver) {
        for (Errand e : ld.errands()) if (giver.equals(e.giver)) return true;
        return false;
    }

    /** An errand this resident would ask for, from its trade, its bonds and what is about. */
    static Errand make(MinecraftServer server, LivingData ld, Settlement s, NamedFighter c, long now) {
        Errand e = new Errand(UUID.randomUUID());
        e.giver = c.id;
        e.settlement = s.id;
        e.created = now;
        e.expires = now + OFFER_DAYS * 24000;
        long seed = c.id.getMostSignificantBits() ^ Math.floorDiv(now, 24000L);
        Occupation o = Occupation.of(c);
        NamedFighter rival = null;
        for (var b : c.characters.entrySet()) {
            NamedFighter x = Nemesis.data().get(b.getKey());
            if (x != null && x.resident() && x.status == NamedFighter.Status.PRESENT && (b.getValue().affinity < -8 || b.getValue().rivalry > 20)) rival = x;
        }
        if (rival != null && AiRandom.chance(seed, 1, 62, 0.6)) {
            e.kind = Errand.Kind.RESOLVE_RIVALRY;
            e.target = rival.id;
            e.targetName = rival.name;
            e.rewardTp = 150;
            return e;
        }
        switch (o) {
            case MASTER -> {
                e.kind = Errand.Kind.SPAR_STUDENTS;
                e.count = 2;
                e.rewardTp = 200;
            }
            case STUDENT -> {
                e.kind = Errand.Kind.SPAR_ME;
                e.rewardTp = 120;
            }
            case GUARD -> {
                e.kind = Errand.Kind.DEFEAT_THREAT;
                NamedFighter threat = threat(s);
                if (threat != null) {
                    e.target = threat.id;
                    e.targetName = threat.fullName();
                    e.rewardTp = 600;
                } else {
                    e.count = 5;
                    e.rewardTp = 250;
                }
            }
            case COOK, MERCHANT -> {
                String[][] goods = o == Occupation.COOK ? COOK : MERCHANT;
                String[] g = goods[AiRandom.pick(seed, 2, 63, goods.length)];
                e.kind = Errand.Kind.BRING_ITEMS;
                e.item = g[0];
                e.count = Integer.parseInt(g[1]);
                e.rewardTp = 150;
            }
            default -> {
                if (c.has(com.dbzenith.nemesis.Trait.CURIOUS) || c.goal == NamedFighter.Goal.EXPLORE) {
                    if (sight(server, s, e, seed)) return e;
                }
                NamedFighter to = recipient(ld, s, c, seed);
                if (to == null) return null;
                e.kind = Errand.Kind.DELIVER;
                e.target = to.id;
                e.targetName = to.name;
                Settlement where = ld.settlement(to.settlement);
                e.item = where == null || where.id.equals(s.id) ? "" : where.name;
                e.rewardTp = e.item.isEmpty() ? 100 : 220;
            }
        }
        return e;
    }

    /** A named fighter who is trouble for the town: an invader's side, or a grudge against it, last seen within 600 blocks. */
    static NamedFighter threat(Settlement s) {
        NemesisData d = Nemesis.data();
        NamedFighter best = null;
        double bd = 600 * 600;
        for (NamedFighter x : d.all()) {
            if (!x.active() || x.resident() || x.canon) continue;
            var prof = com.dbzenith.nemesis.Progression.profile(x);
            if (prof == null || !prof.faction().enemyOf(Faction.TOWNSFOLK)) continue;
            if (!x.dimension.equals(s.dimension)) continue;
            double dist = BlockPos.of(x.pos).distSqr(s.center);
            if (dist < bd) {
                bd = dist;
                best = x;
            }
        }
        return best;
    }

    /** Someone to take a message to: in another town if there is one, else a neighbour it is fond of. */
    static NamedFighter recipient(LivingData ld, Settlement s, NamedFighter c, long seed) {
        List<NamedFighter> away = new ArrayList<>(), here = new ArrayList<>();
        for (NamedFighter x : Nemesis.data().all()) {
            if (!x.resident() || x == c || x.status == NamedFighter.Status.DEAD || x.settlement == null) continue;
            (s.id.equals(x.settlement) ? here : away).add(x);
        }
        List<NamedFighter> pool = away.isEmpty() ? here : away;
        return pool.isEmpty() ? null : pool.get(AiRandom.pick(seed, 3, 64, pool.size()));
    }

    /** A landmark near the town worth the trip (the terrain is read without making chunks). */
    static boolean sight(MinecraftServer server, Settlement s, Errand e, long seed) {
        ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                new ResourceLocation(s.dimension)));
        if (level == null || level != server.overworld()) return false;
        Landmark kind = SIGHTS[AiRandom.pick(seed, 4, 65, SIGHTS.length)];
        LandmarkPlan plan = LandmarkSites.nearest(kind, level.getSeed(), LandmarkSites.Terrain.of(level), s.center, 2);
        if (plan == null) return false;
        e.kind = Errand.Kind.VISIT_LANDMARK;
        e.item = kind.name().toLowerCase(java.util.Locale.ROOT);
        e.targetName = sightName(kind);
        e.place = new BlockPos(plan.x, 0, plan.z).asLong();
        e.rewardTp = 250;
        return true;
    }

    static String sightName(Landmark k) {
        return switch (k) {
            case LOOKOUT -> "Kami's Lookout";
            case TOURNAMENT -> "the World Martial Arts Tournament grounds";
            case CELL_GAMES -> "the Cell Games arena";
            case FRYPAN -> "the Frypan Mountains";
            default -> "West City";
        };
    }

    // ------------------------------------------------------------------ taking, progress, turning in

    /** The player takes the errand this resident offers. */
    public static boolean accept(LivingData ld, NamedFighter giver, ServerPlayer p, long now) {
        Errand e = offeredBy(ld, giver.id);
        if (e == null || takenFrom(ld, giver.id, p.getUUID()) != null) return false;
        e.taker = p.getUUID();
        e.expires = now + TAKEN_DAYS * 24000;
        e.baseline = switch (e.kind) {
            case SPAR_STUDENTS -> studentSpars(ld, e, p.getUUID());
            case SPAR_ME -> giver.player(p.getUUID(), p.getGameProfile().getName()).spars;
            default -> 0;
        };
        ld.setDirty();
        p.sendSystemMessage(Component.translatable("living.dbzenith.errand.taken", giver.name).withStyle(ChatFormatting.YELLOW)
                .append(Component.literal(" " + summary(e)).withStyle(ChatFormatting.WHITE)));
        return true;
    }

    /** Brings an errand's progress up to date (what can be read off the world now). */
    public static void update(LivingData ld, Errand e, ServerPlayer p) {
        switch (e.kind) {
            case SPAR_STUDENTS -> e.progress = Math.min(e.count, studentSpars(ld, e, p.getUUID()) - e.baseline);
            case SPAR_ME -> {
                NamedFighter g = Nemesis.data().get(e.giver);
                Bond b = g == null ? null : g.players.get(p.getUUID());
                e.progress = b == null ? 0 : Math.min(e.count, b.spars - e.baseline);
            }
            case BRING_ITEMS -> e.progress = Math.min(e.count, countItems(p, e.item));
            default -> {
            }
        }
    }

    static int studentSpars(LivingData ld, Errand e, UUID player) {
        int n = 0;
        Settlement s = ld.settlement(e.settlement);
        if (s == null) return 0;
        for (NamedFighter c : Residents.of(s)) {
            if (Occupation.of(c) != Occupation.STUDENT) continue;
            Bond b = c.players.get(player);
            if (b != null) n += b.spars;
        }
        return n;
    }

    static int countItems(ServerPlayer p, String id) {
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(id));
        if (item == null) return 0;
        int n = 0;
        for (ItemStack st : p.getInventory().items) if (st.is(item)) n += st.getCount();
        return n;
    }

    static void takeItems(ServerPlayer p, String id, int count) {
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(id));
        if (item == null) return;
        for (ItemStack st : p.getInventory().items) {
            if (count <= 0) break;
            if (!st.is(item)) continue;
            int take = Math.min(count, st.getCount());
            st.shrink(take);
            count -= take;
        }
    }

    /** Turns a finished errand in to whoever asked: its reward, once. Returns whether it was done. */
    public static boolean turnIn(LivingData ld, NamedFighter giver, ServerPlayer p, long now) {
        Errand e = takenFrom(ld, giver.id, p.getUUID());
        if (e == null) return false;
        update(ld, e, p);
        if (!e.done()) return false;
        if (e.kind == Errand.Kind.BRING_ITEMS) takeItems(p, e.item, e.count);
        ld.removeErrand(e.id);                                                           // gone first: its reward can't be had twice
        ModCapabilities.get(p).ifPresent(d -> d.addTrainingPoints(e.rewardTp));
        Bond b = giver.player(p.getUUID(), p.getGameProfile().getName());
        b.shift(8, 0);
        b.flags.add("helped");
        giver.remember(new Memory(Memory.Kind.HELPED_BY, now, p.getUUID(), e.kind.id()));
        Fame.add(p.server, p, 3, now);
        p.sendSystemMessage(Component.translatable("living.dbzenith.errand.done", giver.name, e.rewardTp).withStyle(ChatFormatting.GOLD));
        Nemesis.data().setDirty();
        return true;
    }

    /** The player gives up an errand (they think a little less of you). */
    public static void abandon(LivingData ld, NamedFighter giver, ServerPlayer p) {
        Errand e = takenFrom(ld, giver.id, p.getUUID());
        if (e == null) return;
        ld.removeErrand(e.id);
        giver.player(p.getUUID(), p.getGameProfile().getName()).shift(-2, 0);
    }

    // ------------------------------------------------------------------ the world's side

    /** A player talks to someone: a message for them is delivered, a quarrel with them is settled. */
    static void onTalk(LivingData ld, NamedFighter c, ServerPlayer p) {
        for (Errand e : ld.errands()) {
            if (!p.getUUID().equals(e.taker) || !c.id.equals(e.target) || e.done()) continue;
            if (e.kind == Errand.Kind.DELIVER) {
                e.progress = e.count;
                NamedFighter g = Nemesis.data().get(e.giver);
                if (g != null) {
                    c.character(g).shift(6, 0);
                    g.character(c).shift(4, 0);
                }
                p.sendSystemMessage(Component.translatable("living.dbzenith.errand.delivered", c.name, g == null ? "" : g.name).withStyle(ChatFormatting.YELLOW));
            } else if (e.kind == Errand.Kind.RESOLVE_RIVALRY) {
                e.progress = e.count;
                NamedFighter g = Nemesis.data().get(e.giver);
                if (g != null) {
                    Bond a = c.character(g), b = g.character(c);
                    a.shift(20, -15);
                    b.shift(20, -15);
                }
                p.sendSystemMessage(Component.translatable("living.dbzenith.errand.reconciled", c.name, g == null ? "" : g.name).withStyle(ChatFormatting.YELLOW));
            }
            ld.setDirty();
        }
    }

    /** A death: a monster or invader a player killed near a town counts toward its guards' errand. */
    static void onKill(LivingData ld, ServerPlayer p, LivingEntity dead) {
        for (Errand e : ld.errands()) {
            if (e.kind != Errand.Kind.DEFEAT_THREAT || e.target != null || !p.getUUID().equals(e.taker) || e.done()) continue;
            Settlement s = ld.settlement(e.settlement);
            if (s == null || !s.dimension.equals(dead.level().dimension().location().toString()) || dead.blockPosition().distSqr(s.center) > 128 * 128) continue;
            if (!Living.hostile(dead)) continue;
            e.progress++;
            ld.setDirty();
            if (e.done()) p.sendSystemMessage(Component.translatable("living.dbzenith.errand.ready").withStyle(ChatFormatting.YELLOW));
        }
    }

    /** A memory: a named threat beaten (or killed) by the player who took it on. */
    static void onMemory(LivingData ld, NamedFighter c, Memory m) {
        if (m.kind() != Memory.Kind.LOST && m.kind() != Memory.Kind.DIED || m.who() == null) return;
        for (Errand e : ld.errands()) {
            if (e.kind == Errand.Kind.DEFEAT_THREAT && c.id.equals(e.target) && m.who().equals(e.taker) && !e.done()) {
                e.progress = e.count;
                ld.setDirty();
            }
        }
    }

    /** Every two seconds: travellers who reached the landmark they were sent to see. */
    static void tick(MinecraftServer server, LivingData ld) {
        for (Errand e : ld.errands()) {
            if (e.kind != Errand.Kind.VISIT_LANDMARK || e.taker == null || e.done()) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(e.taker);
            if (p == null || p.level() != server.overworld()) continue;
            BlockPos at = BlockPos.of(e.place);
            double dx = p.getX() - at.getX(), dz = p.getZ() - at.getZ();
            if (dx * dx + dz * dz > 64 * 64) continue;
            e.progress = e.count;
            ld.setDirty();
            p.sendSystemMessage(Component.translatable("living.dbzenith.errand.ready").withStyle(ChatFormatting.YELLOW));
        }
    }

    // ------------------------------------------------------------------ in words

    /** What the errand is, in a line (for the player's chat when taking it, and for commands). */
    public static String summary(Errand e) {
        return switch (e.kind) {
            case SPAR_STUDENTS -> "Spar with " + e.count + " of the dojo's students.";
            case SPAR_ME -> "Spar with them.";
            case DEFEAT_THREAT -> e.target != null ? "Defeat " + e.targetName + "." : "Kill " + e.count + " monsters near town.";
            case DELIVER -> "Take a message to " + e.targetName + (e.item.isEmpty() ? "." : " in " + e.item + ".");
            case BRING_ITEMS -> "Bring " + e.count + " " + itemName(e.item) + ".";
            case RESOLVE_RIVALRY -> "Talk to " + e.targetName + " and patch things up.";
            case VISIT_LANDMARK -> "Visit " + e.targetName + " (around " + BlockPos.of(e.place).getX() + ", " + BlockPos.of(e.place).getZ() + ").";
        };
    }

    static String itemName(String id) {
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(id));
        return item == null ? id : net.minecraft.locale.Language.getInstance().getOrDefault(item.getDescriptionId(), Rumors.pretty(id.replace("minecraft:", "")));
    }
}
