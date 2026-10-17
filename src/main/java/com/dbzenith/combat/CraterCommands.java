package com.dbzenith.combat;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/** Operator inspection uses the same protection, queue and material path as actual ki attacks. */
public final class CraterCommands {
    public static LiteralArgumentBuilder<CommandSourceStack> branch(){
        return Commands.literal("crater")
            .then(Commands.literal("status").executes(c->{c.getSource().sendSuccess(()->Component.literal(Destruction.work().toString()),false);return 1;}))
            .then(Commands.literal("hit").then(Commands.argument("players",EntityArgument.players())
                .then(Commands.argument("force",DoubleArgumentType.doubleArg(.35,8)).executes(c->{
                    int accepted=0;
                    for(var p:EntityArgument.getPlayers(c,"players")){
                        Vec3 eye=p.getEyePosition();var hit=p.level().clip(new ClipContext(eye,eye.add(p.getLookAngle().scale(64)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
                        if(hit.getType()!=HitResult.Type.BLOCK)continue;
                        accepted+=Destruction.ki(p.serverLevel(),hit.getLocation(),Vec3.atLowerCornerOf(hit.getDirection().getNormal()),DoubleArgumentType.getDouble(c,"force"),p)>0?1:0;
                    }
                    int n=accepted;c.getSource().sendSuccess(()->Component.literal("Queued "+n+" crater(s)."),false);return n;
                }))));
    }
}
