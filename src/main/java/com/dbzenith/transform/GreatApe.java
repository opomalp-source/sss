package com.dbzenith.transform;

import com.dbzenith.DBZenith;
import com.dbzenith.client.ClientPublicStates;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.network.PublicStatePacket;
import com.dbzenith.registry.ModEntities;
import com.dbzenith.stats.Race;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Great Ape: a Saiyan (or Half-Saiyan) with a tail who sees a full moon at night, or a false moon,
 * turns into a giant. Losing sight of the moon reverts them, exhausted. Also scales player size for forms.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GreatApe {
    public static final double FALSE_MOON_RANGE = 64;

    private GreatApe() {}

    public static boolean canTransform(PlayerData data) {
        return (data.getRace() == Race.SAIYAN || data.getRace() == Race.HALF_SAIYAN) && data.hasTail();
    }

    /** Called once per second from KiTicker. */
    public static void tick(ServerPlayer player, PlayerData data) {
        boolean moon = seesMoon(player);
        boolean ape = Forms.GREAT_APE.id().equals(data.getFormId());
        if (!ape && moon && canTransform(data) && !data.isTransformed()) {
            FormHandler.enter(player, data, Forms.GREAT_APE);
            player.displayClientMessage(Component.translatable("message.dbzenith.great_ape"), true);
        } else if (ape && (!moon || !canTransform(data))) {
            FormHandler.revertToBase(player, data);
            data.setStamina(0);
            player.displayClientMessage(Component.translatable("message.dbzenith.great_ape_end"), true);
        }
    }

    public static boolean seesMoon(Player player) {
        Level level = player.level();
        boolean sky = level.canSeeSky(player.blockPosition().above((int) Math.ceil(player.getBbHeight())));
        if (!sky) return false;
        boolean fullMoon = level.dimensionType().hasSkyLight() && level.isNight() && level.getMoonPhase() == 0;
        return fullMoon || nearFalseMoon(player);
    }

    public static boolean nearFalseMoon(Player player) {
        return !player.level().getEntitiesOfClass(FalseMoonEntity.class,
                player.getBoundingBox().inflate(FALSE_MOON_RANGE, 160, FALSE_MOON_RANGE), FalseMoonEntity::isRisen).isEmpty();
    }

    /** Size of a player's current form: from capability data on the server, from public state on clients. */
    public static float scaleOf(Player player) {
        String form;
        if (player.level().isClientSide) {
            PublicStatePacket state = ClientPublicStates.get(player.getId());
            if (state == null) return 1f;
            form = state.form();
        } else {
            PlayerData d = ModCapabilities.get(player).orElse(null);
            if (d == null) return 1f;
            form = d.getFormId();
        }
        return Forms.byId(form).scale();
    }

    @SubscribeEvent
    public static void onSize(EntityEvent.Size event) {
        if (!(event.getEntity() instanceof Player player)) return;
        float s = scaleOf(player);
        if (s == 1f) return;
        float eye = event.getNewEyeHeight();
        event.setNewSize(event.getNewSize().scale(s));
        event.setNewEyeHeight(eye * s);
    }

    /** Used by the Moon Orb item. */
    public static FalseMoonEntity spawnFalseMoon(Level level, double x, double y, double z) {
        FalseMoonEntity moon = new FalseMoonEntity(ModEntities.FALSE_MOON.get(), level);
        moon.setPos(x, y, z);
        moon.setStartY(y);
        level.addFreshEntity(moon);
        return moon;
    }
}
