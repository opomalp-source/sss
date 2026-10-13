package com.dbzenith.gametest;
import com.dbzenith.social.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder("socialqa") @PrefixGameTestTemplate(false)
public class SocialTests {
    @GameTest(template="empty",timeoutTicks=100)
    public static void partyInvitesAndPermissions(GameTestHelper h){
        var a=TestPlayers.create(h);var b=TestPlayers.create(h);var outsider=TestPlayers.create(h);var d=SocialData.get(a.server);
        SocialService.act(a,"create",false,"");var group=d.membership(a.getUUID(),false);h.assertTrue(group!=null,"party created");
        SocialService.act(b,"join",false,group.id.toString());h.assertTrue(d.membership(b.getUUID(),false)==null,"party requires invitation");
        SocialService.act(a,"invite",false,b.getGameProfile().getName());SocialService.act(b,"accept",false,group.id.toString());h.assertTrue(group.members.containsKey(b.getUUID()),"invited player joins");
        SocialService.act(b,"kick",false,a.getUUID().toString());SocialService.act(outsider,"disband",false,"");h.assertTrue(group.members.size()==2,"nonleaders cannot kick or disband");
        SocialService.act(a,"leave",false,"");h.assertTrue(group.owner.equals(b.getUUID()),"leadership transfers on leave");SocialService.act(b,"disband",false,"");h.assertTrue(!d.groups.containsKey(group.id),"leader disbands");
        TestPlayers.remove(h,a);TestPlayers.remove(h,b);TestPlayers.remove(h,outsider);h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void clansAccessAndPersistence(GameTestHelper h){
        var a=TestPlayers.create(h);var b=TestPlayers.create(h);var d=SocialData.get(a.server);String name="Clan "+UUID.randomUUID().toString().substring(0,8);
        SocialService.act(a,"create",true,name);var group=d.membership(a.getUUID(),true);h.assertTrue(group.open,"clan starts open");
        SocialService.act(b,"create",true,name.toLowerCase(Locale.ROOT));h.assertTrue(d.membership(b.getUUID(),true)==null,"duplicate names rejected ignoring case");
        SocialService.act(b,"join",true,group.id.toString());h.assertTrue(group.members.size()==2,"open joining works");
        SocialService.act(b,"access",true,"");h.assertTrue(group.open,"member cannot change access");SocialService.act(b,"leave",true,"");SocialService.act(a,"access",true,"");
        SocialService.act(b,"join",true,group.id.toString());h.assertTrue(d.membership(b.getUUID(),true)==null,"private joining denied");
        SocialService.act(a,"invite",true,b.getGameProfile().getName());SocialService.act(b,"accept",true,group.id.toString());
        var restored=SocialData.load(d.save(new CompoundTag()));var copy=restored.membership(b.getUUID(),true);h.assertTrue(copy!=null&&!copy.open&&copy.name.equals(name)&&copy.owner.equals(a.getUUID()),"membership and access survive save/load");
        h.assertTrue(b.getDisplayName().getString().contains("["+name+"]"),"clan appended to display name");
        com.dbzenith.data.ModCapabilities.getOrThrow(b).setTitle("hero");b.refreshDisplayName();b.refreshTabListName();
        h.assertTrue(b.getTabListDisplayName()!=null&&b.getTabListDisplayName().getString().contains("["+name+"]"),"clan suffix survives title formatting in player list");
        SocialService.act(a,"kick",true,b.getUUID().toString());h.assertTrue(d.membership(b.getUUID(),true)==null&&!b.getDisplayName().getString().contains(name),"kick clears membership and suffix");
        SocialService.act(a,"disband",true,"");TestPlayers.remove(h,a);TestPlayers.remove(h,b);h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void staleInvitesAndValidation(GameTestHelper h){
        var a=TestPlayers.create(h);var b=TestPlayers.create(h);var d=SocialData.get(a.server);
        SocialService.act(a,"create",true,"bad\u00a7name");h.assertTrue(d.membership(a.getUUID(),true)==null,"formatting characters rejected");
        SocialService.act(a,"create",false,"");var group=d.membership(a.getUUID(),false);
        d.invites.add(new SocialData.Invite(group.id,b.getUUID(),-1));SocialService.act(b,"accept",false,group.id.toString());h.assertTrue(d.membership(b.getUUID(),false)==null,"expired invite cannot be used");
        SocialService.act(a,"invite",false,b.getGameProfile().getName());SocialService.act(b,"decline",false,group.id.toString());SocialService.act(b,"accept",false,group.id.toString());h.assertTrue(d.membership(b.getUUID(),false)==null,"declined invite cannot be reused");
        SocialService.act(a,"disband",false,"");TestPlayers.remove(h,a);TestPlayers.remove(h,b);h.succeed();
    }
}
