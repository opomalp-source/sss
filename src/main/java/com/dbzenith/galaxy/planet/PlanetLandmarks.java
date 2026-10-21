package com.dbzenith.galaxy.planet;

import com.dbzenith.galaxy.GalaxyCatalog;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

/** Chunk-clipped structures: no neighbour writes, population order dependence or changes to terrain-v1 noise. */
public final class PlanetLandmarks {
    public record Site(int x,int y,int z,int kind){public String key(){return x+"_"+z;}}
    public static Site site(PlanetGenerator g,int cellX,int cellZ){
        if(g.ref.planet()==0&&com.dbzenith.galaxy.IconicSystems.isNew(g.ref.system())&&cellX==0&&cellZ==0)return new Site(24,Math.max(g.terrain.sea()+2,g.terrain.height(24,24)+1),24,9);   // the series world's own place (IconicLandmarks)
        if(g.kind==GalaxyCatalog.Kind.ASTEROIDS||g.kind==GalaxyCatalog.Kind.GAS_GIANT)return null;
        long hash=GalaxyCatalog.mix(g.terrain.seed^cellX*0x632be59bd9b4e019L^cellZ*0x94d049bb133111ebL);
        if(Math.floorMod(hash,5)!=0)return null;
        int x=cellX*512+96+(int)((hash>>>8)&255),z=cellZ*512+96+(int)((hash>>>24)&255);
        int y=g.terrain.height(x,z)+1;
        if(Math.abs(x)>g.radius-24||Math.abs(z)>g.radius-24||y<=g.terrain.sea()+1)return null;
        return new Site(x,y,z,(int)((hash>>>48)%5));
    }
    public static Site near(PlanetGenerator g,int x,int z){return site(g,Math.floorDiv(x,512),Math.floorDiv(z,512));}
    public static BlockState piece(Site s,int x,int y,int z){
        int dx=x-s.x,dz=z-s.z,dy=y-s.y;int ax=Math.abs(dx),az=Math.abs(dz);
        if(ax>10||az>10||dy< -1||dy>14)return null;
        if(dy==-1&&ax<=8&&az<=8)return Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        if(dx==0&&dz==0&&dy==0)return Blocks.LODESTONE.defaultBlockState(); // persistent population marker
        return switch(s.kind){
            case 0 -> { // broken courtyard, irregular columns and an incomplete lintel
                if(ax==6&&az==6&&dy>=0&&dy<3+Math.floorMod(dx*17+dz*31,5))yield Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
                if(dy==0&&(ax==7||az==7)&&ax<=7&&az<=7)yield Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
                if(dy==7&&dz==-6&&dx>=-6&&dx<=1)yield Blocks.CRACKED_STONE_BRICKS.defaultBlockState();yield null;
            }
            case 1 -> { // grounded hull, sloping bow, fractured canopy and exposed engine
                if(ax>=4&&ax<=6&&dz>=1&&dz<=4&&dy==0)yield Blocks.WAXED_CUT_COPPER.defaultBlockState();
                if(dz>=-7&&dz<=7&&ax<=Math.max(1,4-Math.max(0,-dz-3)/2)&&dy>=0&&dy<=3){
                    if(dz==7&&ax<=2&&dy<=1)yield Blocks.SEA_LANTERN.defaultBlockState();
                    if(dy==3&&dz<1&&dz>-4&&ax<=2)yield Blocks.CYAN_STAINED_GLASS.defaultBlockState();
                    if(ax==3||dy==0||dy==3||az==7)yield (dx+dz==2?Blocks.COPPER_BLOCK:Blocks.IRON_BLOCK).defaultBlockState();
                }yield null;
            }
            case 2 -> { // asymmetric luminous cluster
                double r=dx*dx+dz*dz;int top=14-(int)Math.sqrt(r)*2;
                if((dx-6)*(dx-6)+(dz-3)*(dz-3)<5&&dy>=0&&dy<=8-ax%3)yield Blocks.AMETHYST_BLOCK.defaultBlockState();
                if((dx+5)*(dx+5)+(dz+5)*(dz+5)<4&&dy>=0&&dy<6)yield Blocks.SEA_LANTERN.defaultBlockState();
                if(r<30&&dy>=0&&dy<=top)yield (dy==top?Blocks.SEA_LANTERN:dy%4==0?Blocks.BUDDING_AMETHYST:Blocks.AMETHYST_BLOCK).defaultBlockState();yield null;
            }
            case 3 -> { // quiet master's open shelter
                if(dx==-3&&dz==-3&&dy==1)yield Blocks.LANTERN.defaultBlockState();
                if(dx==3&&dz==-3&&dy==0)yield Blocks.BARREL.defaultBlockState();
                if(ax==4&&az==4&&dy>=0&&dy<5)yield Blocks.STRIPPED_OAK_LOG.defaultBlockState();
                if(dy==5&&ax<=5&&az<=5)yield Blocks.DARK_OAK_SLAB.defaultBlockState();
                if(dy==0&&ax<=3&&az<=3)yield Blocks.SMOOTH_SANDSTONE.defaultBlockState();yield null;
            }
            default -> { // abandoned outpost with accessible doorway and windows
                if(dx==4&&dz==-3&&dy>5&&dy<9)yield (dy==8?Blocks.LIGHTNING_ROD:Blocks.IRON_BARS).defaultBlockState();
                if(dy==6&&dx>=-4&&dx<=0&&dz>=-3&&dz<=2)yield Blocks.BLACK_STAINED_GLASS.defaultBlockState();
                if(dy==0&&dz==-3&&(dx==-4||dx==-3))yield Blocks.BARREL.defaultBlockState();
                if(dy==1&&dz==-4&&dx==3)yield Blocks.SEA_LANTERN.defaultBlockState();
                if(ax<=6&&az<=5&&dy>=0&&dy<=5){
                    if(dy==5)yield Blocks.SMOOTH_STONE_SLAB.defaultBlockState();
                    if((ax==6||az==5)&&!(dz==5&&ax<=1&&dy<3))yield (dy==2&&ax<5?Blocks.CYAN_STAINED_GLASS:Blocks.STONE_BRICKS).defaultBlockState();
                }yield null;
            }
        };
    }
    /** A series world's place on a round plaza: the ground filled up to it and cleared above it, the place built on it (only in this chunk). */
    private static void iconic(PlanetGenerator g,ChunkAccess chunk,Site s){
        int minX=chunk.getPos().getMinBlockX(),minZ=chunk.getPos().getMinBlockZ(),r=IconicLandmarks.RADIUS;
        var pos=new BlockPos.MutableBlockPos();var floor=chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);var surface=chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        for(int x=minX;x<minX+16;x++)for(int z=minZ;z<minZ+16;z++){int dx=x-s.x,dz=z-s.z;double d=Math.sqrt(dx*dx+dz*dz);if(d>r)continue;
            int ground=g.terrain.height(x,z),bottom=Math.min(s.y-2,ground-1),top=Math.max(s.y+34,ground+16);
            for(int y=bottom;y<=top;y++){BlockState state;int dy=y-s.y;
                if(dy<-1)state=y<=ground?null:g.terrain.top();                 // fill hollows up to the plaza
                else if(dy==-1)state=IconicLandmarks.paving(g.ref.system(),dx,dz);
                else{state=IconicLandmarks.piece(g.ref.system(),dx,dy,dz);if(state==null)state=Blocks.AIR.defaultBlockState();}
                if(state==null)continue;
                pos.set(x,y,z);chunk.setBlockState(pos,state,false);floor.update(x&15,y,z&15,state);surface.update(x&15,y,z&15,state);
                if(!state.getFluidState().isEmpty())chunk.markPosForPostprocessing(pos);}
        }
    }
    public static void decorate(PlanetGenerator g,ChunkAccess chunk){
        int minX=chunk.getPos().getMinBlockX(),minZ=chunk.getPos().getMinBlockZ();
        Site origin=g.ref.planet()==0&&IconicLandmarks.has(g.ref.system())?site(g,0,0):null;
        if(origin!=null&&origin.kind==9&&origin.x>=minX-IconicLandmarks.RADIUS&&origin.x<=minX+15+IconicLandmarks.RADIUS&&origin.z>=minZ-IconicLandmarks.RADIUS&&origin.z<=minZ+15+IconicLandmarks.RADIUS){iconic(g,chunk,origin);return;}
        Site s=near(g,minX+8,minZ+8);if(s==null||s.kind==9||s.x<minX-10||s.x>minX+25||s.z<minZ-10||s.z>minZ+25)return;
        var pos=new BlockPos.MutableBlockPos();var floor=chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);var surface=chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        int bottom=com.dbzenith.galaxy.IconicSystems.isNew(g.ref.system())?Math.min(s.y-12,g.terrain.height(s.x,s.z)-48):s.y-12;
        for(int x=minX;x<minX+16;x++)for(int z=minZ;z<minZ+16;z++)for(int y=bottom;y<=s.y+14;y++){
            var state=piece(s,x,y,z);
            if(Math.abs(x-s.x)<=8&&Math.abs(z-s.z)<=8){
                if(y<s.y-1&&y>=g.terrain.height(x,z))state=Blocks.DEEPSLATE_BRICKS.defaultBlockState();
                else if(state==null&&y>=s.y)state=Blocks.AIR.defaultBlockState();
            }
            if(state==null)continue;
            if(g.ref.system().equals(com.dbzenith.galaxy.IconicSystems.VEGETA)&&s.kind==0&&z==s.z-6&&(x==s.x-6||x==s.x+1)&&y>=s.y&&y<s.y+7)state=Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
            if(g.ref.system().equals(com.dbzenith.galaxy.IconicSystems.FRIEZA)){
                if(state.is(Blocks.STONE_BRICKS))state=Blocks.IRON_BLOCK.defaultBlockState();
                else if(state.is(Blocks.CYAN_STAINED_GLASS))state=Blocks.RED_STAINED_GLASS.defaultBlockState();
            }
            pos.set(x,y,z);chunk.setBlockState(pos,state,false);floor.update(x&15,y,z&15,state);surface.update(x&15,y,z&15,state);
        }
    }
}
