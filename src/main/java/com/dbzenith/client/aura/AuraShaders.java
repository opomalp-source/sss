package com.dbzenith.client.aura;

import com.dbzenith.DBZenith;
import com.dbzenith.config.DBZConfig;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.lang.reflect.Method;

/**
 * The aura's own shader (CX-24): {@code aura} blends normally, {@code aura_glow} adds light. With a shader pack on
 * (Oculus), or when the setting asks for it, auras fall back to the plain built-in shaders instead.
 */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AuraShaders {
    static ShaderInstance shell, glow;

    private static boolean irisChecked;
    private static Object irisApi;
    private static Method irisInUse;

    private AuraShaders() {}

    @SubscribeEvent
    public static void register(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(DBZenith.MOD_ID, "aura"),
                DefaultVertexFormat.NEW_ENTITY), s -> shell = s);
        event.registerShader(new ShaderInstance(event.getResourceProvider(), new ResourceLocation(DBZenith.MOD_ID, "aura_glow"),
                DefaultVertexFormat.NEW_ENTITY), s -> glow = s);
    }

    /** Draw with the aura shader this frame? 0 auto (yes unless a shader pack is on), 1 always, 2 never. */
    static boolean use() {
        if (shell == null || glow == null) return false;
        int mode = DBZConfig.CLIENT.auraRenderer.get();
        if (mode == 2) return false;
        return mode == 1 || !shaderPackOn();
    }

    /** Is an Oculus / Iris shader pack in use? Looked up by name, so neither has to be installed. */
    static boolean shaderPackOn() {
        try {
            if (!irisChecked) {
                irisChecked = true;
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisApi = api.getMethod("getInstance").invoke(null);
                irisInUse = api.getMethod("isShaderPackInUse");
            }
            return irisApi != null && (Boolean) irisInUse.invoke(irisApi);
        } catch (Throwable e) {
            irisApi = null;
            return false;
        }
    }
}
