package com.dbzenith.galaxy.space;

import com.dbzenith.galaxy.GalaxyCatalog;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.*;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

/** Sparse orbital volume. Only requested chunks exist; a mineable inner belt contains original block clusters. */
public final class OrbitGenerator extends ChunkGenerator {
    public static final Codec<OrbitGenerator> CODEC=RecordCodecBuilder.create(i->i.group(
        BiomeSource.CODEC.fieldOf("biome_source").forGetter(g->g.biomeSource),
        Codec.STRING.fieldOf("system").forGetter(g->g.system.toString()),
        Codec.LONG.fieldOf("belt_seed").forGetter(g->g.seed)
    ).apply(i,OrbitGenerator::new));
    public final GalaxyCatalog.Id system;private final long seed;
    public OrbitGenerator(BiomeSource biome,long seed,GalaxyCatalog.Id system){this(biome,system.toString(),seed);}
    private OrbitGenerator(BiomeSource biome,String system,long seed){super(biome);this.system=GalaxyCatalog.Id.parse(system);this.seed=seed;}
    @Override protected Codec<? extends ChunkGenerator> codec(){return CODEC;}
    @Override public int getGenDepth(){return 2048;}
    @Override public int getMinY(){return -1024;}
    @Override public int getSeaLevel(){return -1024;}
    @Override public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> lookup,RandomState random,long seed){return ChunkGeneratorStructureState.createForFlat(random,seed,biomeSource,Stream.empty());}
    public BlockState block(int x,int y,int z){
        double d=Math.hypot(x,z);if(d<10000||d>11600||y<40||y>230)return Blocks.AIR.defaultBlockState();
        int cx=Math.floorDiv(x,64),cz=Math.floorDiv(z,64);long h=GalaxyCatalog.mix(seed^cx*0x9e3779b97f4a7c15L^cz*0x632be59bd9b4e019L);
        if((h&15)>3)return Blocks.AIR.defaultBlockState();
        double ax=cx*64+32,az=cz*64+32,ay=70+Math.floorMod(h>>>8,120),r=6+Math.floorMod(h>>>20,10);
        if((x-ax)*(x-ax)+(z-az)*(z-az)+(y-ay)*(y-ay)*1.4>r*r)return Blocks.AIR.defaultBlockState();
        long ore=GalaxyCatalog.mix(h^x*31L^y*97L^z*131L);
        return ((ore&63)==0?Blocks.IRON_ORE:(ore&31)==0?Blocks.PACKED_ICE:Blocks.ANDESITE).defaultBlockState();
    }
    @Override public CompletableFuture<ChunkAccess> fillFromNoise(Executor e,Blender b,RandomState r,StructureManager sm,ChunkAccess chunk){
        double d=Math.hypot(chunk.getPos().getMiddleBlockX(),chunk.getPos().getMiddleBlockZ());
        if(d>9980&&d<11620){var p=new BlockPos.MutableBlockPos();for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=40;y<=230;y++){
            int wx=chunk.getPos().getMinBlockX()+x,wz=chunk.getPos().getMinBlockZ()+z;var s=block(wx,y,wz);if(!s.isAir())chunk.setBlockState(p.set(wx,y,wz),s,false);
        }}return CompletableFuture.completedFuture(chunk);
    }
    @Override public int getBaseHeight(int x,int z,Heightmap.Types t,LevelHeightAccessor l,RandomState r){for(int y=230;y>=40;y--)if(t.isOpaque().test(block(x,y,z)))return y+1;return -1024;}
    @Override public NoiseColumn getBaseColumn(int x,int z,LevelHeightAccessor l,RandomState r){var a=new BlockState[l.getHeight()];for(int i=0;i<a.length;i++)a[i]=block(x,l.getMinBuildHeight()+i,z);return new NoiseColumn(l.getMinBuildHeight(),a);}
    @Override public void buildSurface(WorldGenRegion l,StructureManager s,RandomState r,ChunkAccess c){}
    @Override public void applyCarvers(WorldGenRegion l,long seed,RandomState r,BiomeManager b,StructureManager s,ChunkAccess c,GenerationStep.Carving step){}
    @Override public void spawnOriginalMobs(WorldGenRegion l){}
    @Override public void addDebugScreenInfo(List<String> l,RandomState r,BlockPos p){l.add("Zenith orbital space / "+system);}
}
