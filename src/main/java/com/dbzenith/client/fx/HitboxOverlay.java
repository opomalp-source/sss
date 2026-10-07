package com.dbzenith.client.fx;

import com.dbzenith.DBZenith;
import com.dbzenith.client.Prediction;
import com.dbzenith.client.screen.MoveListScreen;
import com.dbzenith.client.ui.DbzTheme;
import com.dbzenith.combat.engine.Move;
import com.dbzenith.config.DBZConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The hitbox and frame-data overlay (CX-19 phase 9), a training aid: client config {@code hitboxOverlay}, the Combat
 * settings tab, or {@code /dbzhitbox}. Every fighter's move, from the moment the server starts it, shows its hitbox in the
 * world (yellow while it winds up, red while it can hit, blue while it recovers) and every living thing near shows its
 * hurtbox (pale blue). Your own move also gets a frame bar above the hotbar: one cell a tick, the phase colours, where you
 * are in it, and where a landed blow can be cancelled into the next.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, value = Dist.CLIENT)
public final class HitboxOverlay {
    private static final int STARTUP = 0xFFE8C83A, ACTIVE = 0xFFFF3A3A, RECOVERY = 0xFF4A8AFF, HURT = 0xFF8AD8FF;

    private record Running(Move move, float start) {}

    private static final Int2ObjectOpenHashMap<Running> RUNNING = new Int2ObjectOpenHashMap<>();
    private static Running mine;

    private HitboxOverlay() {}

    private static boolean on() {
        return DBZConfig.CLIENT.hitboxOverlay.get();
    }

    /** The server started a move for this entity (all moves, everyone's). */
    public static void started(int entityId, String moveId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Move m = Prediction.moves().stream().filter(x -> x.id.equals(moveId)).findFirst().orElse(null);
        if (m == null) return;
        Running r = new Running(m, mc.level.getGameTime());
        RUNNING.put(entityId, r);
        if (mc.player != null && entityId == mc.player.getId()) mine = r;
    }

    /** A hitstop froze this fighter: its move runs that much later. */
    public static void freeze(int entityId, int ticks) {
        Running r = RUNNING.get(entityId);
        if (r == null || ticks <= 0) return;
        Running later = new Running(r.move, r.start + ticks);
        RUNNING.put(entityId, later);
        if (mine == r) mine = later;
    }

    static int phaseColor(Move m, float tick) {
        if (tick < m.startup) return STARTUP;
        if (tick < m.startup + m.active) return ACTIVE;
        return RECOVERY;
    }

    // ------------------------------------------------------------------ in the world

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !on()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        float pt = event.getPartialTick(), now = mc.level.getGameTime() + pt;
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (Entity e : mc.level.entitiesForRendering()) {                     // hurtboxes
            if (!(e instanceof LivingEntity) || e instanceof ArmorStand || e.distanceToSqr(cam) > 24 * 24 || (e == mc.player && mc.options.getCameraType().isFirstPerson())) continue;
            AABB b = e.getBoundingBox().move(-cam.x, -cam.y, -cam.z);
            LevelRenderer.renderLineBox(pose, lines, b, r(HURT), g(HURT), bl(HURT), 0.5f);
        }
        RUNNING.int2ObjectEntrySet().removeIf(en -> now - en.getValue().start > en.getValue().move.total() + 2);
        for (var en : RUNNING.int2ObjectEntrySet()) {                          // hitboxes
            Entity e = mc.level.getEntity(en.getIntKey());
            if (!(e instanceof LivingEntity l)) continue;
            Running run = en.getValue();
            float tick = now - run.start;
            if (tick < 0 || tick > run.move.total()) continue;
            int c = phaseColor(run.move, tick);
            float a = c == ACTIVE ? 1f : 0.55f;
            Vec3 eye = l.getEyePosition(pt).subtract(cam), look = l.getViewVector(pt);
            draw(pose, lines, run.move, eye, look, l.getViewYRot(pt), r(c), g(c), bl(c), a);
        }
        buffers.endBatch(RenderType.lines());
    }

    static void draw(PoseStack pose, VertexConsumer vc, Move m, Vec3 eye, Vec3 look, float yawDeg, float r, float g, float b, float a) {
        Matrix4f mat = pose.last().pose();
        Matrix3f nrm = pose.last().normal();
        switch (m.shape) {
            case SPHERE -> {
                Vec3 c = eye.add(look.scale(m.range * 0.6));
                for (int axis = 0; axis < 3; axis++) circle(mat, nrm, vc, c, m.radius, axis, r, g, b, a);
            }
            case CONE -> {
                Vec3 up = Math.abs(look.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
                Vec3 side = look.cross(up).normalize(), upr = side.cross(look).normalize();
                double half = Math.toRadians(m.angle / 2);
                Vec3 prev = null, first = null;
                for (int k = 0; k <= 12; k++) {
                    double t = Math.PI * 2 * k / 12;
                    Vec3 dir = look.scale(Math.cos(half)).add(side.scale(Math.sin(half) * Math.cos(t))).add(upr.scale(Math.sin(half) * Math.sin(t)));
                    Vec3 tip = eye.add(dir.scale(m.range));
                    if (k % 3 == 0 && k < 12) line(mat, nrm, vc, eye, tip, r, g, b, a);
                    if (prev != null) line(mat, nrm, vc, prev, tip, r, g, b, a);
                    prev = tip;
                }
            }
            case BOX -> {
                float yaw = yawDeg * Mth.DEG_TO_RAD;
                Vec3 f = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)), s = new Vec3(-f.z, 0, f.x);
                double feet = eye.y - 1.62;
                Vec3 base = new Vec3(eye.x, feet, eye.z);
                Vec3[] c = new Vec3[8];
                for (int i = 0; i < 8; i++) {
                    double d = (i & 1) == 0 ? 0 : m.range, w = (i & 2) == 0 ? -m.radius : m.radius, h = (i & 4) == 0 ? 0 : m.height;
                    c[i] = base.add(f.scale(d)).add(s.scale(w)).add(0, h, 0);
                }
                int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
                for (int[] ed : edges) line(mat, nrm, vc, c[ed[0]], c[ed[1]], r, g, b, a);
            }
        }
    }

    static void circle(Matrix4f mat, Matrix3f nrm, VertexConsumer vc, Vec3 c, double rad, int axis, float r, float g, float b, float a) {
        Vec3 prev = null;
        for (int k = 0; k <= 24; k++) {
            double t = Math.PI * 2 * k / 24, x = Math.cos(t) * rad, y = Math.sin(t) * rad;
            Vec3 p = switch (axis) {
                case 0 -> c.add(x, y, 0);
                case 1 -> c.add(x, 0, y);
                default -> c.add(0, x, y);
            };
            if (prev != null) line(mat, nrm, vc, prev, p, r, g, b, a);
            prev = p;
        }
    }

    static void line(Matrix4f mat, Matrix3f nrm, VertexConsumer vc, Vec3 p0, Vec3 p1, float r, float g, float b, float a) {
        Vec3 n = p1.subtract(p0);
        n = n.lengthSqr() < 1e-8 ? new Vec3(0, 1, 0) : n.normalize();
        vc.vertex(mat, (float) p0.x, (float) p0.y, (float) p0.z).color(r, g, b, a).normal(nrm, (float) n.x, (float) n.y, (float) n.z).endVertex();
        vc.vertex(mat, (float) p1.x, (float) p1.y, (float) p1.z).color(r, g, b, a).normal(nrm, (float) n.x, (float) n.y, (float) n.z).endVertex();
    }

    static float r(int c) {
        return ((c >> 16) & 255) / 255f;
    }

    static float g(int c) {
        return ((c >> 8) & 255) / 255f;
    }

    static float bl(int c) {
        return (c & 255) / 255f;
    }

    // ------------------------------------------------------------------ the frame bar

    /** Your own move, a cell a tick, above the hotbar. */
    public static final class FrameBar implements IGuiOverlay {
        @Override
        public void render(ForgeGui gui, GuiGraphics gg, float partial, int width, int height) {
            Minecraft mc = Minecraft.getInstance();
            if (!on() || mine == null || mc.level == null || mc.options.hideGui) return;
            float tick = mc.level.getGameTime() + partial - mine.start;
            Move m = mine.move;
            if (tick > m.total() + 30) {
                mine = null;
                return;
            }
            int cell = Mth.clamp(160 / Math.max(1, m.total()), 3, 8), w = cell * m.total(), x = width / 2 - w / 2, y = height - 62;
            gg.fill(x - 2, y - 2, x + w + 2, y + 9, 0xB0080B12);
            for (int i = 0; i < m.total(); i++) gg.fill(x + i * cell, y, x + (i + 1) * cell - 1, y + 7, phaseColor(m, i + 0.5f));
            int cx = x + m.cancel * cell;                                       // a landed blow cancels from here
            gg.fill(cx - 1, y - 3, cx, y + 10, 0xFFFFFFFF);
            if (tick <= m.total()) {
                int mx = x + (int) (Mth.clamp(tick, 0, m.total()) * cell);
                gg.fill(mx - 1, y - 4, mx + 1, y + 11, 0xFF101010);
                gg.fill(mx, y - 4, mx + 1, y + 11, 0xFFFFFFFF);
            }
            Font font = mc.font;
            Component label = MoveListScreen.nameOf(m).copy().append(Component.translatable("hud.dbzenith.frames", m.startup, m.active, m.recovery, m.cancel));
            DbzTheme.text(gg, font, label, width / 2f - font.width(label) * 0.65f / 2, y - 10, DbzTheme.TEXT, 0.65f);
        }
    }

    // ------------------------------------------------------------------ the command

    @SubscribeEvent
    public static void onCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dbzhitbox").executes(ctx -> {
            boolean now = !DBZConfig.CLIENT.hitboxOverlay.get();
            DBZConfig.CLIENT.hitboxOverlay.set(now);
            DBZConfig.CLIENT_SPEC.save();
            Minecraft.getInstance().player.displayClientMessage(Component.translatable(now ? "message.dbzenith.hitbox_on" : "message.dbzenith.hitbox_off"), true);
            return 1;
        }));
    }
}
