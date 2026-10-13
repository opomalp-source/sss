package com.dbzenith.social;

import com.dbzenith.client.screen.MenuScreen;
import com.dbzenith.client.ui.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import java.util.*;

public final class SocialScreen extends MenuScreen {
    public final boolean clan;
    private EditBox input;
    private String draft="",selected="",message="";
    private int pageNumber,revision=-1,ticks;
    private boolean invitations;
    public SocialScreen(boolean clan){super(Component.literal(clan?"Clans":"Party"),null,null);this.clan=clan;SocialNetwork.action("refresh",clan,"");}
    private CompoundTag own(){return SocialState.snapshot.getCompound(clan?"Clan":"Party");}
    private boolean member(){return own().hasUUID("Id");}
    private boolean leader(){return minecraft.player!=null&&member()&&own().getUUID("Owner").equals(minecraft.player.getUUID());}
    private List<CompoundTag> rows(String key){List<CompoundTag> out=new ArrayList<>();for(Tag t:SocialState.snapshot.getList(key,Tag.TAG_COMPOUND))out.add((CompoundTag)t);return out;}
    private void action(String action,String value){SocialNetwork.action(action,clan,value);}
    private UiButton button(String label,int x,int y,int w,Runnable click){return addRenderableWidget(UiButton.of(UiButton.Style.CHIP,Component.literal(label),x,y,w,18,b->click.run()).textScale(.8f));}
    private void confirm(String action,String title,String detail){minecraft.setScreen(new ConfirmScreen(yes->{minecraft.setScreen(this);if(yes)action(action,"");},Component.literal(title),Component.literal(detail)));}
    private void textField(int x,int y,int w,String hint){input=new EditBox(font,x,y,w,18,Component.literal(hint));input.setMaxLength(24);input.setValue(draft);input.setHint(Component.literal(hint));input.setResponder(v->draft=v);addRenderableWidget(input);}
    @Override protected void initPage(){
        revision=SocialState.revision;String latest=SocialState.snapshot.getString("Message");if(!latest.isEmpty())message=latest;
        int x=cardX+12,y=cardY+12,w=cardW-24;
        if(member()){
            int split=x+w/2,left=w/2-8,right=w-w/2-8;
            var group=own();List<CompoundTag> members=new ArrayList<>();for(Tag t:group.getList("Members",Tag.TAG_COMPOUND))members.add((CompoundTag)t);
            int count=Math.max(1,(cardH-114)/23),pages=Math.max(1,(members.size()+count-1)/count);pageNumber=Math.min(pageNumber,pages-1);
            for(int i=pageNumber*count;i<Math.min(members.size(),(pageNumber+1)*count);i++){
                var row=members.get(i);String id=row.getUUID("Id").toString();boolean owner=row.getUUID("Id").equals(group.getUUID("Owner"));
                boolean online=rows("Players").stream().anyMatch(p->p.getUUID("Id").equals(row.getUUID("Id")));
                String name=(owner?"* ":"")+row.getString("Name")+(online?"":" (offline)");
                var b=button(font.plainSubstrByWidth(name,(int)(left/.8)-12),x,y+38+(i-pageNumber*count)*23,left,()->{selected=id;rebuildWidgets();});b.selected(selected.equals(id));
            }
            pager(x,y+38+count*23,left,pages);
            int bottom=cardY+cardH-53;
            button("Remove",x,bottom,left/2-2,()->action("kick",selected)).active=leader()&&!selected.isEmpty()&&!selected.equals(minecraft.player.getUUID().toString());
            button("Make leader",x+left/2+2,bottom,left/2-2,()->action("transfer",selected)).active=leader()&&!selected.isEmpty()&&!selected.equals(minecraft.player.getUUID().toString());
            textField(split+8,y+38,right,"Player username");
            button("Send invitation",split+8,y+61,right,()->action("invite",draft)).active=leader();
            if(clan)button(group.getBoolean("Open")?"Joining: Open":"Joining: Invite-only",split+8,y+90,right,()->action("access","")).active=leader();
            button("Leave "+(clan?"clan":"party"),split+8,bottom-24,right,()->confirm("leave","Leave this group?",leader()?"Leadership passes to the next member.":"You can join another group afterwards."));
            button("Disband group",split+8,bottom,right,()->confirm("disband","Disband "+group.getString("Name")+"?","This removes all members and invitations.")).active=leader();
        }else{
            if(clan){textField(x,y+22,w-102,"New clan name");button("Create clan",x+w-96,y+22,96,()->action("create",draft));}
            else button("Create party",x,y+22,112,()->action("create",""));
            if(clan){button("Browse clans",x,y+49,108,()->{invitations=false;pageNumber=0;rebuildWidgets();}).selected(!invitations);button("Invitations",x+114,y+49,108,()->{invitations=true;pageNumber=0;rebuildWidgets();}).selected(invitations);}
            List<CompoundTag> rows=clan&&!invitations?rows("Directory"):rows("Invites").stream().filter(r->r.getBoolean("Clan")==clan).toList();
            int start=y+(clan?77:60),count=Math.max(1,(cardY+cardH-55-start)/25),pages=Math.max(1,(rows.size()+count-1)/count);pageNumber=Math.min(pageNumber,pages-1);
            for(int i=pageNumber*count;i<Math.min(rows.size(),(pageNumber+1)*count);i++){
                var row=rows.get(i);int ry=start+(i-pageNumber*count)*25;String id=row.getUUID("Id").toString();
                boolean invite=!clan||invitations;String label=row.getString("Name")+(invite?"":"  "+row.getInt("Count")+"/32");
                button(font.plainSubstrByWidth(label,(int)((w-145)/.8)),x,ry,w-145,()->{}).active=false;
                button(invite?"Accept":row.getBoolean("Open")?"Join":"Invite-only",x+w-139,ry,invite?65:139,()->action(invite?"accept":"join",id)).active=invite||row.getBoolean("Open")&&row.getInt("Count")<32;
                if(invite)button("Decline",x+w-69,ry,69,()->action("decline",id));
            }
            pager(x,cardY+cardH-53,w,pages);
        }
    }
    private void pager(int x,int y,int w,int pages){if(pages<=1)return;button("<",x,y,24,()->{pageNumber--;rebuildWidgets();}).active=pageNumber>0;button((pageNumber+1)+" / "+pages,x+28,y,w-56,()->{}).active=false;button(">",x+w-24,y,24,()->{pageNumber++;rebuildWidgets();}).active=pageNumber+1<pages;}
    @Override public void tick(){super.tick();if(++ticks%100==0)action("refresh","");if(revision!=SocialState.revision){boolean focus=input!=null&&input.isFocused();int cursor=input==null?0:input.getCursorPosition();rebuildWidgets();if(focus&&input!=null){setFocused(input);input.setCursorPosition(Math.min(cursor,input.getValue().length()));}}if(input!=null)input.tick();}
    @Override protected void renderPage(GuiGraphics g,int mx,int my,float partial){
        int x=cardX+12,y=cardY+12,w=cardW-24;
        if(member()){
            var group=own();Ui.text(g,font,group.getString("Name"),x,y,Ui.GOLD,1);
            Ui.text(g,font,group.getList("Members",Tag.TAG_COMPOUND).size()+" / "+(clan?32:8)+" members  |  * Leader",x,y+16,Ui.MUTED,.75f);
            Ui.text(g,font,leader()?"Invite an online player":"Only your leader can send invitations",x+w/2+8,y+23,Ui.MUTED,.7f);
            String online=String.join(", ",rows("Players").stream().map(r->r.getString("Name")).filter(n->minecraft.player==null||!n.equals(minecraft.player.getGameProfile().getName())).limit(4).toList());
            if(cardH>=245)g.drawWordWrap(font,Component.literal("Online: "+(online.isEmpty()?"No other players":online)),x+w/2+8,y+116,w/2-12,Ui.MUTED);
        }else{
            Ui.text(g,font,clan?"Create a clan, join an open clan, or accept an invitation.":"Start a party and invite your friends to join.",x,y,Ui.MUTED,.75f);
            if(!clan||invitations){long count=rows("Invites").stream().filter(r->r.getBoolean("Clan")==clan).count();if(count==0)Ui.text(g,font,"No pending invitations. Invitations expire after 2 minutes.",x,y+(clan?80:60),Ui.MUTED,.75f);}
            else if(rows("Directory").isEmpty())Ui.text(g,font,"No clans yet. Create the first one above.",x,y+80,Ui.MUTED,.8f);
        }
        Ui.text(g,font,font.plainSubstrByWidth(message,(int)(w/.75)),x,cardY+cardH-18,Ui.GOLD,.75f);
    }
}
