package com.dbzenith.galaxy.planet;

import com.dbzenith.galaxy.GalaxyCatalog;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.*;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

/** One serializable generator for every procedural body. No mutable random state or shared chunk state. */
public final class PlanetGenerator extends ChunkGenerator {
    public static final Codec<PlanetGenerator> CODEC=RecordCodecBuilder.create(i->i.group(
        BiomeSource.CODEC.fieldOf("biome_source").forGetter(g->g.getBiomeSource()),
        Codec.LONG.fieldOf("planet_seed").forGetter(g->g.seed),
        Codec.STRING.fieldOf("body").forGetter(g->g.ref.key()),
        Codec.intRange(0,11).fieldOf("kind").forGetter(g->g.kind.ordinal()),
        Codec.intRange(256,16384).fieldOf("radius").forGetter(g->g.radius),
        Codec.intRange(1,1).optionalFieldOf("terrain_version",1).forGetter(g->1)
    ).apply(i,PlanetGenerator::new));
    public final GalaxyCatalog.Ref ref;public final GalaxyCatalog.Kind kind;public final int radius;
    private final long seed;public final PlanetTerrain terrain;
    public PlanetGenerator(BiomeSource biomes,long seed,String ref,int kind,int radius,int version){
        super(biomes);if(version!=1)throw new IllegalArgumentException("Unsupported planet terrain version");
        this.seed=seed;this.ref=GalaxyCatalog.Ref.parse(ref);this.kind=GalaxyCatalog.Kind.values()[kind];this.radius=radius;this.terrain=new PlanetTerrain(seed,this.kind,com.dbzenith.galaxy.IconicSystems.style(this.ref));
    }
    @Override protected Codec<? extends ChunkGenerator> codec(){return CODEC;}
    @Override public int getGenDepth(){return 384;}
    @Override public int getMinY(){return -64;}
    @Override public int getSeaLevel(){return terrain.sea();}
    @Override public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> structures,RandomState random,long seed){
        return ChunkGeneratorStructureState.createForFlat(random,seed,biomeSource,Stream.empty());
    }
    @Override public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor,Blender blender,RandomState random,StructureManager structures,ChunkAccess chunk){
        // Chunk-status scheduling already gives this stage exclusive access on a world-generation worker.
        BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
        Heightmap floor=chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG),surface=chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        for(int lx=0;lx<16;lx++)for(int lz=0;lz<16;lz++){
            int x=chunk.getPos().getMinBlockX()+lx,z=chunk.getPos().getMinBlockZ()+lz,h=terrain.height(x,z);
            int top=kind==GalaxyCatalog.Kind.ASTEROIDS?155:Math.max(h+15,terrain.sea());
            for(int y=-64;y<=top;y++){
                BlockState block=terrain.columnBlock(x,y,z,h);if(block.isAir())continue;
                pos.set(x,y,z);chunk.setBlockState(pos,block,false);floor.update(lx,y,lz,block);surface.update(lx,y,lz,block);
                if(!block.getFluidState().isEmpty())chunk.markPosForPostprocessing(pos);
            }
        }
        return CompletableFuture.completedFuture(chunk);
    }
    @Override public int getBaseHeight(int x,int z,Heightmap.Types type,LevelHeightAccessor level,RandomState random){
        int h=terrain.height(x,z);
        for(int y=Math.min(255,level.getMaxBuildHeight()-1);y>=level.getMinBuildHeight();y--)if(type.isOpaque().test(terrain.columnBlock(x,y,z,h)))return y+1;
        return level.getMinBuildHeight();
    }
    @Override public NoiseColumn getBaseColumn(int x,int z,LevelHeightAccessor level,RandomState random){
        BlockState[] column=new BlockState[level.getHeight()];int h=terrain.height(x,z);
        for(int i=0;i<column.length;i++)column[i]=terrain.columnBlock(x,level.getMinBuildHeight()+i,z,h);
        return new NoiseColumn(level.getMinBuildHeight(),column);
    }
    @Override public void buildSurface(WorldGenRegion level,StructureManager structures,RandomState random,ChunkAccess chunk){PlanetLandmarks.decorate(this,chunk);}
    @Override public void applyCarvers(WorldGenRegion level,long seed,RandomState random,BiomeManager biomes,StructureManager structures,ChunkAccess chunk,GenerationStep.Carving step){}
    @Override public void spawnOriginalMobs(WorldGenRegion level){}
    @Override public void addDebugScreenInfo(List<String> lines,RandomState random,BlockPos pos){lines.add("Zenith planet "+ref.key()+" / terrain v1");}
}
