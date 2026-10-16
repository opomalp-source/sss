package com.dbzenith.network;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
/** Bounded motion hint only; the server owns altitude, destination, preload and transfer. */
public record SpaceVelocityPacket(float x,float y,float z){
    public static void encode(SpaceVelocityPacket m,FriendlyByteBuf b){b.writeFloat(m.x);b.writeFloat(m.y);b.writeFloat(m.z);}
    public static SpaceVelocityPacket decode(FriendlyByteBuf b){return new SpaceVelocityPacket(b.readFloat(),b.readFloat(),b.readFloat());}
    public static void handle(SpaceVelocityPacket m,Supplier<NetworkEvent.Context> c){var p=c.get().getSender();if(p!=null&&InputGuard.allow(p,InputGuard.Kind.SPACE))com.dbzenith.galaxy.space.SpaceRuntime.velocityHint(p,new net.minecraft.world.phys.Vec3(m.x,m.y,m.z));}
}
