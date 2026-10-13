package com.dbzenith.nimbus;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

public final class NimbusNetwork {
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation("dbzenith","nimbus"),()->"1","1"::equals,"1"::equals);
    public record Control(float forward,float strafe,boolean rise,boolean boost,boolean toggle) {}
    public static void register() {
        CHANNEL.messageBuilder(Control.class,0,NetworkDirection.PLAY_TO_SERVER)
            .encoder((m,b)-> { b.writeFloat(m.forward);b.writeFloat(m.strafe);b.writeBoolean(m.rise);b.writeBoolean(m.boost);b.writeBoolean(m.toggle); })
            .decoder(b->new Control(b.readFloat(),b.readFloat(),b.readBoolean(),b.readBoolean(),b.readBoolean()))
            .consumerMainThread((m,ctx)-> { var p=ctx.get().getSender();if(p!=null&&p.getVehicle() instanceof NimbusEntity n) n.controls(p,m.forward,m.strafe,m.rise,m.boost,m.toggle); }).add();
    }
}
