package com.dbzenith.client.motion;

import com.dbzenith.DBZenith;
import com.dbzenith.config.DBZConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

import java.util.Map;

/**
 * Tools for the motion engine (CX-18): each figure's state above its head, and the client command {@code /dbzanim}:
 * <ul>
 *   <li>{@code /dbzanim toggle} the engine on or off (the old animations come back);</li>
 *   <li>{@code /dbzanim labels} the state labels;</li>
 *   <li>{@code /dbzanim set <name> <value>} and {@code /dbzanim get [name]} the {@link Tuning} values, live;</li>
 *   <li>{@code /dbzanim info} the engine's counts and the clips your own figure uses.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, value = Dist.CLIENT)
public final class MotionDebug {
    private static boolean labels;

    private MotionDebug() {}

    private static boolean labelsOn() {
        return labels || DBZConfig.CLIENT.animationLabels.get();
    }

    @SubscribeEvent
    public static void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
        if (!labelsOn()) return;
        LivingEntity e = event.getEntity();
        Motion m = MotionEngine.peek(e);
        if (m == null) return;
        String text = m.state.key + (m.simple ? " ~" : "") + String.format(" %.2f", m.speed) + (m.engine < 0.99f ? String.format(" e%.1f", m.engine) : "");
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(0, e.getBbHeight() + 0.85, 0);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(-0.025f, -0.025f, 0.025f);
        Matrix4f mat = pose.last().pose();
        float x = -font.width(text) / 2f;
        font.drawInBatch(text, x, 0, 0xFFFFE070, false, mat, event.getMultiBufferSource(), Font.DisplayMode.SEE_THROUGH, 0x60000000, event.getPackedLight());
        pose.popPose();
    }

    @SubscribeEvent
    public static void onCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dbzanim")
                .then(Commands.literal("toggle").executes(ctx -> {
                    boolean on = !DBZConfig.CLIENT.animationEngine.get();
                    DBZConfig.CLIENT.animationEngine.set(on);
                    ctx.getSource().sendSuccess(() -> Component.literal("Motion engine " + (on ? "on" : "off (old animations)")), false);
                    return 1;
                }))
                .then(Commands.literal("labels").executes(ctx -> {
                    labels = !labels;
                    ctx.getSource().sendSuccess(() -> Component.literal("Motion labels " + (labels ? "on" : "off")), false);
                    return 1;
                }))
                .then(Commands.literal("get")
                        .executes(ctx -> {
                            StringBuilder sb = new StringBuilder();
                            for (Map.Entry<String, Float> e : Tuning.all().entrySet()) sb.append(e.getKey()).append('=').append(e.getValue()).append("  ");
                            ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
                            return 1;
                        })
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Tuning.all().keySet(), b))
                                .executes(ctx -> {
                                    String n = StringArgumentType.getString(ctx, "name");
                                    Float v = Tuning.all().get(n);
                                    ctx.getSource().sendSuccess(() -> Component.literal(n + " = " + v), false);
                                    return v == null ? 0 : 1;
                                })))
                .then(Commands.literal("set")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(Tuning.all().keySet(), b))
                                .then(Commands.argument("value", FloatArgumentType.floatArg())
                                        .executes(ctx -> {
                                            String n = StringArgumentType.getString(ctx, "name");
                                            float v = FloatArgumentType.getFloat(ctx, "value");
                                            boolean ok = Tuning.set(n, v);
                                            ctx.getSource().sendSuccess(() -> Component.literal(ok ? n + " = " + v : "No tuning value " + n), false);
                                            return ok ? 1 : 0;
                                        }))))
                .then(Commands.literal("info").executes(ctx -> {
                    Minecraft mc = Minecraft.getInstance();
                    StringBuilder sb = new StringBuilder("Motion engine: " + (MotionEngine.enabled() ? "on" : "off") + ", " + MotionEngine.count()
                            + " figures, " + MotionData.data().clips.size() + " clips, " + MotionData.sets().size() + " sets.");
                    if (mc.player != null) {
                        Motion m = MotionEngine.get(mc.player);
                        sb.append(" You: ").append(m.state.key).append(" (");
                        for (State s : State.ALL) {
                            Clip c = m.clips[s.ordinal()];
                            if (c != null) sb.append(s.key).append('=').append(c.id).append(' ');
                        }
                        sb.append(')');
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
                    return 1;
                })));
    }
}
