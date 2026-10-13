package com.dbzenith.social;

import com.dbzenith.DBZenith;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod.EventBusSubscriber(modid=DBZenith.MOD_ID)
public final class SocialService {
    public static String act(ServerPlayer p,String action,boolean clan,String value) {
        SocialData d=SocialData.get(p.server);UUID who=p.getUUID();var own=d.membership(who,clan);long now=p.server.overworld().getGameTime();
        d.invites.removeIf(i->i.expires()<now||!d.groups.containsKey(i.group()));
        String status;
        switch(action) {
            case "create" -> {
                if(own!=null)return "Leave your current group first.";
                String name=clan?value.trim():p.getGameProfile().getName()+" Party";
                if(!name.matches("[A-Za-z0-9 _-]{3,24}"))return "Use 3-24 letters, numbers, spaces, _ or -.";
                if(d.groups.size()>=512)return "This world has reached its group limit.";
                if(clan&&d.groups.values().stream().anyMatch(g->g.clan&&g.name.equalsIgnoreCase(name)))return "That clan name is already taken.";
                var g=new SocialData.Group();g.id=UUID.randomUUID();g.owner=who;g.clan=clan;g.open=clan;g.name=name;g.members.put(who,p.getGameProfile().getName());d.groups.put(g.id,g);status="Created "+name+".";
            }
            case "invite" -> {
                if(own==null||!own.owner.equals(who))return "Only the leader can invite players.";
                ServerPlayer target=p.server.getPlayerList().getPlayerByName(value);
                if(target==null)return "That player is not online.";
                if(d.membership(target.getUUID(),clan)!=null)return "That player already belongs to a "+(clan?"clan.":"party.");
                if(own.members.size()>=own.capacity())return "Your group is full.";
                if(d.invites.stream().filter(i->i.target().equals(target.getUUID())).count()>=12)return "That player has too many pending invitations.";
                d.invites.removeIf(i->i.group().equals(own.id)&&i.target().equals(target.getUUID()));
                d.invites.add(new SocialData.Invite(own.id,target.getUUID(),now+2400));
                target.sendSystemMessage(Component.literal(p.getGameProfile().getName()+" invited you to "+own.name+". Open the "+(clan?"Clans":"Party")+" tab to accept."));status="Invitation sent to "+target.getGameProfile().getName()+".";
            }
            case "join", "accept" -> {
                if(own!=null)return "Leave your current group first.";
                var group=find(d,value);if(group==null||group.clan!=clan)return "That group no longer exists.";
                boolean invited=d.invites.stream().anyMatch(i->i.group().equals(group.id)&&i.target().equals(who));
                if(!(clan&&group.open)&&!invited)return "This group requires an invitation.";
                if(group.members.size()>=group.capacity())return "That group is full.";
                group.members.put(who,p.getGameProfile().getName());d.invites.removeIf(i->i.target().equals(who)&&d.groups.get(i.group()).clan==clan);status="Joined "+group.name+".";
            }
            case "decline" -> {
                boolean removed=d.invites.removeIf(i->i.target().equals(who)&&i.group().toString().equals(value));if(!removed)return "Invitation expired.";status="Invitation declined.";
            }
            case "leave" -> {
                if(own==null)return "You are not in a group.";
                own.members.remove(who);
                if(own.members.isEmpty())remove(d,own.id);else if(own.owner.equals(who))own.owner=own.members.keySet().iterator().next();status="You left "+own.name+".";
            }
            case "disband" -> {
                if(own==null||!own.owner.equals(who))return "Only the leader can disband the group.";remove(d,own.id);status="Group disbanded.";
            }
            case "kick", "transfer" -> {
                if(own==null||!own.owner.equals(who))return "Only the leader can manage members.";
                UUID target;try{target=UUID.fromString(value);}catch(IllegalArgumentException e){return "Invalid member.";}
                if(target.equals(who)||!own.members.containsKey(target))return "Choose another group member.";
                if(action.equals("kick")) { own.members.remove(target);status="Member removed."; }else{own.owner=target;status="Leadership transferred.";}
            }
            case "access" -> {
                if(own==null||!clan||!own.owner.equals(who))return "Only the clan leader can change access.";
                own.open=!own.open;status=own.open?"Clan is open to join.":"Clan is invitation-only.";
            }
            default -> { return "Unknown group action."; }
        }
        d.setDirty();refresh(p.server);return status;
    }
    private static SocialData.Group find(SocialData d,String id) { try{return d.groups.get(UUID.fromString(id));}catch(IllegalArgumentException e){return null;} }
    private static void remove(SocialData d,UUID id) { d.groups.remove(id);d.invites.removeIf(i->i.group().equals(id)); }
    public static CompoundTag snapshot(ServerPlayer p,String message) {
        SocialData d=SocialData.get(p.server);CompoundTag tag=new CompoundTag();tag.putString("Message",message);
        for(boolean clan:new boolean[]{false,true}) { var own=d.membership(p.getUUID(),clan);if(own!=null)tag.put(clan?"Clan":"Party",SocialData.groupTag(own)); }
        ListTag directory=new ListTag(),invites=new ListTag(),players=new ListTag();long now=p.server.overworld().getGameTime();
        for(var g:d.groups.values())if(g.clan){CompoundTag row=new CompoundTag();row.putUUID("Id",g.id);row.putString("Name",g.name);row.putBoolean("Open",g.open);row.putInt("Count",g.members.size());directory.add(row);}
        for(var i:d.invites)if(i.target().equals(p.getUUID())&&i.expires()>=now){var g=d.groups.get(i.group());if(g!=null){CompoundTag row=new CompoundTag();row.putUUID("Id",g.id);row.putString("Name",g.name);row.putBoolean("Clan",g.clan);invites.add(row);}}
        for(var online:p.server.getPlayerList().getPlayers()){CompoundTag row=new CompoundTag();row.putUUID("Id",online.getUUID());row.putString("Name",online.getGameProfile().getName());var g=d.membership(online.getUUID(),true);row.putString("Clan",g==null?"":g.name);players.add(row);}
        tag.put("Directory",directory);tag.put("Invites",invites);tag.put("Players",players);return tag;
    }
    public static void refresh(MinecraftServer server) { for(var p:server.getPlayerList().getPlayers()){p.refreshDisplayName();p.refreshTabListName();SocialNetwork.send(p,"");} }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if(e.getEntity() instanceof ServerPlayer p){var d=SocialData.get(p.server);for(var g:d.groups.values())if(g.members.containsKey(p.getUUID())){g.members.put(p.getUUID(),p.getGameProfile().getName());d.setDirty();}refresh(p.server);}
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST) public static void name(PlayerEvent.NameFormat e) {
        String clan="";
        if(e.getEntity() instanceof ServerPlayer p){var g=SocialData.get(p.server).membership(p.getUUID(),true);if(g!=null)clan=g.name;}
        else clan=SocialState.clanNames.getOrDefault(e.getEntity().getUUID(),"");
        if(!clan.isEmpty())e.setDisplayname(e.getDisplayname().copy().append(Component.literal(" ["+clan+"]").withStyle(ChatFormatting.GOLD)));
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST) public static void tabName(PlayerEvent.TabListNameFormat e) {
        if(e.getEntity() instanceof ServerPlayer p){var g=SocialData.get(p.server).membership(p.getUUID(),true);if(g!=null){var base=e.getDisplayName();e.setDisplayName((base==null?Component.literal(p.getGameProfile().getName()):base.copy()).append(Component.literal(" ["+g.name+"]").withStyle(ChatFormatting.GOLD)));}}
    }
}
