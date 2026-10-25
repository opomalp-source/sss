package com.dbzenith.world.landmark;

import com.dbzenith.living.LivingData;
import com.dbzenith.living.Poi;
import com.dbzenith.living.Resident;
import com.dbzenith.living.Routes;
import com.dbzenith.living.Settlement;
import com.dbzenith.npc.ModNpcs;
import com.dbzenith.world.settle.SettlementPlan;
import com.dbzenith.world.settle.SettlementSites;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * {@code /dbz structures} (operators; docs/WORLD_STRUCTURES.md), for building and checking the world's structures:
 * <ul>
 *   <li>{@code list}: every landmark kind, whether it is unique and guarded, and the nearest one;</li>
 *   <li>{@code info}: the nearest settlement landmark: name, centre, reach, places, population, whether it is registered;</li>
 *   <li>{@code places}: checks each of its places in loaded ground: room to stand (feet and head clear, a floor below)
 *       and a way for a resident from the settlement's centre, walked in legs as residents do (doors opened), naming the ones that fail;</li>
 *   <li>{@code register}: registers it with the living world now (it happens by itself as players come near);</li>
 *   <li>{@code refresh}: brings a registered one's places and map up to its plan (its residents kept);</li>
 *   <li>{@code resettle}: retires its residents and registers it afresh (its plan's people in its homes);</li>
 *   <li>{@code path <from> <to>}: a resident's own path search between two spots, node by node;</li>
 *   <li>{@code rebuild <landmark>}: draws the nearest landmark of a kind again into the loaded chunks it covers (after
 *       a change to its plan; it overwrites what stands there, and spawns its garrison again).</li>
 * </ul>
 */
public final class StructuresCommand {
    private StructuresCommand() {}

    public static ArgumentBuilder<CommandSourceStack, ?> branch() {
        return Commands.literal("structures").requires(s -> s.hasPermission(2))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("info").executes(ctx -> info(ctx.getSource())))
                .then(Commands.literal("places").executes(ctx -> places(ctx.getSource())))
                .then(Commands.literal("register").executes(ctx -> register(ctx.getSource())))
                .then(Commands.literal("refresh").executes(ctx -> refresh(ctx.getSource())))
                .then(Commands.literal("resettle").executes(ctx -> resettle(ctx.getSource())))
                .then(Commands.literal("path").then(Commands.argument("from", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                        .then(Commands.argument("to", net.minecraft.commands.arguments.coordinates.BlockPosArgument.blockPos())
                                .executes(ctx -> path(ctx.getSource(), net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(ctx, "from"),
                                        net.minecraft.commands.arguments.coordinates.BlockPosArgument.getLoadedBlockPos(ctx, "to"))))))
                .then(Commands.literal("rebuild").then(Commands.argument("landmark", StringArgumentType.word())
                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Landmark.ALL).map(l -> l.id), b))
                        .executes(ctx -> rebuild(ctx.getSource(), StringArgumentType.getString(ctx, "landmark")))));
    }

    static void say(CommandSourceStack src, String text) {
        src.sendSuccess(() -> Component.literal(text), false);
    }

    static int list(CommandSourceStack src) {
        ServerLevel level = src.getServer().overworld();
        LandmarkSites.Terrain t = LandmarkSites.Terrain.of(level);
        BlockPos at = BlockPos.containing(src.getPosition());
        for (Landmark type : Landmark.ALL) {
            LandmarkPlan p = LandmarkSites.nearest(type, level.getSeed(), t, at, type.unique ? LandmarkSites.UNIQUE_RINGS * 2 : 4);
            String where = p == null ? "none near" : String.format(Locale.ROOT, "nearest at %d, %d (%d blocks)", p.x, p.z,
                    (int) Math.sqrt(at.distSqr(new BlockPos(p.x, at.getY(), p.z))));
            say(src, type.id + (type.unique ? " [unique]" : "") + (type.guarded ? " [guarded]" : "") + ": " + where);
        }
        return Landmark.ALL.length;
    }

    /** The nearest settlement landmark (of any kind) to where the command runs, or null. */
    static SettlementPlan nearestSettlement(CommandSourceStack src) {
        ServerLevel level = src.getServer().overworld();
        LandmarkSites.Terrain t = LandmarkSites.Terrain.of(level);
        BlockPos at = BlockPos.containing(src.getPosition());
        SettlementPlan best = null;
        double bestD = Double.MAX_VALUE;
        for (Landmark type : Landmark.ALL) {
            LandmarkPlan p = LandmarkSites.nearest(type, level.getSeed(), t, at, type.unique ? LandmarkSites.UNIQUE_RINGS * 2 : 4);
            if (!(p instanceof SettlementPlan sp)) continue;
            double d = at.distSqr(new BlockPos(p.x, at.getY(), p.z));
            if (d < bestD) {
                bestD = d;
                best = sp;
            }
        }
        if (best == null) src.sendFailure(Component.literal("No settlement landmark near"));
        return best;
    }

    static int info(CommandSourceStack src) {
        SettlementPlan sp = nearestSettlement(src);
        if (sp == null) return 0;
        Settlement s = LivingData.get(src.getServer()).settlement(sp.settlementId());
        say(src, String.format(Locale.ROOT, "%s (%s) centre %s, reach %d, %d places, %d people, %s", sp.name(), sp.style(), sp.centre().toShortString(),
                sp.reach(), sp.places().size(), sp.people().size(), s != null ? "registered" : "not registered yet"));
        int[] kinds = new int[Poi.Kind.values().length];
        for (Poi p : sp.places()) kinds[p.kind().ordinal()]++;
        StringBuilder b = new StringBuilder("  places:");
        for (Poi.Kind k : Poi.Kind.values()) if (kinds[k.ordinal()] > 0) b.append(' ').append(k.id()).append(' ').append(kinds[k.ordinal()]);
        say(src, b.toString());
        say(src, "  people: " + String.join(", ", sp.people()));
        return 1;
    }

    /** Whether someone can stand at {@code p}: feet and head clear of anything solid, something to stand on below. */
    public static boolean standable(ServerLevel level, BlockPos p) {
        BlockState below = level.getBlockState(p.below());
        return level.getBlockState(p).getCollisionShape(level, p).isEmpty() && level.getBlockState(p.above()).getCollisionShape(level, p.above()).isEmpty()
                && !below.getCollisionShape(level, p.below()).isEmpty();
    }

    static int places(CommandSourceStack src) {
        SettlementPlan sp = nearestSettlement(src);
        if (sp == null) return 0;
        ServerLevel level = src.getServer().overworld();
        Resident walker = ModNpcs.RESIDENT.get().create(level);                               // a resident's legs, never added to the world
        if (walker == null) return 0;
        BlockPos from = sp.centre();
        Routes.Map map = new Routes.Map(sp.ways(), sp.rooms());
        int ok = 0, unloaded = 0, bad = 0;
        for (int i = 0; i < sp.places().size(); i++) {
            Poi p = sp.places().get(i);
            if (!level.isLoaded(p.pos())) {
                unloaded++;
                continue;
            }
            String problem = null;
            if (!standable(level, p.pos())) problem = "no room to stand";
            else problem = walk(walker, map, from, p.pos());
            if (problem == null) ok++;
            else {
                bad++;
                say(src, "  #" + i + " " + p.kind().id() + " '" + p.label() + "' " + p.pos().toShortString() + ": " + problem);
            }
        }
        say(src, sp.name() + ": " + ok + " places fine, " + bad + " not, " + unloaded + " not loaded");
        return bad == 0 ? 1 : 0;
    }

    /**
     * Walks a resident's way from one spot to another as residents do: the path its own navigation finds (its own
     * range and search, doors opened), then on from where that ends, giving up when a leg brings it no nearer. Returns
     * null when it gets there, else what stopped it.
     */
    static String walk(Resident walker, Routes.Map map, BlockPos from, BlockPos to) {
        BlockPos at = from;
        for (int leg = 0; leg < 40; leg++) {
            BlockPos goal = Routes.next(map, walker.level(), at, to);                                      // by the town's waypoints, as residents go
            walker.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
            walker.setOnGround(true);                                                       // (ground navigation plans only for a mob standing)
            Path path = walker.getNavigation().createPath(goal, 1);
            if (path == null || path.getEndNode() == null) return "no way on from " + at.toShortString() + " toward " + goal.toShortString();
            BlockPos end = path.getEndNode().asBlockPos();
            if (path.canReach()) {
                if (goal.equals(to)) return null;
                at = end;
                continue;
            }
            if (end.distSqr(goal) >= at.distSqr(goal) - 4) return "stuck at " + end.toShortString() + " heading for " + goal.toShortString();
            at = end;
        }
        return "still on its way after 40 legs";
    }

    /** A resident's own path search from one spot to another, node by node (one search, as one leg of a walk). */
    static int path(CommandSourceStack src, BlockPos from, BlockPos to) {
        ServerLevel level = src.getLevel();
        Resident walker = ModNpcs.RESIDENT.get().create(level);
        if (walker == null) return 0;
        walker.moveTo(from.getX() + 0.5, from.getY(), from.getZ() + 0.5);
        walker.setOnGround(true);
        Path path = walker.getNavigation().createPath(to, 1);
        if (path == null) {
            say(src, "no path");
            return 0;
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < path.getNodeCount(); i++) {
            var n = path.getNode(i);
            b.append(n.x).append(',').append(n.y).append(',').append(n.z).append(' ').append(n.type.name().toLowerCase(Locale.ROOT)).append("; ");
        }
        say(src, (path.canReach() ? "reaches" : "doesn't reach") + " in " + path.getNodeCount() + " nodes: " + b);
        return path.canReach() ? 1 : 0;
    }

    /** Brings a registered settlement's places and map up to its plan (after the plan changed), its residents kept. */
    static int refresh(CommandSourceStack src) {
        SettlementPlan sp = nearestSettlement(src);
        if (sp == null) return 0;
        LivingData ld = LivingData.get(src.getServer());
        Settlement s = ld.settlement(sp.settlementId());
        if (s == null) {
            src.sendFailure(Component.literal(sp.name() + " isn't registered yet"));
            return 0;
        }
        s.pois.clear();
        s.pois.addAll(sp.places());
        s.ways.clear();
        s.ways.addAll(sp.ways());
        s.rooms.clear();
        s.rooms.addAll(sp.rooms());
        s.unreachable.clear();
        s.forgetRoutes();
        ld.setDirty();
        say(src, sp.name() + " refreshed: " + s.pois.size() + " places, " + s.ways.size() + " waypoints, " + s.rooms.size() + " rooms");
        return 1;
    }

    /** Retires a registered settlement's residents and registers it afresh from its plan (dev: after the plan's homes or people changed). */
    static int resettle(CommandSourceStack src) {
        SettlementPlan sp = nearestSettlement(src);
        if (sp == null) return 0;
        ServerLevel level = src.getServer().overworld();
        LivingData ld = LivingData.get(src.getServer());
        Settlement old = ld.settlement(sp.settlementId());
        int gone = 0;
        if (old != null) {
            for (com.dbzenith.nemesis.NamedFighter c : com.dbzenith.living.Residents.of(old)) {
                if (c.body != null && level.getEntity(c.body) instanceof Resident r) r.discard();
                c.status = com.dbzenith.nemesis.NamedFighter.Status.DEAD;
                gone++;
            }
            ld.remove(old.id);
        }
        ld.forgetVisit(sp.settlementId());
        Settlement s = SettlementSites.register(level, sp);
        int retired = gone;
        say(src, sp.name() + " resettled: " + retired + " residents retired, " + (s == null ? 0 : com.dbzenith.living.Residents.of(s).size()) + " moved in");
        return s == null ? 0 : 1;
    }

    static int register(CommandSourceStack src) {
        SettlementPlan sp = nearestSettlement(src);
        if (sp == null) return 0;
        Settlement s = SettlementSites.register(src.getServer().overworld(), sp);
        say(src, s == null ? sp.name() + " was registered already" : sp.name() + " registered: " + s.pois.size() + " places");
        return s == null ? 0 : 1;
    }

    static int rebuild(CommandSourceStack src, String id) {
        Landmark type = Landmark.byId(id);
        if (type == null) {
            src.sendFailure(Component.literal("Unknown landmark"));
            return 0;
        }
        ServerLevel level = src.getServer().overworld();
        BlockPos at = BlockPos.containing(src.getPosition());
        LandmarkPlan p = LandmarkSites.nearest(type, level.getSeed(), LandmarkSites.Terrain.of(level), at, type.unique ? LandmarkSites.UNIQUE_RINGS * 2 : 4);
        if (p == null) {
            src.sendFailure(Component.literal("No " + id + " near"));
            return 0;
        }
        int r = type.radius + 8, n = 0;
        for (int cx = (p.x - r) >> 4; cx <= (p.x + r) >> 4; cx++)
            for (int cz = (p.z - r) >> 4; cz <= (p.z + r) >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                p.build(new Canvas(level, cx, cz));
                n++;
            }
        int built = n;
        say(src, "Rebuilt " + id + " at " + p.x + ", " + p.z + " in " + built + " loaded chunks");
        return n;
    }
}
