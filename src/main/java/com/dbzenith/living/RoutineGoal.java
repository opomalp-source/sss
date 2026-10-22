package com.dbzenith.living;

import com.dbzenith.nemesis.NamedFighter;
import com.dbzenith.npc.KiFighter;
import com.dbzenith.npc.ai.AiRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * A resident's day ({@link Schedules}): where it should be for what it is doing now, walking there, and what it does
 * once there (sleeps in its bed, minds its counter, drills at the dojo, walks the guards' round, wanders the town).
 * Layered for cost: near a player it decides every second; farther than {@code living.activeRange} only every four.
 * A body that loads far from where its day put it, with no one watching, is simply there (it lived on while unloaded).
 * Places it keeps failing to reach are dropped from the town's use.
 */
final class RoutineGoal extends Goal {
    static final double ARRIVED = 1.6;
    private final Resident r;
    private BlockPos dest;
    /** The town's place it is heading for (index in its points), or -1 (its home, or open ground). */
    private int destIndex = -1;
    private long nextDecide, arrivedAt, progressAt;
    private double bestDist = Double.MAX_VALUE;
    private int failures;
    private boolean placed;
    /** A place it gave up on as crowded (it stays where it got to until its day moves on). */
    private BlockPos crowded;

    RoutineGoal(Resident r) {
        this.r = r;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    static boolean active() {
        return LivingConfig.enabled();
    }

    @Override
    public boolean canUse() {
        if (!active() || r.level().isClientSide || r.getTarget() != null || FleeGoal.fleeing(r) || r.talkingTo != null || r.nemesisId() == null) return false;
        Companions.Mode m = Companions.mode(r.character());
        return m == null || m == Companions.Mode.HOME;                                   // a companion out with its player has no day of its own
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return false;
    }

    @Override
    public void stop() {
        r.getNavigation().stop();
        if (r.isSleeping()) r.stopSleeping();
        r.training = false;
        r.setAiFlag(KiFighter.AI_CHARGING, false);
        r.setAiFlag(KiFighter.AI_GUARDING, false);
    }

    @Override
    public void tick() {
        long now = r.level().getGameTime();
        if (now >= nextDecide) {
            boolean near = r.level().getNearestPlayer(r, LivingConfig.activeRange()) != null;
            nextDecide = now + (near ? 20 : 80);
            decide(now);
        }
        if (r.isSleeping()) {                                                            // in bed (off its home spot): it stays put
            if (r.activity == Schedules.Activity.SLEEP) return;
            r.stopSleeping();
        }
        if (r.chatWith != null && now < r.chatUntil) {                                   // chatting: stand and face them
            r.getNavigation().stop();
            var other = ((ServerLevel) r.level()).getEntity(r.chatWith);
            if (other != null) r.getLookControl().setLookAt(other, 30f, 30f);
            return;
        }
        if (dest == null) return;
        double d = Math.sqrt(r.distanceToSqr(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5));
        if (d > ARRIVED && !(d <= 3.0 && r.getNavigation().isDone() && now - progressAt > 40)) {   // close enough when the spot is taken
            if (r.isSleeping()) r.stopSleeping();
            if (r.getNavigation().isDone()) r.getNavigation().moveTo(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5, speed());
            if (d < bestDist - 0.5) {
                bestDist = d;
                progressAt = now;
            } else if (now - progressAt > 160) {                                         // stuck
                progressAt = now;
                bestDist = Double.MAX_VALUE;
                r.getNavigation().stop();
                if (++failures >= 3) {
                    if (d > 6) unreachable();                                               // really out of reach
                    else {
                        failures = 0;                                                       // crowded: it stays where it got to
                        crowded = dest;
                        dest = r.blockPosition();
                    }
                }
            }
            arrivedAt = 0;
            return;
        }
        failures = 0;
        if (arrivedAt == 0) arrivedAt = now;
        there(now);
    }

    double speed() {
        return switch (r.activity) {
            case PATROL -> 0.75;
            case WANDER, SHOP, SOCIAL -> 0.6;
            default -> 0.7;
        };
    }

    // ------------------------------------------------------------------ deciding where to be

    void decide(long now) {
        NamedFighter c = r.character();
        if (c == null) return;
        LivingData ld = LivingData.get(r.getServer());
        Settlement s = ld.settlement(c.settlement);
        Schedules.Activity a = s == null ? Schedules.Activity.WANDER
                : Schedules.activity(c, r.level().getDayTime(), Mood.of(c, r.level()), s.alarmed(now));
        if (a != r.activity) {
            r.activity = a;
            arrivedAt = 0;
        }
        int[] idx = {-1};
        BlockPos to = s == null ? stroll(c, now) : r.leaving ? pick(s, Poi.Kind.GATE, null, c.id.getMostSignificantBits(), 0, idx, s.center) : where(c, s, a, now, idx);
        if (to == null) return;
        if (!to.equals(dest) && !to.equals(crowded)) {
            crowded = null;
            dest = to;
            destIndex = idx[0];
            bestDist = Double.MAX_VALUE;
            progressAt = now;
            arrivedAt = 0;
            r.training = false;
            r.setAiFlag(KiFighter.AI_CHARGING, false);
            r.setAiFlag(KiFighter.AI_GUARDING, false);
            if (r.isSleeping()) r.stopSleeping();
            if (!placed) catchUp();
        }
        placed = true;
    }

    /** Just loaded, far from where its day has it, and nobody near to see: it is already there. */
    void catchUp() {
        if (dest == null || r.distanceToSqr(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5) < 16 * 16) return;
        if (r.level().getNearestPlayer(r, 32) != null) return;
        if (!r.level().isLoaded(dest)) return;
        r.moveTo(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5, r.getYRot(), 0);
    }

    /** Where in town an activity takes it (stable for a while, so it doesn't dither). */
    BlockPos where(NamedFighter c, Settlement s, Schedules.Activity a, long now, int[] idx) {
        Occupation occ = Occupation.of(c);
        long seed = c.id.getMostSignificantBits();
        BlockPos home = c.home == 0 ? s.center : BlockPos.of(c.home);
        return switch (a) {
            case SLEEP, HOME -> home;
            case WORK -> pick(s, occ.workplace, label(occ), seed, now / 6000, idx, home);
            case TRAIN -> pick(s, Poi.Kind.DOJO, occ == Occupation.MASTER ? "master" : "floor", seed, now / 6000, idx, home);
            case EAT -> pick(s, Poi.Kind.TAVERN, "table", seed, now / 3000, idx, home);
            case SOCIAL -> pick(s, Schedules.hour(r.level()) >= 18 ? Poi.Kind.TAVERN : Poi.Kind.PLAZA, null, seed, now / 3000, idx, home);
            case SHOP -> pick(s, Poi.Kind.MARKET, null, seed, now / 2400, idx, home);
            case TRAVEL -> pick(s, Poi.Kind.GATE, null, seed, now / 6000, idx, home);
            case PATROL -> {
                List<Integer> round = indices(s, Poi.Kind.PATROL, null);
                if (round.isEmpty()) yield home;
                int step = (int) Math.floorMod(now / 900 + AiRandom.pick(seed, 0, 31, round.size()), round.size());
                idx[0] = round.get(step);
                yield s.pois.get(idx[0]).pos();
            }
            case WANDER -> {
                Poi.Kind[] kinds = {Poi.Kind.PLAZA, Poi.Kind.MARKET, Poi.Kind.GATE, Poi.Kind.PLAZA, Poi.Kind.PATROL, Poi.Kind.TAVERN};
                Poi.Kind k = kinds[AiRandom.pick(seed, now / 2400, 32, kinds.length)];
                yield pick(s, k, null, seed, now / 2400, idx, home);
            }
        };
    }

    static String label(Occupation o) {
        return switch (o) {
            case COOK -> "kitchen";
            case MASTER -> "master";
            default -> null;
        };
    }

    /** One of the town's places of a kind (and label), chosen by who it is and the time slot. */
    BlockPos pick(Settlement s, Poi.Kind kind, String label, long seed, long slot, int[] idx, BlockPos fallback) {
        List<Integer> all = indices(s, kind, label);
        if (all.isEmpty() && label != null) all = indices(s, kind, null);
        if (all.isEmpty()) return fallback;
        idx[0] = all.get(AiRandom.pick(seed, slot, 33, all.size()));
        return s.pois.get(idx[0]).pos();
    }

    static List<Integer> indices(Settlement s, Poi.Kind kind, String label) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < s.pois.size(); i++) {
            Poi p = s.pois.get(i);
            if (p.kind() == kind && (label == null || label.equals(p.label())) && !s.unreachable.contains(i)) out.add(i);
        }
        return out;
    }

    /** Without a town: a spot near where it lives, now and then. */
    BlockPos stroll(NamedFighter c, long now) {
        BlockPos base = c.home == 0 ? r.blockPosition() : BlockPos.of(c.home);
        if (c.home == 0) c.home = base.asLong();
        long slot = now / 400;
        int dx = AiRandom.pick(c.id.getMostSignificantBits(), slot, 34, 17) - 8, dz = AiRandom.pick(c.id.getLeastSignificantBits(), slot, 35, 17) - 8;
        int x = base.getX() + dx, z = base.getZ() + dz;
        int y = r.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new BlockPos(x, y, z);
    }

    /** It couldn't get there three times running: the town stops using that place. */
    void unreachable() {
        failures = 0;
        NamedFighter c = r.character();
        if (c == null || destIndex < 0) {
            dest = null;
            return;
        }
        LivingData ld = LivingData.get(r.getServer());
        Settlement s = ld.settlement(c.settlement);
        if (s != null) {
            s.unreachable.add(destIndex);
            ld.setDirty();
        }
        dest = null;
        nextDecide = 0;
    }

    // ------------------------------------------------------------------ once there

    void there(long now) {
        long stay = now - arrivedAt;
        if (r.leaving) {                                                                 // a traveler at the gate: on its way
            Travelers.atGate(r);
            return;
        }
        switch (r.activity) {
            case SLEEP -> {
                if (!r.isSleeping() && stay > 20) {
                    BlockPos bed = bed();
                    if (bed != null) r.startSleeping(bed);
                }
            }
            case TRAIN -> {
                boolean drilling = now < r.drillUntil;
                r.training = !drilling;                                                     // the Nemesis way: aura up, a word for those it knows
                if (drilling) {
                    if (now % 12 == 0) r.swing(now % 24 == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, true);
                    r.setAiFlag(KiFighter.AI_GUARDING, now % 48 >= 36);
                } else if (stay % 100 > 70) {
                    r.training = false;
                    r.setAiFlag(KiFighter.AI_CHARGING, false);
                }
            }
            case WORK -> {
                if (now % 80 == 0 && AiRandom.chance(r.getUUID().getMostSignificantBits(), now / 80, 36, 0.4)) r.swing(InteractionHand.MAIN_HAND, true);
                Player p = r.level().getNearestPlayer(r, 6);
                if (p != null) r.getLookControl().setLookAt(p, 30f, 30f);
            }
            case PATROL, WANDER -> {
                if (stay > 140) nextDecide = Math.min(nextDecide, now + 20);
            }
            default -> {
            }
        }
    }

    /** Its bed: the beds by its home, one each by who moved in first. */
    BlockPos bed() {
        NamedFighter c = r.character();
        if (c == null || c.home == 0) return null;
        BlockPos home = BlockPos.of(c.home);
        List<BlockPos> beds = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(home.offset(-3, -1, -3), home.offset(3, 1, 3))) {
            BlockState st = r.level().getBlockState(p);
            if (st.getBlock() instanceof BedBlock && st.getValue(BedBlock.PART) == BedPart.HEAD) beds.add(p.immutable());
        }
        if (beds.isEmpty()) return null;
        beds.sort((a, b) -> Long.compare(a.asLong(), b.asLong()));
        List<NamedFighter> mates = new ArrayList<>();
        for (NamedFighter o : Residents.housemates(c)) mates.add(o);
        mates.sort((a, b) -> a.id.compareTo(b.id));
        int i = Math.max(0, mates.indexOf(c));
        BlockPos bed = beds.get(i % beds.size());
        return r.level().getBlockState(bed).getValue(BedBlock.OCCUPIED) && !bed.equals(r.getSleepingPos().orElse(null)) ? null : bed;
    }
}
