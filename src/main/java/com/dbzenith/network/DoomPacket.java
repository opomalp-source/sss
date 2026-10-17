package com.dbzenith.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.*;
import java.util.function.Supplier;

/** Only the current system's changed bodies; no terrain seeds, snapshots or block journals are sent. */
public record DoomPacket(String dimension,String system,List<Body> bodies) {
    public record Body(int index,int phase,int elapsed,int duration,int age,String name){}
    public static void encode(DoomPacket p,FriendlyByteBuf b){
        b.writeUtf(p.dimension,256);b.writeUtf(p.system,48);b.writeVarInt(p.bodies.size());
        for(var e:p.bodies){b.writeVarInt(e.index);b.writeByte(e.phase);b.writeVarInt(e.elapsed);b.writeVarInt(e.duration);b.writeVarInt(e.age);b.writeUtf(e.name,64);}
    }
    public static DoomPacket decode(FriendlyByteBuf b){
        String dim=b.readUtf(256),sys=b.readUtf(48);int count=b.readVarInt();if(count<0||count>12)throw new IllegalArgumentException("Planet event count");
        var list=new ArrayList<Body>();for(int i=0;i<count;i++){int index=b.readVarInt(),phase=b.readUnsignedByte(),elapsed=b.readVarInt(),duration=b.readVarInt(),age=b.readVarInt();
            if(index<0||index>11||phase<1||phase>4||duration<600||duration>1800||elapsed<0||elapsed>duration||age<0||age>1201)throw new IllegalArgumentException("Planet event bounds");
            list.add(new Body(index,phase,elapsed,duration,age,b.readUtf(64)));
        }return new DoomPacket(dim,sys,List.copyOf(list));
    }
    public static void handle(DoomPacket p,Supplier<NetworkEvent.Context> c){com.dbzenith.client.space.DoomClient.receive(p);}
}
