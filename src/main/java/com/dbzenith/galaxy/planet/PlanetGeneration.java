package com.dbzenith.galaxy.planet;
import com.dbzenith.DBZenith;
import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.*;
public final class PlanetGeneration {
    private static final DeferredRegister<Codec<? extends ChunkGenerator>> GENERATORS=DeferredRegister.create(Registries.CHUNK_GENERATOR,DBZenith.MOD_ID);
    public static final RegistryObject<Codec<? extends ChunkGenerator>> PLANET=GENERATORS.register("procedural_planet",()->PlanetGenerator.CODEC);
    public static final RegistryObject<Codec<? extends ChunkGenerator>> ORBIT=GENERATORS.register("orbital_space",()->com.dbzenith.galaxy.space.OrbitGenerator.CODEC);
    public static void register(IEventBus bus){GENERATORS.register(bus);}
}
