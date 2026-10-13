package com.dbzenith.social;

import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** World-owned UUID membership; never trusts names supplied by a client. */
public final class SocialData extends SavedData {
    public static final int PARTY_LIMIT=8,CLAN_LIMIT=32;
    public static final class Group {
        public UUID id,owner;public String name;public boolean clan,open;
        public final LinkedHashMap<UUID,String> members=new LinkedHashMap<>();
        public int capacity() { return clan?CLAN_LIMIT:PARTY_LIMIT; }
    }
    public record Invite(UUID group,UUID target,long expires) {}
    public final LinkedHashMap<UUID,Group> groups=new LinkedHashMap<>();
    public final List<Invite> invites=new ArrayList<>();
    public static SocialData get(MinecraftServer server) { return server.overworld().getDataStorage().computeIfAbsent(SocialData::load,SocialData::new,"dbzenith_social"); }
    public Group membership(UUID member,boolean clan) { for(Group g:groups.values()) if(g.clan==clan&&g.members.containsKey(member)) return g;return null; }
    public static SocialData load(CompoundTag tag) {
        SocialData data=new SocialData();
        for(Tag value:tag.getList("Groups",Tag.TAG_COMPOUND)) {
            CompoundTag t=(CompoundTag)value;if(!t.hasUUID("Id")||!t.hasUUID("Owner"))continue;
            Group g=new Group();g.id=t.getUUID("Id");g.owner=t.getUUID("Owner");g.name=t.getString("Name");g.clan=t.getBoolean("Clan");g.open=t.getBoolean("Open");
            for(Tag m:t.getList("Members",Tag.TAG_COMPOUND)) { CompoundTag row=(CompoundTag)m;if(row.hasUUID("Id"))g.members.put(row.getUUID("Id"),row.getString("Name")); }
            if(!g.members.isEmpty()) { if(!g.members.containsKey(g.owner))g.owner=g.members.keySet().iterator().next();data.groups.put(g.id,g); }
        }
        return data;
    }
    public static CompoundTag groupTag(Group g) {
        CompoundTag t=new CompoundTag();t.putUUID("Id",g.id);t.putUUID("Owner",g.owner);t.putString("Name",g.name);t.putBoolean("Clan",g.clan);t.putBoolean("Open",g.open);
        ListTag members=new ListTag();g.members.forEach((id,name)-> { CompoundTag m=new CompoundTag();m.putUUID("Id",id);m.putString("Name",name);members.add(m); });t.put("Members",members);return t;
    }
    @Override public CompoundTag save(CompoundTag tag) { ListTag rows=new ListTag();groups.values().forEach(g->rows.add(groupTag(g)));tag.put("Groups",rows);return tag; }
}
