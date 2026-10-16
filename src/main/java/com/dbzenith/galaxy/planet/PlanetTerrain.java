package com.dbzenith.galaxy.planet;

import com.dbzenith.galaxy.GalaxyCatalog;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Stateless v1 terrain functions: chunk order, thread order and neighbouring visits do not affect terrain. */
public final class PlanetTerrain {
    public final long seed;
    public final GalaxyCatalog.Kind kind;
    public PlanetTerrain(long seed,GalaxyCatalog.Kind kind){this.seed=seed;this.kind=kind;}
    private double hash(int x,int y,int z){return (GalaxyCatalog.mix(seed ^ x*0x9e3779b97f4a7c15L ^ y*0x632be59bd9b4e019L ^ z*0x94d049bb133111ebL)>>>11)*0x1.0p-53;}
    private static double ease(double x){return x*x*(3-2*x);}
    private static double lerp(double t,double a,double b){return a+(b-a)*t;}
    public double noise(double x,double z,double scale){
        double a=x/scale,b=z/scale;int ix=(int)Math.floor(a),iz=(int)Math.floor(b);double fx=ease(a-ix),fz=ease(b-iz);
        return lerp(fz,lerp(fx,hash(ix,0,iz),hash(ix+1,0,iz)),lerp(fx,hash(ix,0,iz+1),hash(ix+1,0,iz+1)))*2-1;
    }
    public int sea(){return switch(kind){case VOLCANIC->54;case OCEAN->82;case BARREN,DESERT,CRYSTAL,TRAINING,ASTEROIDS,GAS_GIANT->-64;default->64;};}
    public int height(int x,int z){
        double continental=noise(x,z,420),hills=noise(x,z,95),detail=noise(x,z,23);
        double base=switch(kind){
            case OCEAN -> 62+continental*30+hills*12;
            case DESERT -> 78+continental*20+Math.sin(x*.037+noise(x,z,170)*5)*5+hills*9;
            case ICE -> 82+continental*24+Math.abs(hills)*24;
            case VOLCANIC -> 78+continental*30+Math.pow(Math.max(0,hills),2)*60;
            case JUNGLE -> 79+continental*27+hills*17;
            case BARREN -> 71+continental*34+hills*11;
            case CRYSTAL -> 75+continental*26+Math.pow(Math.max(0,noise(x,z,15)),9)*48;
            case TOXIC_SWAMP -> 64+continental*6+hills*4;
            case TRAINING -> 76+continental*16+hills*9;
            case ASTEROIDS -> 91;
            case GAS_GIANT -> 110;
            default -> 72+continental*27+hills*15;
        };
        if(kind==GalaxyCatalog.Kind.VOLCANIC && Math.abs(noise(x,z,110))<.055)base=48;
        return Math.max(15,Math.min(220,(int)(base+detail*3)));
    }
    public BlockState top(){return (switch(kind){
        case DESERT->Blocks.RED_SAND;case ICE->Blocks.SNOW_BLOCK;case VOLCANIC->Blocks.BASALT;case BARREN,ASTEROIDS->Blocks.ANDESITE;
        case CRYSTAL->Blocks.CALCITE;case TOXIC_SWAMP->Blocks.MUD;case JUNGLE->Blocks.MOSS_BLOCK;case GAS_GIANT->Blocks.WHITE_STAINED_GLASS;
        default->Blocks.GRASS_BLOCK;
    }).defaultBlockState();}
    private BlockState stone(){return (switch(kind){case VOLCANIC->Blocks.BLACKSTONE;case CRYSTAL->Blocks.CALCITE;case BARREN->Blocks.TUFF;default->Blocks.STONE;}).defaultBlockState();}
    public BlockState block(int x,int y,int z){return columnBlock(x,y,z,height(x,z));}
    public BlockState columnBlock(int x,int y,int z,int h){
        if(y<-64||y>=320)return Blocks.AIR.defaultBlockState();
        if(kind==GalaxyCatalog.Kind.ASTEROIDS){
            // An assured home asteroid plus independently seeded clusters in 48-block cells.
            double d=((double)x*x+(double)z*z)/144d+(y-80d)*(y-80d)/64;
            if(d<1)return stone();
            int cx=Math.floorDiv(x,48),cz=Math.floorDiv(z,48);
            double ax=cx*48+12+hash(cx,1,cz)*24,az=cz*48+12+hash(cx,2,cz)*24,ay=64+hash(cx,3,cz)*76,r=4+hash(cx,4,cz)*7;
            return (x-ax)*(x-ax)+(z-az)*(z-az)+(y-ay)*(y-ay)<r*r?ore(x,y,z):Blocks.AIR.defaultBlockState();
        }
        if(y==-64)return Blocks.BEDROCK.defaultBlockState();
        if(y>h){
            if(y<=sea())return (kind==GalaxyCatalog.Kind.VOLCANIC?Blocks.LAVA:Blocks.WATER).defaultBlockState();
            return decoration(x,y,z,h);
        }
        if(y<h-5 && y>-53 && y<55 && noise(x+y*2,z-y,31)>.62 && noise(x-y,z+y*3,19)>.36)return Blocks.AIR.defaultBlockState();
        if(y==h){
            if(h<sea()-1)return (kind==GalaxyCatalog.Kind.VOLCANIC?Blocks.MAGMA_BLOCK:kind==GalaxyCatalog.Kind.TOXIC_SWAMP?Blocks.CLAY:Blocks.SAND).defaultBlockState();
            return top();
        }
        if(y>h-4)return (kind==GalaxyCatalog.Kind.DESERT?Blocks.RED_SANDSTONE:kind==GalaxyCatalog.Kind.ICE?Blocks.PACKED_ICE:
                kind==GalaxyCatalog.Kind.TEMPERATE||kind==GalaxyCatalog.Kind.JUNGLE||kind==GalaxyCatalog.Kind.OCEAN||kind==GalaxyCatalog.Kind.TRAINING?Blocks.DIRT:kind==GalaxyCatalog.Kind.TOXIC_SWAMP?Blocks.CLAY:stone().getBlock()).defaultBlockState();
        return ore(x,y,z);
    }
    private BlockState ore(int x,int y,int z){
        double n=hash(Math.floorDiv(x,2),Math.floorDiv(y,2),Math.floorDiv(z,2));
        if(n>.997&&y<5)return Blocks.DIAMOND_ORE.defaultBlockState();
        if(n>.987&&y<45)return Blocks.IRON_ORE.defaultBlockState();
        if(n>.976)return Blocks.COPPER_ORE.defaultBlockState();
        if(n>.965)return Blocks.COAL_ORE.defaultBlockState();return stone();
    }
    private BlockState decoration(int x,int y,int z,int h){
        if(h<=sea())return Blocks.AIR.defaultBlockState();
        if(kind==GalaxyCatalog.Kind.CRYSTAL && hash(x,77,z)>.94 && y<=h+1+(int)(hash(x,76,z)*9))
            return (y==h+1?Blocks.AMETHYST_BLOCK:Blocks.BUDDING_AMETHYST).defaultBlockState();
        if(kind==GalaxyCatalog.Kind.ICE&&y==h+1&&hash(x,3,z)>.55)return Blocks.SNOW.defaultBlockState();
        // Chunk-independent, compact trees: their cells reserve space so crowns never depend on chunk order.
        if(kind==GalaxyCatalog.Kind.JUNGLE||kind==GalaxyCatalog.Kind.TEMPERATE||kind==GalaxyCatalog.Kind.TRAINING){
            int cell=kind==GalaxyCatalog.Kind.JUNGLE?9:15,cx=Math.floorDiv(x,cell),cz=Math.floorDiv(z,cell),tx=cx*cell+cell/2,tz=cz*cell+cell/2;
            int th=height(tx,tz),tall=kind==GalaxyCatalog.Kind.JUNGLE?9:5;
            if(th>sea() && hash(cx,8,cz)>.35){
                if(x==tx&&z==tz&&y<=th+tall)return (kind==GalaxyCatalog.Kind.JUNGLE?Blocks.JUNGLE_LOG:Blocks.OAK_LOG).defaultBlockState();
                if(Math.abs(x-tx)<=2&&Math.abs(z-tz)<=2&&y>=th+tall-2&&y<=th+tall+1)
                    return (kind==GalaxyCatalog.Kind.JUNGLE?Blocks.JUNGLE_LEAVES:Blocks.OAK_LEAVES).defaultBlockState().setValue(LeavesBlock.PERSISTENT,true);
            }
        }
        return Blocks.AIR.defaultBlockState();
    }
}
