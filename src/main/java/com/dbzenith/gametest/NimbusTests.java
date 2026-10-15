package com.dbzenith.gametest;
import com.dbzenith.nimbus.*;
import com.dbzenith.data.ModCapabilities;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

@GameTestHolder("nimbusqa")
@PrefixGameTestTemplate(false)
public class NimbusTests {
    private static Vec3 air(GameTestHelper h) { var p=h.absoluteVec(new Vec3(2,2,2)); return new Vec3(p.x,h.getLevel().getMaxBuildHeight()-40,p.z); }
    /** CX-40: the item binds the cloud to your soul; the call key brings it down to you, and again sends it away. */
    @GameTest(template="empty",timeoutTicks=400)
    public static void bindCallAndSendAway(GameTestHelper h) {
        var p=TestPlayers.create(h);var d=ModCapabilities.getOrThrow(p);d.initDefaultsIfNeeded();
        p.setPos(air(h));p.setYRot(0);p.setXRot(0);
        NimbusBond.setBound(p,false);
        var stack=new net.minecraft.world.item.ItemStack(NimbusContent.ITEM);
        p.setItemInHand(InteractionHand.MAIN_HAND,stack);
        d.setAlignment(10);NimbusContent.ITEM.use(h.getLevel(),p,InteractionHand.MAIN_HAND);
        h.assertTrue(!NimbusBond.bound(p)&&stack.getCount()==1,"a heart that is not good cannot bind it");
        NimbusBond.press(p);
        h.assertTrue(NimbusBond.owned(p).isEmpty(),"nothing comes before binding");
        d.setAlignment(30);NimbusContent.ITEM.use(h.getLevel(),p,InteractionHand.MAIN_HAND);
        h.assertTrue(NimbusBond.bound(p),"using the item binds it");
        h.assertTrue(stack.isEmpty(),"the item is absorbed");
        h.assertTrue(p.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG).getBoolean("dbzenith_nimbus_bound"),"the bond is kept across death");
        h.assertTrue(NimbusBond.owned(p).isEmpty(),"binding alone calls nothing");
        var cloud=NimbusBond.call(p);
        h.assertTrue(cloud.state()==NimbusEntity.ARRIVING,"called: on its way");
        h.assertTrue(cloud.getY()>p.getY()+10,"it comes from high above");
        h.succeedWhen(()-> {
            h.assertTrue(cloud.state()==NimbusEntity.IDLE,"still arriving at "+cloud.position());
            h.assertTrue(cloud.distanceToSqr(p)<3*3,"parked beside its owner");
            NimbusBond.press(p);
            h.assertTrue(cloud.leaving(),"pressing again sends it away");
            h.assertTrue(NimbusBond.owned(p).isEmpty(),"a leaving cloud no longer counts as yours");
            TestPlayers.remove(h,p);
        });
    }
    @GameTest(template="empty",timeoutTicks=200)
    public static void sentAwayWhileRidingDissolves(GameTestHelper h) {
        var p=TestPlayers.create(h);var d=ModCapabilities.getOrThrow(p);d.initDefaultsIfNeeded();d.setAlignment(40);
        NimbusBond.setBound(p,true);
        var n=new NimbusEntity(NimbusContent.TYPE,h.getLevel());n.setPos(air(h));n.setOwner(p);h.getLevel().addFreshEntity(n);
        p.setPos(air(h));n.interact(p,InteractionHand.MAIN_HAND);
        h.assertTrue(p.getVehicle()==n,"riding");
        NimbusBond.press(p);
        h.assertTrue(!p.isPassenger(),"sent away: the rider gets off");
        h.assertTrue(p.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING),"high up: the rider drifts down");
        h.assertTrue(!p.startRiding(n),"a leaving cloud cannot be mounted");
        Delays.after(h, NimbusEntity.LEAVE_TICKS+5,()-> {
            h.assertTrue(n.isRemoved(),"dissolved");
            NimbusBond.setBound(p,false);TestPlayers.remove(h,p);h.succeed();
        });
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void alignmentAndControls(GameTestHelper h) {
        var p=TestPlayers.create(h);var d=ModCapabilities.getOrThrow(p);d.initDefaultsIfNeeded();
        var n=new NimbusEntity(NimbusContent.TYPE,h.getLevel());
        var at=air(h);n.setPos(at);h.getLevel().addFreshEntity(n);
        p.setPos(at.add(2,0,0));
        d.setAlignment(29);n.interact(p,InteractionHand.MAIN_HAND);
        h.assertTrue(!p.isPassenger(),"neutral player denied");
        d.setAlignment(-100);h.assertTrue(!p.startRiding(n),"evil player cannot bypass interaction");
        d.setAlignment(30);n.interact(p,InteractionHand.MAIN_HAND);
        h.assertTrue(p.getVehicle()==n,"good boundary can ride");
        h.assertTrue(!n.shouldRiderSit(),"standing default");n.togglePosture();
        h.assertTrue(n.shouldRiderSit(),"seated mode");
        n.controls(p,Float.NaN,1,true,true,false);n.tick();
        h.assertTrue(n.getDeltaMovement().lengthSqr()==0,"invalid input rejected");
        p.setYRot(0);p.setXRot(0);n.controls(p,1,0,true,false,false);n.tick();
        h.assertTrue(n.getDeltaMovement().z>0&&n.getDeltaMovement().y>0,"forward and ascent work");
        d.setAlignment(0);n.tick();h.assertTrue(!p.isPassenger(),"alignment checked while riding");
        n.discard();TestPlayers.remove(h,p);h.succeed();
    }
    @GameTest(template="empty",timeoutTicks=100)
    public static void timeoutBrakesAndModePersists(GameTestHelper h) {
        var p=TestPlayers.create(h);var d=ModCapabilities.getOrThrow(p);d.initDefaultsIfNeeded();d.setAlignment(50);
        var n=new NimbusEntity(NimbusContent.TYPE,h.getLevel());n.setPos(air(h));h.getLevel().addFreshEntity(n);p.startRiding(n);
        p.setYRot(0);p.setXRot(0);n.controls(p,1,0,false,true,true);n.tick();
        var tag=new net.minecraft.nbt.CompoundTag();n.saveWithoutId(tag);
        var restored=new NimbusEntity(NimbusContent.TYPE,h.getLevel());restored.load(tag);
        h.assertTrue(restored.sitting(),"posture saved");
        h.assertTrue(n.getDeltaMovement().lengthSqr()>.001,"cloud was moving before timeout");
        for(int i=0;i<130;i++) { n.tickCount++;n.tick(); }   // it glides to a stop (momentum), it does not halt at once
        h.assertTrue(n.getDeltaMovement().lengthSqr()<.0001,"lost input glides to a hover");
        n.discard();TestPlayers.remove(h,p);h.succeed();
    }
}
