package com.dbzenith.client.fx;

import com.dbzenith.DBZenith;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.network.DamageNumberPacket;
import com.dbzenith.network.ImpactPacket;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Damage popups (CX-19e): the damage a blow did, popping up over whoever took it, rising and fading; and words for
 * what made a blow special (COUNTER!, CRITICAL!, GUARD BREAK!, PERFECT GUARD!). Colours: white for a blow, yellow for a
 * heavy one, orange for a critical, gold for a counter, pale blue through a guard, the ki's colour for a ki attack.
 * Client config {@code damagePopups} turns them off.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, value = Dist.CLIENT)
public final class DamagePopups {
    private static final int MAX = 64, NUMBER_LIFE = 22, WORD_LIFE = 28;
    public static final int WHITE = 0xFFFFFFFF, HEAVY = 0xFFFFE14A, CRIT = 0xFFFF7A4A, COUNTER = 0xFFFFD34A, GUARDED = 0xFFAEE6FF;

    private record Popup(Component text, Vec3 pos, int color, float size, int born, int life, boolean word) {}

    private static final List<Popup> POPUPS = new ArrayList<>();
    private static int ticks;
    private static boolean left;

    private DamagePopups() {}

    private static boolean on() {
        return DBZConfig.CLIENT.damagePopups.get();
    }

    public static void number(int entityId, float amount, int flags, int kiColor) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !on() || amount <= 0) return;
        Entity e = mc.level.getEntity(entityId);
        if (e == null) return;
        int color = WHITE;
        float size = 1f;
        if ((flags & DamageNumberPacket.KI) != 0) color = 0xFF000000 | FxDraw.mix(kiColor, 0xFFFFFF, 0.45f);
        if ((flags & DamageNumberPacket.HEAVY) != 0) {
            color = HEAVY;
            size = 1.25f;
        }
        if ((flags & ImpactPacket.CRIT) != 0) {
            color = CRIT;
            size = 1.4f;
        }
        if ((flags & ImpactPacket.COUNTER) != 0) {
            color = COUNTER;
            size = 1.5f;
        }
        if ((flags & DamageNumberPacket.GUARDED) != 0) {
            color = GUARDED;
            size = 0.8f;
        }
        left = !left;                                                          // alternate left and right of the head
        Vec3 at = e.position().add((left ? -0.35 : 0.35) + (mc.level.random.nextDouble() - 0.5) * 0.2,
                e.getBbHeight() + 0.25 + mc.level.random.nextDouble() * 0.2, (mc.level.random.nextDouble() - 0.5) * 0.3);
        add(new Popup(Component.literal(compact(amount)), at, color, size, ticks, NUMBER_LIFE, false));
    }

    /** A word over an entity: "counter", "critical", "guard_break", "perfect_guard" (popup.dbzenith.*). */
    public static void word(int entityId, String key, int color) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !on() || entityId < 0) return;
        Entity e = mc.level.getEntity(entityId);
        if (e == null) return;
        Vec3 at = e.position().add(0, e.getBbHeight() + 1.05, 0);                 // above the numbers
        add(new Popup(Component.translatable("popup.dbzenith." + key), at, color, 1.35f, ticks, WORD_LIFE, true));
    }

    private static void add(Popup p) {
        if (POPUPS.size() >= MAX) POPUPS.remove(0);
        POPUPS.add(p);
    }

    /** 87, 950, 1.2K, 12K, 1.2M. */
    static String compact(double v) {
        if (v < 1000) return String.valueOf((int) Math.max(1, Math.round(v)));
        String[] units = {"K", "M", "B", "T"};
        int u = -1;
        while (v >= 1000 && u < units.length - 1) {
            v /= 1000;
            u++;
        }
        return (v < 10 ? String.format("%.1f", v) : String.valueOf((int) v)) + units[u];
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            POPUPS.clear();
            return;
        }
        if (mc.isPaused()) return;
        ticks++;
        POPUPS.removeIf(p -> ticks - p.born > p.life);
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || POPUPS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        Camera cam = event.getCamera();
        Vec3 c = cam.getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Font font = mc.font;
        float pt = event.getPartialTick();
        for (Popup p : POPUPS) {
            float age = ticks - p.born + pt, t = Mth.clamp(age / p.life, 0, 1);
            float pop = age < 3 ? Mth.lerp(age / 3f, 1.7f, 1f) : 1f;              // pops in big, settles
            float rise = (p.word ? 0.3f : 0.6f) * (1 - (1 - t) * (1 - t));
            float alpha = t < 0.7f ? 1f : 1 - (t - 0.7f) / 0.3f;
            if (alpha <= 0.02f) continue;
            double dist = c.distanceTo(p.pos);
            float scale = 0.025f * p.size * pop * (float) Math.max(1, dist / 8);  // stays readable further off
            pose.pushPose();
            pose.translate(p.pos.x - c.x, p.pos.y + rise - c.y, p.pos.z - c.z);
            pose.mulPose(cam.rotation());
            pose.scale(-scale, -scale, scale);
            FormattedCharSequence text = p.text.getVisualOrderText();
            float x = -font.width(text) / 2f;
            int a = (int) (alpha * 255) << 24;
            font.drawInBatch8xOutline(text, x, 0, a | (p.color & 0xFFFFFF), a | 0x101018, pose.last().pose(), buffers, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
        buffers.endBatch();
    }
}
