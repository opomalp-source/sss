package com.dbzenith.client;

import com.dbzenith.DBZenith;
import com.dbzenith.client.anim.AnimController;
import com.dbzenith.client.anim.Anims;
import com.dbzenith.client.fx.Afterimages;
import com.dbzenith.client.fx.CameraFx;
import com.dbzenith.client.fx.ImpactFx;
import com.dbzenith.client.ui.CutInOverlay;
import com.dbzenith.client.ui.DbzTheme;
import com.dbzenith.data.PlayerData;
import com.dbzenith.fusion.Fusion;
import com.dbzenith.fusion.FusionDance;
import com.dbzenith.network.FusionPackets;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.network.PublicStatePacket;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The Fusion Dance and the Potara as this client sees them (12c): both dancers' bodies held facing the same way and
 * playing the dance (mirrored), the camera turned round to watch from the front, clicks turned into beats, the beat
 * rings on the HUD; the Potara pull with its streaks; then the fused warrior's entrance, and the fused name over them.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, value = Dist.CLIENT)
public final class ClientFusion {
    /** Live sessions, by the asker's entity id. */
    private static final Map<Integer, FusionPackets.Show> SESSIONS = new HashMap<>();
    private static CameraType savedCamera;
    private static long lastPress = Long.MIN_VALUE / 2;
    private static int pendingCutIn = -1;
    private static long cutInAt;

    private ClientFusion() {}

    public static void show(FusionPackets.Show s) {
        FusionPackets.Show old = SESSIONS.put(s.a(), s);
        if (old != null) return;                                             // a re-send with the beats judged so far
        int lock = s.kind() == FusionDance.DANCE ? FusionDance.LENGTH + 4 : FusionDance.POTARA_LENGTH + 4;
        if (s.kind() == FusionDance.DANCE) {
            AnimController.playLocked(s.a(), Anims.FUSION_DANCE_A, lock);
            AnimController.playLocked(s.b(), Anims.FUSION_DANCE_B, lock);
        } else {
            AnimController.playLocked(s.a(), Anims.POTARA, lock);
            AnimController.playLocked(s.b(), Anims.POTARA, lock);
        }
        Minecraft mc = Minecraft.getInstance();
        if (s.kind() == FusionDance.DANCE && mine(s)) startCamera();
    }

    public static void end(int a, int b) {
        FusionPackets.Show s = SESSIONS.remove(a);
        if (s == null) return;
        Minecraft mc = Minecraft.getInstance();
        long t = mc.level == null ? 0 : mc.level.getGameTime() - s.start();
        int length = s.kind() == FusionDance.DANCE ? FusionDance.LENGTH : FusionDance.POTARA_PULL;
        if (t < length - 2) {                                               // broken off: let go of the pose
            AnimController.stop(a);
            AnimController.stop(b);
        }
        if (mine(s)) stopCamera();
    }

    // ------------------------------------------------------------------ the dance camera

    /** A stand-in the view rides on during the dance: in front of the pair, looking back at both of them. */
    private static Entity camera;

    /** A fusion cinematic owns the view (no crosshair then). */
    public static boolean cinematic() {
        return camera != null;
    }
    private static boolean devCamera;

    private static void startCamera() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || camera != null) return;
        camera = net.minecraft.world.entity.EntityType.MARKER.create(mc.level);
        if (camera == null) return;
        if (savedCamera == null) savedCamera = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        if (mc.player != null) camera.moveTo(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ(), mc.player.getYRot() + 180, 6);
        mc.setCameraEntity(camera);
    }

    private static void stopCamera() {
        Minecraft mc = Minecraft.getInstance();
        if (camera != null && mc.player != null) mc.setCameraEntity(mc.player);
        camera = null;
        if (savedCamera != null) mc.options.setCameraType(savedCamera);
        savedCamera = null;
    }

    /** Frames the two dancers from the front: centred between them, far enough back to see the first steps. */
    private static void aim(net.minecraft.world.phys.Vec3 a, net.minecraft.world.phys.Vec3 b, float yaw) {
        double r = Math.toRadians(yaw);
        net.minecraft.world.phys.Vec3 mid = a.add(b).scale(0.5);
        double x = mid.x - Math.sin(r) * 5.0, z = mid.z + Math.cos(r) * 5.0, y = mid.y + 1.6;
        camera.xo = camera.getX();
        camera.yo = camera.getY();
        camera.zo = camera.getZ();
        camera.setPos(x, y, z);
        camera.setYRot(yaw + 180);
        camera.yRotO = yaw + 180;
        camera.setXRot(7);
        camera.xRotO = 7;
    }

    /** No first-person hand or crosshair while the view rides the dance camera. */
    @SubscribeEvent
    public static void onHand(net.minecraftforge.client.event.RenderHandEvent event) {
        if (camera != null) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onOverlay(net.minecraftforge.client.event.RenderGuiOverlayEvent.Pre event) {
        if (camera != null && event.getOverlay().id().equals(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.CROSSHAIR.id())) event.setCanceled(true);
    }

    /** Dev automation: the dance camera on the local player and the stand-in partner. */
    public static void devCamera(boolean on) {
        devCamera = on;
        if (on) startCamera();
        else if (myDance() == null) stopCamera();
    }

    private static boolean mine(FusionPackets.Show s) {
        Player me = Minecraft.getInstance().player;
        return me != null && (me.getId() == s.a() || me.getId() == s.b());
    }

    /** The dance the local player is in, or null. */
    public static FusionPackets.Show myDance() {
        for (FusionPackets.Show s : SESSIONS.values()) if (s.kind() == FusionDance.DANCE && mine(s)) return s;
        return null;
    }

    /** Wearing a Potara earring: a Potara fusion, or the moment before one. */
    public static boolean earrings(AbstractClientPlayer p) {
        PublicStatePacket state = ClientPublicStates.get(p.getId());
        if (state != null && state.has(PublicStatePacket.FUSED_POTARA)) return true;
        for (FusionPackets.Show s : SESSIONS.values()) if (s.kind() == FusionDance.POTARA && (s.a() == p.getId() || s.b() == p.getId())) return true;
        return false;
    }

    /** The fused warrior appears: the entrance (or the sorry sight of a botched one), the light, and the name. */
    public static void fused(AbstractClientPlayer host, int kind) {
        var anim = switch (kind) {
            case Fusion.FAILED_FAT -> Anims.FUSED_FAT;
            case Fusion.FAILED_THIN -> Anims.FUSED_THIN;
            default -> Anims.FUSED_ENTRANCE;
        };
        AnimController.playLocked(host.getId(), anim, 30);
        int glow = kind == Fusion.POTARA ? 0xBFF2FF : 0xFFE89A;
        ImpactFx.transformBurst(host, glow);
        ImpactFx.transformBurst(host, 0xFFFFFF);
        Minecraft mc = Minecraft.getInstance();
        if (host.distanceToSqr(mc.gameRenderer.getMainCamera().getPosition()) < 48 * 48) CameraFx.flash(0xFFFFFF, Fusion.failed(kind) ? 0.5f : 0.85f);
        if (host == mc.player || mc.getCameraEntity() == host) {
            pendingCutIn = host.getId();                                    // once the fused name has arrived
            cutInAt = host.level().getGameTime() + 3;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            SESSIONS.clear();
            savedCamera = null;
            camera = null;
            devCamera = false;
            return;
        }
        long now = mc.level.getGameTime();
        if (pendingCutIn >= 0 && now >= cutInAt) {
            PublicStatePacket state = ClientPublicStates.get(pendingCutIn);
            if (state != null && !state.fusedName().isEmpty()) {
                boolean botched = state.has(PublicStatePacket.FUSED_FAT) || state.has(PublicStatePacket.FUSED_THIN);
                CutInOverlay.play(Component.literal(state.fusedName()), state.has(PublicStatePacket.FUSED_POTARA) ? 0xBFF2FF : 0xFFC850,
                        botched ? "cutin.dbzenith.fusion_botched" : "cutin.dbzenith.fusion");
                pendingCutIn = -1;
            } else if (now > cutInAt + 20) pendingCutIn = -1;
        }
        for (Iterator<FusionPackets.Show> it = SESSIONS.values().iterator(); it.hasNext(); ) {
            FusionPackets.Show s = it.next();
            long t = now - s.start();
            if (t > FusionDance.LENGTH + 60) {                              // the server's End went missing
                it.remove();
                continue;
            }
            face(mc.level.getEntity(s.a()), s.yaw());
            face(mc.level.getEntity(s.b()), s.kind() == FusionDance.DANCE ? s.yaw() : s.yaw() + 180);
            if (s.kind() == FusionDance.POTARA && t >= FusionDance.POTARA_PULL) {
                if (mc.level.getEntity(s.a()) instanceof Player pa) Afterimages.keepAlive(pa, 3);
                if (mc.level.getEntity(s.b()) instanceof Player pb) Afterimages.keepAlive(pb, 3);
            }
        }
        FusionPackets.Show mine = myDance();
        if (mine != null && camera != null) {
            Entity ea = mc.level.getEntity(mine.a()), eb = mc.level.getEntity(mine.b());
            if (ea != null && eb != null) aim(ea.position(), eb.position(), mine.yaw());
        } else if (devCamera && DevDuet.partner() != null && mc.player != null) {
            float yaw = DevDuet.faceToFace() ? mc.player.getYRot() - 90 : mc.player.getYRot();   // the Potara from the side
            aim(mc.player.position(), DevDuet.partner().position(), yaw);
        } else if (camera != null) stopCamera();
    }

    /** Holds a dancer's body (and, for the local player, the view) facing the dance's way. */
    private static void face(Entity e, float yaw) {
        if (!(e instanceof Player p)) return;
        p.setYBodyRot(yaw);
        p.yBodyRotO = yaw;
        p.setYHeadRot(yaw);
        p.yHeadRotO = yaw;
        if (p == Minecraft.getInstance().player) {
            p.setYRot(yaw);
            p.yRotO = yaw;
            p.setXRot(0);
            p.xRotO = 0;
        }
    }

    /** Dancers do not walk off mid-dance. */
    @SubscribeEvent
    public static void onMovement(MovementInputUpdateEvent event) {
        if (myDance() == null && !inPotara()) return;
        event.getInput().forwardImpulse = 0;
        event.getInput().leftImpulse = 0;
        event.getInput().jumping = false;
        event.getInput().shiftKeyDown = false;
        event.getInput().up = event.getInput().down = event.getInput().left = event.getInput().right = false;
    }

    private static boolean inPotara() {
        for (FusionPackets.Show s : SESSIONS.values()) if (s.kind() == FusionDance.POTARA && mine(s)) return true;
        return false;
    }

    /** A click on the beat. */
    @SubscribeEvent
    public static void onClick(InputEvent.InteractionKeyMappingTriggered event) {
        if (myDance() == null) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        lastPress = mc.level.getGameTime();
        ModNetwork.sendToServer(new FusionPackets.Press());
    }

    /** Widen the view while the dance is on, so both dancers fit. */
    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (camera != null) event.setFOV(event.getFOV() + 4);
    }

    /** The fused name over the fused warrior on this client, too. */
    @SubscribeEvent
    public static void onName(PlayerEvent.NameFormat event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer p)) return;
        PublicStatePacket state = ClientPublicStates.get(p.getId());
        if (state != null && !state.fusedName().isEmpty()) event.setDisplayname(Component.literal(state.fusedName()));
    }

    // ------------------------------------------------------------------ the beat HUD

    private static final String[] SYLLABLES = {"hud.dbzenith.fusion_fu", "hud.dbzenith.fusion_sion", "hud.dbzenith.fusion_ha"};

    /** FU · SION · HA!: a ring closes on each syllable as its beat comes; a pip for each dancer shows how it went. */
    public static final class Overlay implements IGuiOverlay {
        @Override
        public void render(ForgeGui gui, GuiGraphics g, float partial, int width, int height) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null || mc.options.hideGui) return;
            FusionPackets.Show s = myDance();
            if (s == null) {
                fusedTimer(g, mc, width);
                return;
            }
            float t = mc.level.getGameTime() + partial - s.start();
            int me = mc.player.getId() == s.a() ? 0 : 1;
            float cy = height * 0.72f, gap = Math.min(110, width / 4.2f);
            float intro = Mth.clamp(t / 8f, 0, 1);
            Component hint = Component.translatable("hud.dbzenith.fusion_hint", mc.options.keyAttack.getTranslatedKeyMessage());
            DbzTheme.text(g, mc.font, hint, width / 2f - mc.font.width(hint) * 0.9f / 2, cy - 44, DbzTheme.withAlpha(0xF0F0F0, (int) (230 * intro)), 0.9f);
            for (int i = 0; i < 3; i++) {
                float cx = width / 2f + (i - 1) * gap;
                float until = FusionDance.BEATS[i] - t;
                int mine = (s.marks() >> ((me * 3 + i) * 2)) & 3, theirs = (s.marks() >> (((1 - me) * 3 + i) * 2)) & 3;
                boolean active = until > -FusionDance.LATE && until < FusionDance.LISTEN + 6;
                // the target ring, and the closing ring
                int base = mine == 1 ? 0x7CE07C : mine == 2 ? 0xFF6A5A : 0xFFD27A;
                DbzTheme.arc(g, cx, cy, 15, 17, 0, 360, DbzTheme.withAlpha(base, (int) (200 * intro)), DbzTheme.withAlpha(base, (int) (120 * intro)));
                if (active && mine == 0) {
                    float r = 16 + Math.max(0, until) * 2.2f;
                    float near = 1 - Mth.clamp(Math.abs(until) / FusionDance.LISTEN, 0, 1);
                    DbzTheme.arc(g, cx, cy, r - 1.5f, r + 1.5f, 0, 360, DbzTheme.withAlpha(0xFFFFFF, (int) (120 + 135 * near)), DbzTheme.withAlpha(0xFFE070, (int) (90 + 120 * near)));
                }
                // the syllable, popping as its beat lands
                float pop = 1.6f + (Math.abs(until) < 3 ? 0.5f * (1 - Math.abs(until) / 3f) : 0) + (mine == 1 && until < 0 && until > -6 ? 0.3f : 0);
                Component word = Component.translatable(SYLLABLES[i]);
                int col = mine == 1 ? 0xFF7CE07C : mine == 2 ? 0xFFFF6A5A : until < FusionDance.LISTEN ? 0xFFFFFFFF : 0xFFB0B4C4;
                DbzTheme.text(g, mc.font, word, cx - mc.font.width(word) * pop / 2, cy - 4 * pop, DbzTheme.withAlpha(col & 0xFFFFFF, (int) (255 * intro)), pop);
                // you and your partner
                pip(g, cx - 6, cy + 22, mine);
                pip(g, cx + 6, cy + 22, theirs);
            }
            long since = mc.level.getGameTime() - lastPress;
            if (since < 4) DbzTheme.arc(g, width / 2f, cy, 52, 54 + since * 6, 0, 360, DbzTheme.withAlpha(0xFFFFFF, (int) (120 - since * 30)), 0);
        }

        private static void pip(GuiGraphics g, float x, float y, int state) {
            int c = state == 1 ? 0xFF7CE07C : state == 2 ? 0xFFFF6A5A : 0xFF5A6070;
            g.fill((int) x - 3, (int) y - 3, (int) x + 3, (int) y + 3, 0xC0000000);
            g.fill((int) x - 2, (int) y - 2, (int) x + 2, (int) y + 2, c);
        }

        /** While fused: the fused name and the time left, small, under the top of the screen. */
        private static void fusedTimer(GuiGraphics g, Minecraft mc, int width) {
            if (!ClientPlayerData.hasData()) return;
            PlayerData d = ClientPlayerData.get();
            if (!d.isFused()) return;
            long left = Math.max(0, d.getFusionUntil() - mc.level.getGameTime()) / 20;
            Component line = Component.translatable(d.isFusionHost() ? "hud.dbzenith.fused" : "hud.dbzenith.fused_inside",
                    d.getFusedName(), String.format("%d:%02d", left / 60, left % 60));
            DbzTheme.text(g, mc.font, line, width / 2f - mc.font.width(line) / 2f, 26, DbzTheme.TITLE, 1f);
        }
    }
}
