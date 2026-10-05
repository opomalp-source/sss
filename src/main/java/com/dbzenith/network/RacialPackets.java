package com.dbzenith.network;

import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.data.PlayerDataEvents;
import com.dbzenith.race.RacialSkillEffects;
import com.dbzenith.stats.StatCalculator;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import com.dbzenith.race.RacialSkill;
import com.dbzenith.race.RacialSkills;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Racial skills: choosing the active on the Racial key. */
public final class RacialPackets {
    private RacialPackets() {}

    /** Client to server: put this active racial skill on the key. */
    public record Select(String id) {
        public static void encode(Select m, FriendlyByteBuf buf) {
            buf.writeUtf(m.id, 64);
        }

        public static Select decode(FriendlyByteBuf buf) {
            return new Select(buf.readUtf(64));
        }

        public static void handle(Select m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            ModCapabilities.get(player).ifPresent(d -> {
                RacialSkill s = RacialSkills.byId(m.id);
                if (s != null && s.isActive() && s.fits(d.getRace(), d.getVariant())) d.setRacialSelected(s.id());
            });
        }
    }

    /** Client to server: put this universal skill on the Skill key. */
    public record SelectSkill(String id) {
        public static void encode(SelectSkill m, FriendlyByteBuf buf) {
            buf.writeUtf(m.id, 64);
        }

        public static SelectSkill decode(FriendlyByteBuf buf) {
            return new SelectSkill(buf.readUtf(64));
        }

        public static void handle(SelectSkill m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            ModCapabilities.get(player).ifPresent(d -> {
                RacialSkill s = RacialSkills.byId(m.id);
                if (s != null && s.learned() && s.isActive() && RacialSkills.unlocked(d, s)) d.setSkillSelected(s.id());
            });
        }
    }

    /** Client to server: learn the next level of a universal skill. */
    public record Learn(String id) {
        public static void encode(Learn m, FriendlyByteBuf buf) {
            buf.writeUtf(m.id, 64);
        }

        public static Learn decode(FriendlyByteBuf buf) {
            return new Learn(buf.readUtf(64));
        }

        public static void handle(Learn m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            ModCapabilities.get(player).ifPresent(d -> {
                RacialSkill s = RacialSkills.byId(m.id);
                if (s == null) return;
                Component problem = RacialSkills.learnProblem(d, s);
                if (problem != null) {
                    player.displayClientMessage(problem, true);
                    return;
                }
                RacialSkills.learn(d, s);
                if (d.getSkillSelected().isEmpty() && s.isActive()) d.setSkillSelected(s.id());
                player.displayClientMessage(Component.translatable("message.dbzenith.skill_learned",
                        Component.translatable(s.translationKey()), d.getSkillLevel(s.id())), true);
                player.level().playSound(null, player.getX(), player.getY(), player.getZ(), com.dbzenith.registry.ModSounds.ZENKAI.get(), SoundSource.PLAYERS, 0.8f, 1.2f);
                PlayerDataEvents.sync(player);
            });
        }
    }

    /** One place Instant Transmission can take you: a ki you can sense, or home. */
    public record Destination(String key, String name, int distance, long power) {}

    /** Client to server: who can I sense? */
    public record TransmitRequest() {
        public static void encode(TransmitRequest m, FriendlyByteBuf buf) {
        }

        public static TransmitRequest decode(FriendlyByteBuf buf) {
            return new TransmitRequest();
        }

        public static void handle(TransmitRequest m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            ModCapabilities.get(player).ifPresent(d -> {
                if (!RacialSkills.has(d, "instant_transmission")) return;
                ModNetwork.sendTo(player, new TransmitTargets(destinations(player, d)));
            });
        }
    }

    /** Players in your dimension whose ki you can feel (god ki only by god ki), then home. */
    static List<Destination> destinations(ServerPlayer player, PlayerData d) {
        List<Destination> out = new ArrayList<>();
        boolean divine = d.hasFlag("god_ki");
        for (ServerPlayer p : player.serverLevel().players()) {
            if (p == player || p.isSpectator()) continue;
            PlayerData o = ModCapabilities.get(p).orElse(null);
            if (o == null || o.hasFlag("god_ki") && !divine) continue;
            out.add(new Destination(p.getStringUUID(), p.getGameProfile().getName(), (int) p.distanceTo(player),
                    o.hasFlag("god_ki") ? -1 : StatCalculator.battlePower(o)));
        }
        out.sort(Comparator.comparingInt(Destination::distance));
        out.add(new Destination("home", "", (int) Math.sqrt(home(player).distanceToSqr(player.position())), 0));
        return out;
    }

    /** Your bed in this dimension, or the world spawn. */
    static Vec3 home(ServerPlayer player) {
        BlockPos bed = player.getRespawnPosition();
        if (bed != null && player.getRespawnDimension() == player.level().dimension()) return Vec3.atBottomCenterOf(bed).add(0, 1, 0);
        BlockPos spawn = player.serverLevel().getSharedSpawnPos();
        return Vec3.atBottomCenterOf(player.serverLevel().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn));
    }

    /** Server to client: the destinations, to pick from. */
    public record TransmitTargets(List<Destination> list) {
        public static void encode(TransmitTargets m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.list.size());
            for (Destination t : m.list) {
                buf.writeUtf(t.key(), 64);
                buf.writeUtf(t.name(), 64);
                buf.writeVarInt(t.distance());
                buf.writeLong(t.power());
            }
        }

        public static TransmitTargets decode(FriendlyByteBuf buf) {
            int n = Math.min(256, buf.readVarInt());
            List<Destination> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) list.add(new Destination(buf.readUtf(64), buf.readUtf(64), buf.readVarInt(), buf.readLong()));
            return new TransmitTargets(list);
        }

        public static void handle(TransmitTargets m, Supplier<NetworkEvent.Context> ctx) {
            com.dbzenith.client.ClientHooks.openTransmission(m.list);
        }
    }

    /** Client to server: go there. */
    public record Transmit(String key) {
        public static void encode(Transmit m, FriendlyByteBuf buf) {
            buf.writeUtf(m.key, 64);
        }

        public static Transmit decode(FriendlyByteBuf buf) {
            return new Transmit(buf.readUtf(64));
        }

        public static void handle(Transmit m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            ModCapabilities.get(player).ifPresent(d -> {
                for (Destination t : destinations(player, d)) {
                    if (!t.key().equals(m.key)) continue;
                    Vec3 dest;
                    if (t.key().equals("home")) {
                        dest = home(player);
                    } else {
                        ServerPlayer target = player.getServer().getPlayerList().getPlayer(UUID.fromString(t.key()));
                        if (target == null) return;
                        dest = target.position().subtract(target.getLookAngle().multiply(1, 0, 1).normalize().scale(1.5));   // right behind them
                    }
                    RacialSkillEffects.transmit(player, dest);
                    return;
                }
            });
        }
    }
}
