package com.dbzenith.network;

import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerDataEvents;
import com.dbzenith.skill.CustomTechniques;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client to server: create (or rewrite) a Ki Creator technique in a slot, or delete one. Validated server-side. */
public record KiCreatorPacket(boolean delete, int slot, CustomTechniques.Spec spec) {
    public static void encode(KiCreatorPacket m, FriendlyByteBuf buf) {
        buf.writeBoolean(m.delete);
        buf.writeByte(m.slot);
        if (!m.delete) m.spec.write(buf);
    }

    public static KiCreatorPacket decode(FriendlyByteBuf buf) {
        boolean delete = buf.readBoolean();
        int slot = buf.readByte();
        return new KiCreatorPacket(delete, slot, delete ? null : CustomTechniques.Spec.read(buf));
    }

    public static void handle(KiCreatorPacket m, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player == null) return;
        ModCapabilities.get(player).ifPresent(d -> {
            if (m.delete) {
                CustomTechniques.delete(d, m.slot);
            } else {
                Component why = CustomTechniques.create(d, m.slot, m.spec);
                if (why != null) player.displayClientMessage(why, true);
                else player.displayClientMessage(Component.translatable("kicreator.dbzenith.created",
                        d.getCustomSpec(m.slot).name()), true);
            }
            PlayerDataEvents.sync(player);
        });
    }
}
