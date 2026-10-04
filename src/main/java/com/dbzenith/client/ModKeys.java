package com.dbzenith.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** Keybinds; all rebindable under Controls > "Dragon Block Zenith". */
public final class ModKeys {
    public static final String CATEGORY = "key.categories.dbzenith";

    public static final KeyMapping CHARGE = key("charge", GLFW.GLFW_KEY_G);
    public static final KeyMapping LOWER_RELEASE = key("lower_release", GLFW.GLFW_KEY_Z);
    public static final KeyMapping FLY = key("fly", GLFW.GLFW_KEY_V);
    public static final KeyMapping GUARD = key("guard", GLFW.GLFW_KEY_LEFT_ALT);
    public static final KeyMapping KI_ATTACK = key("ki_attack", GLFW.GLFW_KEY_R);
    public static final KeyMapping NEXT_TECHNIQUE = key("next_technique", GLFW.GLFW_KEY_Y);
    public static final KeyMapping HEAVY = key("heavy", GLFW.GLFW_KEY_H);
    public static final KeyMapping DASH = key("dash", GLFW.GLFW_KEY_B);
    public static final KeyMapping TRANSFORM = key("transform", GLFW.GLFW_KEY_J);
    public static final KeyMapping OVERDRIVE = key("overdrive", GLFW.GLFW_KEY_N);
    public static final KeyMapping STATS = key("stats", GLFW.GLFW_KEY_K);

    public static final KeyMapping[] ALL = {CHARGE, LOWER_RELEASE, FLY, GUARD, KI_ATTACK, NEXT_TECHNIQUE, HEAVY, DASH, TRANSFORM, OVERDRIVE, STATS};

    private ModKeys() {}

    private static KeyMapping key(String name, int glfwKey) {
        return new KeyMapping("key.dbzenith." + name, KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, glfwKey, CATEGORY);
    }
}
