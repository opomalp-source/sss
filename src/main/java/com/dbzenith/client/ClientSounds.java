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
        float big = Mth.clamp(scale, 0.5f, 3f);
        switch (kind) {
            case ImpactPacket.PUNCH -> at(level, pos, ModSounds.PUNCH_LIGHT.get(), 0.8f, 1f);
            case ImpactPacket.HEAVY, ImpactPacket.SPIKE -> at(level, pos, ModSounds.PUNCH_HEAVY.get(), 1f, 1f);
            case ImpactPacket.GUARD -> at(level, pos, ModSounds.GUARD_BLOCK.get(), 0.9f, 1f);
            case ImpactPacket.PARRY -> at(level, pos, ModSounds.PARRY.get(), 0.9f, 1f);
            case ImpactPacket.GUARD_BREAK -> at(level, pos, ModSounds.GUARD_BREAK.get(), 1f, 1f);
            case ImpactPacket.DEFLECT -> at(level, pos, ModSounds.DEFLECT.get(), 0.9f, 1f);
            case ImpactPacket.KI_HIT -> at(level, pos, ModSounds.KI_HIT.get(), 0.5f + 0.25f * big, 1.15f - 0.1f * big);
            case ImpactPacket.EXPLOSION -> at(level, pos, big > 1.6f ? ModSounds.EXPLOSION_BIG.get() : ModSounds.EXPLOSION.get(),
                    0.7f + 0.3f * big, 1.1f - 0.1f * big);
            default -> { }
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
        boolean held = !f.isBase() || s.overdrive() > 0 || s.has(PublicStatePacket.KAIOKEN);
        if (!held) return Loop.NONE;
        return f.calmAura() && !s.has(PublicStatePacket.KAIOKEN) ? Loop.CALM : Loop.HUM;
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
