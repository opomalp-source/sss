package com.dbzenith.social;
import net.minecraft.nbt.CompoundTag;
import java.util.*;
/** Client snapshot has no Minecraft client dependencies, so common event classes remain server-safe. */
public final class SocialState {
    public static CompoundTag snapshot=new CompoundTag();
    public static final Map<UUID,String> clanNames=new HashMap<>();
    public static int revision;
    public static void clear(){snapshot=new CompoundTag();clanNames.clear();revision++;}
}
