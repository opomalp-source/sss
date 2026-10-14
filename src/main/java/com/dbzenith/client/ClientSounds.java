package com.dbzenith.client;

import com.dbzenith.DBZenith;
import com.dbzenith.network.ImpactPacket;
import com.dbzenith.network.PublicStatePacket;
import com.dbzenith.registry.ModSounds;
import com.dbzenith.transform.Form;
import com.dbzenith.transform.Forms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Sounds the client makes by itself: every impact (from the impact packets everyone already gets), swings, hard
 * landings, UI clicks, and the aura loops that follow each fighter (a roar while powering up, a hum while a form is
 * held, a pure tone for calm god ki).
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, value = Dist.CLIENT)
public final class ClientSounds {
    private ClientSounds() {}

    static void at(ClientLevel level, Vec3 pos, SoundEvent sound, float volume, float pitch) {
        level.playLocalSound(pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, volume, pitch * (0.92f + level.random.nextFloat() * 0.16f), false);
    }

    /** The sound of an impact, sized by its scale. */
    public static void impact(ClientLevel level, Vec3 pos, int kind, float scale) {
        impact(level, pos, kind, scale, 0);
    }

    /**
     * The sound of an impact, in layers (CX-19e): a light blow is a slap with a whiff of air; a heavy one adds a sub boom;
     * a guarded one is a block over a muffled thud; a perfect guard rings over a sharp block; a critical adds a crack and
     * a counter its sting.
     */
    public static void impact(ClientLevel level, Vec3 pos, int kind, float scale, int flags) {
        float big = Mth.clamp(scale, 0.5f, 3f);
        if ((flags & ImpactPacket.CRIT) != 0) at(level, pos, ModSounds.HIT_CRIT.get(), 0.75f, 1f);
        if ((flags & ImpactPacket.COUNTER) != 0) at(level, pos, ModSounds.COUNTER_HIT.get(), 0.9f, 1f);
        switch (kind) {
            case ImpactPacket.PUNCH -> {
                at(level, pos, ModSounds.PUNCH_LIGHT.get(), 0.8f, 1f);
                at(level, pos, ModSounds.WHOOSH.get(), 0.18f, 1.6f);
            }
            case ImpactPacket.HEAVY, ImpactPacket.SPIKE -> {
                at(level, pos, ModSounds.PUNCH_HEAVY.get(), 1f, 1f);
                at(level, pos, ModSounds.IMPACT_BOOM.get(), 0.7f, kind == ImpactPacket.SPIKE ? 0.85f : 1f);
            }
            case ImpactPacket.GUARD -> {
                at(level, pos, ModSounds.GUARD_BLOCK.get(), 0.8f, 1f);
                at(level, pos, ModSounds.HIT_GUARDED.get(), 0.7f, 1f);
            }
            case ImpactPacket.PARRY -> {
                at(level, pos, ModSounds.PARRY.get(), 0.9f, 1f);
                at(level, pos, ModSounds.GUARD_BLOCK.get(), 0.6f, 1.3f);
            }
            case ImpactPacket.GUARD_BREAK -> {
                at(level, pos, ModSounds.GUARD_BREAK.get(), 1f, 1f);
                at(level, pos, ModSounds.IMPACT_BOOM.get(), 0.5f, 1.2f);
            }
            case ImpactPacket.DEFLECT -> at(level, pos, ModSounds.DEFLECT.get(), 0.9f, 1f);
            case ImpactPacket.KI_HIT -> at(level, pos, ModSounds.KI_HIT.get(), 0.5f + 0.25f * big, 1.15f - 0.1f * big);
            case ImpactPacket.EXPLOSION -> {                                  // in layers (the ki attack rework): the boom, then debris, then far off an echo
                at(level, pos, big > 1.6f ? ModSounds.EXPLOSION_BIG.get() : ModSounds.EXPLOSION.get(), 0.7f + 0.3f * big, 1.1f - 0.1f * big);
                later(level, pos, 6, ModSounds.IMPACT_DEBRIS.get(), 0.35f + 0.25f * big, 1f);
                Minecraft mc = Minecraft.getInstance();
                double far = mc.gameRenderer.getMainCamera().getPosition().distanceTo(pos);
                if (scale >= 1.2f) later(level, pos, 10 + (int) (far / 17), ModSounds.IMPACT_ECHO.get(), Math.min(1.6f, 0.4f + 0.4f * big), 0.9f);
            }
            default -> { }
        }
    }


    // ------------------------------------------------------------------ the ki attack rework: sounds that come later, and ki in flight

    private record Later(long at, Vec3 pos, SoundEvent sound, float volume, float pitch) {}

    private static final java.util.List<Later> LATER = new java.util.ArrayList<>();

    /** A sound some ticks from now (debris coming down, an echo coming back). */
    static void later(ClientLevel level, Vec3 pos, int ticks, SoundEvent sound, float volume, float pitch) {
        if (LATER.size() < 64) LATER.add(new Later(level.getGameTime() + ticks, pos, sound, volume, pitch));
    }

    @SubscribeEvent
    public static void onLaterTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || LATER.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            LATER.clear();
            return;
        }
        long now = mc.level.getGameTime();
        LATER.removeIf(l -> {
            if (now < l.at) return false;
            at(mc.level, l.pos, l.sound, l.volume, l.pitch);
            return true;
        });
    }

    /** A ki ball in flight: its hum and rush follow it, rising in pitch as it comes at you and falling as it goes. */
    public static void travel(com.dbzenith.skill.KiBlastEntity blast) {
        Minecraft.getInstance().getSoundManager().play(new Travel(blast));
    }

    static final class Travel extends AbstractTickableSoundInstance {
        final com.dbzenith.skill.KiBlastEntity blast;
        double lastDist = -1;
        float doppler = 1;

        Travel(com.dbzenith.skill.KiBlastEntity blast) {
            super(ModSounds.KI_TRAVEL.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.blast = blast;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01f;
            this.x = blast.getX(); this.y = blast.getY(); this.z = blast.getZ();
        }

        @Override
        public void tick() {
            if (blast.isRemoved()) { stop(); return; }
            x = blast.getX(); y = blast.getY() + blast.getBbHeight() / 2; z = blast.getZ();
            Vec3 ear = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            double dist = ear.distanceTo(new Vec3(x, y, z));
            double closing = lastDist < 0 ? 0 : lastDist - dist;                   // blocks a tick toward you
            lastDist = dist;
            doppler += ((float) Mth.clamp(1 + closing * 0.18, 0.7, 1.45) - doppler) * 0.5f;
            double speed = blast.getDeltaMovement().length();
            float size = Math.min(2.5f, blast.getSize());
            volume = (float) Math.max(0.01, Math.min(1, speed * 1.2) * (0.35 + 0.45 * size));   // a bigger ball carries further
            pitch = (1.25f - 0.2f * size) * doppler;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }

    /** A bare-handed swing cuts the air. */
    public static void swing(AbstractClientPlayer player) {
        if (player.level() instanceof ClientLevel level) at(level, player.position().add(0, 1.2, 0), ModSounds.WHOOSH.get(), 0.45f, 1.1f);
    }

    /** A hard landing thuds. */
    public static void land(AbstractClientPlayer player, float hardness) {
        if (player.level() instanceof ClientLevel level) at(level, player.position(), ModSounds.LAND.get(), 0.4f + 0.5f * hardness, 1f);
    }

    public static void uiClick() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.UI_CLICK.get(), 1f, 0.6f));
    }

    public static void uiOpen() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.UI_OPEN.get(), 1f, 0.5f));
    }

    // ------------------------------------------------------------------ aura loops

    private enum Loop { NONE, CHARGE, HUM, CALM }

    private static final Map<AbstractClientPlayer, AuraLoop> LOOPS = new WeakHashMap<>();

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            LOOPS.clear();
            return;
        }
        for (AbstractClientPlayer p : mc.level.players()) {
            Loop want = want(p);
            AuraLoop now = LOOPS.get(p);
            if (now != null && (now.isStopped() || now.kind != want)) {
                now.fadeOut();
                LOOPS.remove(p);
                now = null;
            }
            if (now == null && want != Loop.NONE) {
                AuraLoop loop = new AuraLoop(p, want);
                LOOPS.put(p, loop);
                mc.getSoundManager().play(loop);
            }
        }
    }

    static Loop want(AbstractClientPlayer p) {
        if (!p.isAlive() || p.isSpectator() || p.isInvisible()) return Loop.NONE;
        PublicStatePacket s = ClientPublicStates.get(p.getId());
        if (s == null) return Loop.NONE;
        if (s.powering()) return Loop.CHARGE;
        Form f = Forms.byId(s.form());
        if (f == Forms.GREAT_APE) return Loop.NONE;
        boolean held = !f.isBase() || s.has(PublicStatePacket.KAIOKEN);
        if (!held) return Loop.NONE;
        return f.calmAura() && !s.has(PublicStatePacket.KAIOKEN) ? Loop.CALM : Loop.HUM;
    }

    // ------------------------------------------------------------------ the roar of a held beam (CX-41)

    /** A beam has appeared: its roar takes over from the firing sound and lasts as long as the beam does. */
    public static void beam(com.dbzenith.skill.KiBeamEntity beam) {
        Minecraft.getInstance().getSoundManager().play(new BeamLoop(beam));
    }

    static final class BeamLoop extends AbstractTickableSoundInstance {
        final com.dbzenith.skill.KiBeamEntity beam;
        int age;
        float level;

        BeamLoop(com.dbzenith.skill.KiBeamEntity beam) {
            super(ModSounds.BEAM_LOOP.get(), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.beam = beam;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01f;
            this.x = beam.getX(); this.y = beam.getY(); this.z = beam.getZ();
        }

        @Override
        public void tick() {
            age++;
            boolean alive = !beam.isRemoved();
            float width = Math.max(0.3f, beam.getWidth());
            float want = alive && age > 8 ? Math.min(1f, 0.35f + width * 0.25f) : 0f;   // after the firing burst, by the beam's size
            level += (want - level) * (alive ? 0.12f : 0.3f);
            if (!alive && level < 0.02f) { stop(); return; }
            volume = Math.max(0.01f, level * 0.7f);
            pitch = 1.15f - Math.min(0.35f, width * 0.08f);                           // bigger beams roar lower
            if (alive) {                                                                 // heard from its middle
                net.minecraft.world.phys.Vec3 mid = beam.position().add(beam.direction().scale(Math.min(beam.getLength(), 16) * 0.3));
                x = mid.x; y = mid.y; z = mid.z;
            }
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }

    /** A loop that follows its fighter and fades in and out instead of cutting. */
    static final class AuraLoop extends AbstractTickableSoundInstance {
        final AbstractClientPlayer player;
        final Loop kind;
        final float target;
        boolean fading;

        AuraLoop(AbstractClientPlayer player, Loop kind) {
            super(switch (kind) {
                case CHARGE -> ModSounds.AURA_CHARGE.get();
                case CALM -> ModSounds.AURA_CALM.get();
                default -> ModSounds.AURA_HUM.get();
            }, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.player = player;
            this.kind = kind;
            this.looping = true;
            this.delay = 0;
            this.target = kind == Loop.CHARGE ? 0.55f : kind == Loop.CALM ? 0.25f : 0.3f;
            this.volume = 0.01f;
            this.pitch = kind == Loop.CHARGE ? 0.95f + player.getRandom().nextFloat() * 0.1f : 1f;
            follow();
        }

        void fadeOut() {
            fading = true;
        }

        void follow() {
            x = player.getX();
            y = player.getY() + 1;
            z = player.getZ();
        }

        @Override
        public void tick() {
            if (player.isRemoved()) {
                stop();
                return;
            }
            follow();
            if (fading) {
                volume -= 0.06f;
                if (volume <= 0.01f) stop();
            } else {
                volume = Math.min(target, volume + 0.08f);
            }
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }
    }
}
