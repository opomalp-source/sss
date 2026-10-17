package com.dbzenith.galaxy.planet;

import com.dbzenith.galaxy.*;
import com.dbzenith.galaxy.space.*;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import java.util.UUID;

/** Operator tools share normal protection and event caps; no destructive bypass. */
public final class DoomCommands {
    public static LiteralArgumentBuilder<CommandSourceStack> branch(){
        var branch=Commands.literal("planet").requires(s->s.hasPermission(2));
        for(String action:new String[]{"destroy","restore","status","protect","unprotect"})branch.then(Commands.literal(action).then(Commands.argument("ref",StringArgumentType.string()).executes(c->{
            try{
                var ref=GalaxyCatalog.Ref.parse(StringArgumentType.getString(c,"ref"));var s=c.getSource().getServer();var data=GalaxyData.get(s);
                var body=GalaxyCatalog.body(s.overworld().getSeed(),data.salt(),ref);boolean ok=true;String message;
                switch(action){
                    case "destroy" -> {var p=c.getSource().getPlayer();ok=PlanetDestruction.begin(s,ref,p==null?new UUID(0,0):p.getUUID(),p==null?"Server":p.getGameProfile().getName());message=ok?"Core instability started. Evacuate!":PlanetDestruction.protection(s,ref);if(message.isEmpty())message="Destruction refused: disabled, already active/destroyed, non-landable or event cap reached.";}
                    case "restore" -> {ok=PlanetDestruction.restore(s,ref);message=ok?"Restoring the sealed surface and fissure journal.":"No destroyed planet at that address.";}
                    case "protect","unprotect" -> {data.discover(ref,new UUID(0,0),"Server",s.overworld().getGameTime(),body.name());data.base(ref,action.equals("protect"));message=action.equals("protect")?"Planet base protection enabled.":"Planet base protection cleared; configured/spawn protections still apply.";}
                    default -> {var e=DoomData.get(s).events.get(ref);message=data.name(ref,body.name())+" | "+(e==null?"Intact":"phase="+e.phase+" remaining="+Math.max(0,e.duration-e.elapsed)/20d+"s scars="+e.scars.size())+" | "+PlanetDestruction.protection(s,ref);}
                }
                final String text=message;c.getSource().sendSuccess(()->Component.literal(text),false);return ok?1:0;
            }catch(IllegalArgumentException ex){c.getSource().sendFailure(Component.literal(ex.getMessage()));return 0;}
        })));
        branch.then(Commands.literal("remnant").then(Commands.argument("players",EntityArgument.players()).then(Commands.argument("planet",IntegerArgumentType.integer(0,11)).executes(c->{
            int n=0;for(var p:EntityArgument.getPlayers(c,"players")){if(!SpaceRuntime.isSpace(p.level()))continue;var sys=SpaceRuntime.system(p.serverLevel());int i=IntegerArgumentType.getInteger(c,"planet");if(i>=sys.planets().size())continue;
                var ref=new GalaxyCatalog.Ref(sys.id(),i);if(!GalaxyData.get(p.server).destroyed(ref))continue;var f=Remnants.fragments(ref,sys.planets().get(i)).get(0);var at=f.center().add(0,f.radius()+5,20);p.teleportTo(p.serverLevel(),at.x,at.y,at.z,180,30);n++;
            }return n;
        }))));return branch;
    }
}
