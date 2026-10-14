package com.dbzenith.client;

import com.dbzenith.DBZenith;
import com.dbzenith.client.fx.ImpactFx;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.ki.FlightModel;
import com.dbzenith.network.PublicStatePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * Ki flight with weight (CX-38). Your own flight runs through {@link FlightModel} instead of vanilla's instant creative
 * flight: you fly where you look, speed builds and carries, you glide to a stop, reversing brakes, sprint boosts (a
 * burst, then building speed). Vanilla still moves and collides the player; it is only kept from adding its own push
 * (its flight speed is zeroed for the tick) and handed our velocity at the end of each tick.
 * <p>
 * The feel: wind that rises with speed, the view widening, a ring of air on a boost, wind streaks at speed, and dust or
 * spray thrown up when skimming low over ground or water (for every flier you can see).
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, value = Dist.CLIENT)
public final class FlightPhysics {
    private static final FlightModel.State STATE = new FlightModel.State();
    private static boolean active;
    private static float savedSpeed;
    private static double x0, y0, z0;
    private static float fov, fovO;
    private static Wind wind;
    private static final Map<Integer, Double> LAST_SPEED = new HashMap<>();
    /** Each flier's lagging body yaw and its spring speed. */
    private static final Map<Integer, float[]> BODY = new HashMap<>();
    private static float camBack, camBackO, camRoll, camRollO, lastYaw;
    private static final java.lang.reflect.Method MOVE = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findMethod(
            net.minecraft.client.Camera.class, "m_90568_", double.class, double.class, double.class);

    private FlightPhysics() {}

    /** Your flight speed now (blocks per tick) and the cruise speed it is measured against. */
    public static double speed() {
        return active ? STATE.speed() : 0;
    }

    private static boolean kiFlying(LocalPlayer p) {
        return p.getAbilities().flying && ClientPlayerData.hasData() && ClientPlayerData.get().isFlying()
                && DBZConfig.CLIENT.flightMomentum.get() && !p.isSpectator() && !p.isPassenger();
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (!(event.player instanceof LocalPlayer p) || event.side.isServer()) return;
        if (event.phase == TickEvent.Phase.START) {
            boolean on = kiFlying(p);
            if (on && !active) {                                                 // taking off: carry what we have
                Vec3 v = p.getDeltaMovement();
                STATE.vx = v.x;
                STATE.vy = v.y;
                STATE.vz = v.z;
                STATE.boostTicks = 0;
            }
            active = on;
            if (!active) return;
            savedSpeed = p.getAbilities().getFlyingSpeed();
            p.getAbilities().setFlyingSpeed(0);                                  // vanilla adds no push of its own this tick
            x0 = p.getX();
            y0 = p.getY();
            z0 = p.getZ();
            return;
        }
        if (!active) return;
        p.getAbilities().setFlyingSpeed(savedSpeed);
        if (p.horizontalCollision) {                                             // a wall takes the speed into it
            STATE.vx = p.getX() - x0;
            STATE.vz = p.getZ() - z0;
        }
        if (p.verticalCollision) STATE.vy = p.getY() - y0;
        Minecraft mc = Minecraft.getInstance();
        boolean boost = mc.options.keySprint.isDown();
        double up = (p.input.jumping ? 1 : 0) - (p.input.shiftKeyDown ? 1 : 0);
        float steerPitch = DBZConfig.CLIENT.flightLookSteering.get() ? p.getXRot() : 0;   // fly where you look, or keep level
        boolean burst = FlightModel.step(STATE, p.input.forwardImpulse, p.input.leftImpulse, up, boost, p.getYRot(), steerPitch, savedSpeed);
        p.setDeltaMovement(STATE.vx, STATE.vy, STATE.vz);
        if (burst) {
            ImpactFx.boostBurst(p, p.getLookAngle(), auraColor(p));
            p.level().playLocalSound(p.getX(), p.getY(), p.getZ(), com.dbzenith.registry.ModSounds.DASH.get(), SoundSource.PLAYERS, 0.8f, 0.9f, false);
            LAST_SPEED.put(p.getId(), 99.0);                                     // no second burst from the speed check
        }
    }

    private static int auraColor(AbstractClientPlayer p) {
        PublicStatePacket s = ClientPublicStates.get(p.getId());
        return s == null ? 0xFFFFFF : s.auraColor();
    }

    // ------------------------------------------------------------------ the feel

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        fovO = fov;
        if (mc.level == null || mc.player == null || mc.isPaused()) {
            if (mc.level == null) LAST_SPEED.clear();
            return;
        }
        LocalPlayer me = mc.player;
        double mySpeed = active ? STATE.speed() : 0, cruise = Math.max(.05, me.getAbilities().getFlyingSpeed() * FlightModel.CRUISE);
        float want = active ? (float) Mth.clamp((mySpeed - cruise * .6) / (cruise * 1.8), 0, 1) : 0;
        fov += (want - fov) * .12f;
        if (active && (wind == null || wind.isStopped())) {                     // the wind
            wind = new Wind(me);
            mc.getSoundManager().play(wind);
        }
        // the cinematic camera: easing back as speed builds (and in a little when braking), rolling into turns
        camBackO = camBack;
        camRollO = camRoll;
        float turn = active ? Mth.wrapDegrees(me.getYRot() - lastYaw) : 0;
        lastYaw = me.getYRot();
        float backWant = active ? (float) Mth.clamp((mySpeed - cruise * .4) / (cruise * 2.2), 0, 1) * 1.6f : 0;
        camBack += (backWant - camBack) * .06f;                                  // slow: the camera trails the speed
        float rollWant = active ? Mth.clamp(turn * .22f, -4.5f, 4.5f) * (float) Mth.clamp(mySpeed / cruise, .3, 1) : 0;
        camRoll += (rollWant - camRoll) * .12f;
        for (AbstractClientPlayer p : mc.level.players()) {
            PublicStatePacket s = ClientPublicStates.get(p.getId());
            boolean flying = s != null && s.has(PublicStatePacket.FLYING) && !p.onGround();
            if (!flying || p.distanceToSqr(me) > 64 * 64) {
                LAST_SPEED.remove(p.getId());
                BODY.remove(p.getId());
                continue;
            }
            body(p);
            Vec3 v = p == me ? new Vec3(STATE.vx, STATE.vy, STATE.vz) : new Vec3(p.getX() - p.xo, p.getY() - p.yo, p.getZ() - p.zo);
            double speed = v.length();
            Double last = LAST_SPEED.put(p.getId(), speed);
            if (p != me && last != null && speed - last > .45 && speed > .9) ImpactFx.boostBurst(p, v, auraColor(p));   // someone else boosts
            if (!DBZConfig.CLIENT.flightEffects.get()) continue;
            streaks(mc, p, v, speed, p == me);
            skim(mc, p, v, speed);
        }
    }

    /**
     * A flier's body turns after where they look on a soft spring, a moment behind the camera, instead of snapping
     * round with it (the head turns first, the body follows). Set after the entity ticked, so it overrides vanilla.
     */
    private static void body(AbstractClientPlayer p) {
        if (!DBZConfig.CLIENT.flightBodyLag.get()) return;
        float[] b = BODY.computeIfAbsent(p.getId(), k -> new float[]{p.getYRot(), 0});
        float prev = b[0];
        float diff = Mth.wrapDegrees(p.getYRot() - b[0]);
        if (Math.abs(diff) > 120) {                                              // a snap turn: catch up at once
            b[0] = p.getYRot() - Math.signum(diff) * 120;
            diff = Mth.wrapDegrees(p.getYRot() - b[0]);
        }
        b[1] += diff * .16f - b[1] * .55f;                                        // a spring: about 0.3 s behind, barely any overshoot
        b[0] += b[1];
        p.yBodyRotO = prev;
        p.yBodyRot = b[0];
    }

    /** Applies the camera's speed pull-back and turn roll, before the shoulder camera's own shift. */
    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (!DBZConfig.CLIENT.flightCinematicCamera.get()) return;
        float pt = (float) event.getPartialTick();
        float roll = Mth.lerp(pt, camRollO, camRoll), back = Mth.lerp(pt, camBackO, camBack);
        if (Math.abs(roll) > .01f) event.setRoll(event.getRoll() + roll);
        net.minecraft.client.Camera cam = event.getCamera();
        Minecraft mc = Minecraft.getInstance();
        if (back < .01f || !cam.isDetached() || mc.level == null || mc.options.getCameraType().isMirrored()) return;
        Vec3 from = cam.getPosition(), look = new Vec3(cam.getLookVector());
        Vec3 to = from.subtract(look.scale(back));
        var hit = mc.level.clip(new net.minecraft.world.level.ClipContext(from, to, net.minecraft.world.level.ClipContext.Block.VISUAL,
                net.minecraft.world.level.ClipContext.Fluid.NONE, cam.getEntity()));
        double room = hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? back : Math.max(0, hit.getLocation().distanceTo(from) - .25);
        try {
            MOVE.invoke(cam, -room, 0.0, 0.0);                                    // move(forward, up, left)
        } catch (ReflectiveOperationException ignored) {
        }
    }

    /** Wind streaking past a fast flier. */
    private static void streaks(Minecraft mc, AbstractClientPlayer p, Vec3 v, double speed, boolean me) {
        if (speed < .9) return;
        var rnd = p.getRandom();
        int n = speed > 1.6 ? 2 : 1;
        if (me && mc.options.getCameraType().isFirstPerson()) n = rnd.nextInt(3) == 0 ? 1 : 0;   // a few, from your own eyes
        Vec3 d = v.normalize();
        for (int i = 0; i < n; i++) {
            Vec3 at = p.position().add(d.scale(2 + rnd.nextDouble() * 3)).add((rnd.nextDouble() - .5) * 3, rnd.nextDouble() * 2.2, (rnd.nextDouble() - .5) * 3);
            Vec3 vel = v.scale(-.25);
            mc.level.addParticle(ParticleTypes.CLOUD, at.x, at.y, at.z, vel.x, vel.y, vel.z);
        }
    }

    /** Dust kicked up off the ground, or spray off water, when a flier skims low and fast. */
    private static void skim(Minecraft mc, AbstractClientPlayer p, Vec3 v, double speed) {
        double h = Math.sqrt(v.x * v.x + v.z * v.z);
        if (h < .55) return;
        BlockPos below = null;
        for (int dy = 0; dy <= 4; dy++) {
            BlockPos b = BlockPos.containing(p.getX(), p.getY() - dy - .2, p.getZ());
            if (!mc.level.getBlockState(b).isAir() || !mc.level.getFluidState(b).isEmpty()) { below = b; break; }
        }
        if (below == null) return;
        float close = 1 - (float) (p.getY() - below.getY() - 1) / 5f;
        var rnd = p.getRandom();
        int n = (int) Math.ceil(close * Math.min(4, h * 3));
        boolean water = !mc.level.getFluidState(below).isEmpty();
        if (water) {                                                              // the water parts under you (CX-48)
            Vec3 surf = com.dbzenith.client.fx.WaterFx.surfaceBelow(mc.level, p.position(), 5);
            if (surf != null) com.dbzenith.client.fx.WaterFx.part(surf, v, (float) Math.min(1, h * 0.8) * close);
        }
        BlockState block = mc.level.getBlockState(below);
        for (int i = 0; i < n; i++) {
            double ox = (rnd.nextDouble() - .5) * 1.6, oz = (rnd.nextDouble() - .5) * 1.6;
            double x = p.getX() - v.x * 1.5 + ox, y = below.getY() + 1.05, z = p.getZ() - v.z * 1.5 + oz;
            double sx = -v.x * .15 + ox * .2, sz = -v.z * .15 + oz * .2, sy = .12 + rnd.nextDouble() * .18;
            if (water) {
                mc.level.addParticle(ParticleTypes.SPLASH, x, y, z, sx, sy, sz);
                if (rnd.nextInt(3) == 0) mc.level.addParticle(ParticleTypes.CLOUD, x, y, z, sx * .5, .05, sz * .5);
            } else if (!block.isAir()) {
                mc.level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, block), x, y, z, sx, sy, sz);
            }
        }
    }

    /** The view widens with speed. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (!DBZConfig.CLIENT.fovEffects.get()) return;
        float f = Mth.lerp((float) event.getPartialTick(), fovO, fov);
        if (f > .001f) event.setFOV(event.getFOV() * (1 + .16f * f * f * (3 - 2 * f)));
    }

    /** The wind in your ears, louder and higher the faster you fly. */
    private static final class Wind extends AbstractTickableSoundInstance {
        private final LocalPlayer player;

        Wind(LocalPlayer player) {
            super(com.dbzenith.registry.ModSounds.WIND_LOOP.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.player = player;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01f;
            this.relative = true;
        }

        @Override
        public void tick() {
            if (player.isRemoved() || !active) {
                stop();
                return;
            }
            double cruise = Math.max(.05, player.getAbilities().getFlyingSpeed() * FlightModel.CRUISE);
            float f = (float) Mth.clamp((STATE.speed() - cruise * .25) / (cruise * 2.2), 0, 1);
            volume = Math.max(.01f, f * f * .85f);
            pitch = .6f + .6f * f;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}
