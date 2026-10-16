package com.dbzenith.galaxy;

import com.dbzenith.network.ModNetwork;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.*;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import java.util.Arrays;

public final class GalaxyCommands {
    private GalaxyCommands() {}
    /** Public /galaxy. /dbz galaxy retains the existing admin command's permission. */
    public static void publicCommand(com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("galaxy").executes(c->{GalaxyService.open(c.getSource().getPlayerOrException());return 1;}).then(nameCommand()).then(Commands.literal("home").executes(c->com.dbzenith.galaxy.planet.PlanetRuntime.home(c.getSource().getPlayerOrException())?1:0)));
    }
    public static LiteralArgumentBuilder<CommandSourceStack> branch() {
        return Commands.literal("galaxy").executes(c->{GalaxyService.open(c.getSource().getPlayerOrException());return 1;})
            .then(Commands.literal("map").then(Commands.argument("players",EntityArgument.players()).executes(c->{
                for(var p:EntityArgument.getPlayers(c,"players"))GalaxyService.open(p);return 1;
            })))
            .then(Commands.literal("system").then(Commands.argument("id",StringArgumentType.string()).executes(c->{
                try {
                    var id=GalaxyCatalog.Id.parse(StringArgumentType.getString(c,"id"));var data=GalaxyData.get(c.getSource().getServer());
                    var s=GalaxyCatalog.system(c.getSource().getServer().overworld().getSeed(),data.salt(),id);
                    c.getSource().sendSuccess(()->Component.literal(s.name()+" ["+id+"] | "+s.stars().size()+" star(s), "+s.planets().size()+" planets"),false);
                    for(var b:s.planets())c.getSource().sendSuccess(()->Component.literal(b.index()+": "+b.name()+" | "+GalaxyCatalog.label(b.kind())+" | "+b.gravity()+"g | "+GalaxyCatalog.label(b.atmosphere())),false);
                    return 1;
                } catch(IllegalArgumentException ex) { c.getSource().sendFailure(Component.literal(ex.getMessage()));return 0; }
            })))
            .then(Commands.literal("discover").requires(s->s.hasPermission(2)).then(Commands.argument("id",StringArgumentType.string())
                .then(Commands.argument("planet",IntegerArgumentType.integer(0,11)).executes(c->{
                    var p=c.getSource().getPlayerOrException();
                    try {
                        var ref=new GalaxyCatalog.Ref(GalaxyCatalog.Id.parse(StringArgumentType.getString(c,"id")),IntegerArgumentType.getInteger(c,"planet"));
                        var data=GalaxyData.get(p.server);GalaxyCatalog.body(p.server.overworld().getSeed(),data.salt(),ref);
                        boolean fresh=data.discover(ref,p.getUUID(),p.getGameProfile().getName(),p.server.overworld().getGameTime(),GalaxyCatalog.body(p.server.overworld().getSeed(),data.salt(),ref).name());
                        c.getSource().sendSuccess(()->Component.literal(fresh?"Discovery recorded (admin survey).":"Already discovered."),false);return 1;
                    } catch(IllegalArgumentException ex){c.getSource().sendFailure(Component.literal(ex.getMessage()));return 0;}
                }))))
            .then(Commands.literal("sample").then(Commands.argument("kind",StringArgumentType.word())
                .suggests((c,b)->SharedSuggestionProvider.suggest(Arrays.stream(GalaxyCatalog.Kind.values()).map(k->k.name().toLowerCase(java.util.Locale.ROOT)),b))
                .then(Commands.argument("players",EntityArgument.players()).executes(c->{
                    try{var kind=GalaxyCatalog.Kind.valueOf(StringArgumentType.getString(c,"kind").toUpperCase(java.util.Locale.ROOT));
                        var ref=com.dbzenith.galaxy.planet.PlanetRuntime.sample(c.getSource().getServer(),kind);
                        for(var p:EntityArgument.getPlayers(c,"players"))com.dbzenith.galaxy.planet.PlanetRuntime.travel(p,ref);return 1;
                    }catch(IllegalArgumentException ex){c.getSource().sendFailure(Component.literal(ex.getMessage()));return 0;}
                }))))
            .then(Commands.literal("land").then(Commands.argument("players",EntityArgument.players()).then(Commands.argument("ref",StringArgumentType.string()).executes(c->{
                try{var ref=GalaxyCatalog.Ref.parse(StringArgumentType.getString(c,"ref"));for(var p:EntityArgument.getPlayers(c,"players"))com.dbzenith.galaxy.planet.PlanetRuntime.travel(p,ref);return 1;}
                catch(IllegalArgumentException ex){c.getSource().sendFailure(Component.literal(ex.getMessage()));return 0;}
            }))))
            .then(Commands.literal("unload").then(Commands.argument("ref",StringArgumentType.string()).executes(c->{
                try{var ref=GalaxyCatalog.Ref.parse(StringArgumentType.getString(c,"ref"));var level=c.getSource().getServer().getLevel(com.dbzenith.galaxy.planet.PlanetRuntime.key(ref));
                    boolean ok=level!=null&&com.dbzenith.galaxy.planet.PlanetRuntime.unload(level);c.getSource().sendSuccess(()->Component.literal(ok?"Empty planet scheduled to save and unload.":"Planet is occupied, pinned or not loaded."),false);return ok?1:0;
                }catch(IllegalArgumentException ex){return 0;}
            })))
            .then(nameCommand());
    }
    private static LiteralArgumentBuilder<CommandSourceStack> nameCommand() {
        return Commands.literal("name").then(Commands.argument("ref",StringArgumentType.string()).then(Commands.argument("name",StringArgumentType.greedyString()).executes(c->{
                var p=c.getSource().getPlayerOrException();
                try {
                    var ref=GalaxyCatalog.Ref.parse(StringArgumentType.getString(c,"ref"));
                    boolean ok=GalaxyData.get(p.server).rename(ref,p.getUUID(),StringArgumentType.getString(c,"name"));
                    if(!ok)c.getSource().sendFailure(Component.literal("Only the first discoverer can name this planet. Use 2-32 letters, numbers, spaces, apostrophes, dots or hyphens."));
                    else c.getSource().sendSuccess(()->Component.literal("Planet named."),false);return ok?1:0;
                } catch(IllegalArgumentException ex){c.getSource().sendFailure(Component.literal(ex.getMessage()));return 0;}
            })));
    }
}
