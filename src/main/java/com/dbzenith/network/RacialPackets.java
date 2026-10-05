package com.dbzenith.network;

import com.dbzenith.data.ModCapabilities;
import com.dbzenith.race.RacialSkill;
import com.dbzenith.race.RacialSkills;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

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
}
