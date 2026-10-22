package com.dbzenith.living;

import com.dbzenith.nemesis.NamedFighter;
import net.minecraft.server.MinecraftServer;

/** Tests: the errand a resident would ask for now ({@link Errands#make}). */
public final class ErrandsTestAccess {
    private ErrandsTestAccess() {}

    public static Errand make(MinecraftServer server, LivingData ld, Settlement s, NamedFighter c, long now) {
        return Errands.make(server, ld, s, c, now);
    }
}
