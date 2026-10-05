package com.dbzenith.race;

import com.dbzenith.config.DBZConfig;
import com.dbzenith.data.PlayerData;
import com.dbzenith.data.PlayerDataEvents;
import com.dbzenith.network.ImpactPacket;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.network.PathPackets;
import com.dbzenith.stats.StatCalculator;
import com.dbzenith.transform.Form;
import com.dbzenith.transform.Forms;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The first milestone ({@code races.milestoneLevel}): a rare destiny rolled at creation awakens (Legendary Saiyan,
 * Mutant Frost Demon, Corrupted Majin...), and races with paths (Half-Saiyan, Human) are asked to choose one.
 */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.dbzenith.DBZenith.MOD_ID)
public final class Milestones {
    private static final Set<UUID> OFFERED = new HashSet<>();   // ask once per session; the Training screen keeps a button

    private Milestones() {}

    public static boolean reached(PlayerData d) {
        return d.isCharacterCreated() && StatCalculator.level(d) >= DBZConfig.SERVER.milestoneLevel.get();
    }

    /** Whether this character still has a path to choose. */
    public static boolean pathPending(PlayerData d) {
        return reached(d) && d.getVariant().kind() == Variant.Kind.DEFAULT && !Variant.paths(d.getRace()).isEmpty();
    }

    /** Once a second. */
    public static void tick(ServerPlayer player, PlayerData d) {
        if (!reached(d)) return;
        if (!d.getDestiny().isEmpty()) awaken(player, d);
        else if (pathPending(d) && OFFERED.add(player.getUUID())) ModNetwork.sendTo(player, new PathPackets.Open());
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onLogout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        OFFERED.remove(event.getEntity().getUUID());
    }

    /** The hidden destiny comes out: the variant changes, and the moment gets a title, a thunderclap and a burst. */
    public static void awaken(ServerPlayer player, PlayerData d) {
        Variant v = Variant.byId(d.getDestiny(), d.getRace());
        d.setDestiny("");
        if (v.kind() != Variant.Kind.RARE) return;
        d.setVariant(v);
        Form form = Forms.byId(d.getFormId());
        if (!form.isBase() && !form.allows(v)) d.setFormId(PlayerData.BASE_FORM);
        d.invalidateDerived();
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(v.translationKey()).withStyle(s -> s.withColor(Math.max(0, v.auraColor())))));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("message.dbzenith.destiny_awakens")));
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1f, 0.8f);
        ImpactPacket.at(player.getBoundingBox().getCenter(), player.getLookAngle(), ImpactPacket.EXPLOSION, 4f, Math.max(0, v.auraColor()),
                player.getId()).send(player.serverLevel());
        PlayerDataEvents.sync(player);
    }

    /** A path chosen from the screen. Only once, only at the milestone, only a path of the character's own race. */
    public static boolean choosePath(PlayerData d, Variant v) {
        if (v == null || v.kind() != Variant.Kind.PATH || v.race() != d.getRace() || !pathPending(d)) return false;
        d.setVariant(v);
        Form form = Forms.byId(d.getFormId());
        if (!form.isBase() && !form.allows(v)) d.setFormId(PlayerData.BASE_FORM);
        d.invalidateDerived();
        return true;
    }
}
