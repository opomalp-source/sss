package com.dbzenith.social;
import com.dbzenith.DBZenith;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;

@Mod.EventBusSubscriber(modid=DBZenith.MOD_ID,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class SocialNetwork {
    public record Action(String action,boolean clan,String value) {}
    public record State(CompoundTag tag) {}
    private static final Map<ServerPlayer,Long> LAST=new WeakHashMap<>();
    public static final SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation("dbzenith","social"),()->"1","1"::equals,"1"::equals);
    @SubscribeEvent public static void setup(FMLCommonSetupEvent e){e.enqueueWork(SocialNetwork::register);}
    private static void register(){
        CHANNEL.messageBuilder(Action.class,0,NetworkDirection.PLAY_TO_SERVER)
            .encoder((m,b)->{b.writeUtf(m.action,16);b.writeBoolean(m.clan);b.writeUtf(m.value,64);})
            .decoder(b->new Action(b.readUtf(16),b.readBoolean(),b.readUtf(64)))
            .consumerMainThread((m,c)->{var p=c.get().getSender();if(p==null)return;long now=p.server.overworld().getGameTime();if(now-LAST.getOrDefault(p,-100L)<4)return;LAST.put(p,now);send(p,m.action.equals("refresh")?"":SocialService.act(p,m.action,m.clan,m.value));}).add();
        CHANNEL.messageBuilder(State.class,1,NetworkDirection.PLAY_TO_CLIENT)
            .encoder((m,b)->b.writeNbt(m.tag)).decoder(b->new State(b.readNbt()))
            .consumerMainThread((m,c)->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->SocialClient.receive(m.tag))).add();
    }
    public static void send(ServerPlayer p,String message){CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new State(SocialService.snapshot(p,message)));}
    public static void action(String action,boolean clan,String value){CHANNEL.sendToServer(new Action(action,clan,value));}
}
