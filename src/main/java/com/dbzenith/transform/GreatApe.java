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

    /** Seconds of looking at the moon before the change takes them (the user: "they have to look at the full moon for a while"). */
    public static final int GAZE_SECONDS = 6;
    /** How close to the moon the look must be (cosine of about 14 degrees). */
    private static final double GAZE_CONE = 0.97;

    /**
     * Called once per second from KiTicker. A Saiyan with a tail turns only under a full moon (or a false moon), and only
     * after looking at it for {@link #GAZE_SECONDS} seconds: their heart pounds harder each second they stare (heard,
     * and shown at the screen's edges, {@code PlayerData.getMoonGaze}); looking away lets it calm again. Once a Great
     * Ape, losing the moon (its night ending, a roof, the false moon gone) or the tail turns them back, exhausted.
     */
    public static void tick(ServerPlayer player, PlayerData data) {
        boolean moon = seesMoon(player);
        boolean ape = Forms.GREAT_APE.id().equals(data.getFormId());
        if (ape) {
            data.setMoonGaze(0);
            if (!moon || !canTransform(data)) {
                FormHandler.revertToBase(player, data);
                data.setStamina(0);
                player.displayClientMessage(Component.translatable("message.dbzenith.great_ape_end"), true);
            }
            return;
        }
        boolean gazing = moon && canTransform(data) && !data.isTransformed() && lookingAtMoon(player);
        int gaze = gazing ? data.getMoonGaze() + 1 : Math.max(0, data.getMoonGaze() - 2);
        data.setMoonGaze(gaze);
        if (gazing) {                                                               // the heartbeat, louder and quicker each second
            float k = gaze / (float) GAZE_SECONDS;
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.WARDEN_HEARTBEAT,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.6f + 1.2f * k, 0.8f + 0.5f * k);
            if (gaze == 2) player.displayClientMessage(Component.translatable("message.dbzenith.moon_gaze"), true);
        }
        if (gaze >= GAZE_SECONDS) {
            data.setMoonGaze(0);
            FormHandler.enter(player, data, Forms.GREAT_APE);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.RAVAGER_ROAR,   // the roar
                    net.minecraft.sounds.SoundSource.PLAYERS, 3f, 0.45f);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.ENDER_DRAGON_GROWL,
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.5f, 0.6f);
            player.displayClientMessage(Component.translatable("message.dbzenith.great_ape"), true);
        }
    }

    /** Is the player's look on the moon (the full moon in the sky, or a risen false moon nearby)? */
    public static boolean lookingAtMoon(Player player) {
        net.minecraft.world.phys.Vec3 look = player.getViewVector(1f);
        if (isFullMoonNight(player.level()) && look.dot(moonDirection(player.level(), 1f)) > GAZE_CONE) return true;
        net.minecraft.world.phys.Vec3 eye = player.getEyePosition();
        for (FalseMoonEntity m : player.level().getEntitiesOfClass(FalseMoonEntity.class,
                player.getBoundingBox().inflate(FALSE_MOON_RANGE, 160, FALSE_MOON_RANGE), FalseMoonEntity::isRisen)) {
            if (look.dot(m.position().subtract(eye).normalize()) > GAZE_CONE) return true;
        }
        return false;
    }

    /**
     * Where the moon is in the sky, as a unit vector: the sky turns about the east-west axis, the sun rising in the east
     * (+x), the moon opposite it.
     */
    public static net.minecraft.world.phys.Vec3 moonDirection(Level level, float partial) {
        double a = level.getTimeOfDay(partial) * Math.PI * 2;
        return new net.minecraft.world.phys.Vec3(Math.sin(a), -Math.cos(a), 0);
    }

    public static boolean seesMoon(Player player) {
        Level level = player.level();
        // open sky over the head: nothing solid above it (the heightmap; sky light high above a giant can be unlit)
        int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, player.getBlockX(), player.getBlockZ());
        boolean sky = player.getY() + player.getBbHeight() >= top - 0.5 || level.canSeeSky(player.blockPosition().above((int) Math.ceil(player.getBbHeight())));
        if (!sky) return false;
        return isFullMoonNight(level) || nearFalseMoon(player);
    }

    /** Time of day, not Level.isNight(): a thunderstorm darkens the sky enough for isNight() to report true. */
    public static boolean isFullMoonNight(Level level) {
        long time = level.getDayTime() % 24000L;
        boolean night = time >= 13000L && time <= 23000L;
        return level.dimensionType().hasSkyLight() && !level.dimensionType().hasFixedTime() && night && level.getMoonPhase() == 0;
    }

    public static boolean nearFalseMoon(Player player) {
        return !player.level().getEntitiesOfClass(FalseMoonEntity.class,
                player.getBoundingBox().inflate(FALSE_MOON_RANGE, 160, FALSE_MOON_RANGE), FalseMoonEntity::isRisen).isEmpty();
    }

    /** Size of a player's current form: from capability data on the server, from public state on clients. */
    /** The forms drawn with the Great Ape model (the moon ape, the golden ape, the legendary ape). */
    public static boolean isApeForm(String form) {
        return "great_ape".equals(form) || "golden_ape".equals(form) || "legendary_great_ape".equals(form);
    }

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
