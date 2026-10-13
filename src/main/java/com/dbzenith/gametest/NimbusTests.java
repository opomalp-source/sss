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
    @GameTest(template="empty",timeoutTicks=100)
    public static void reusableSummon(GameTestHelper h) {
        var p=TestPlayers.create(h);var d=ModCapabilities.getOrThrow(p);d.initDefaultsIfNeeded();d.setAlignment(30);
        p.setPos(air(h));p.setYRot(0);p.setXRot(0);
        var stack=new net.minecraft.world.item.ItemStack(NimbusContent.ITEM);
        p.setItemInHand(InteractionHand.MAIN_HAND,stack);
        NimbusContent.ITEM.use(h.getLevel(),p,InteractionHand.MAIN_HAND);
        h.assertTrue(p.getVehicle() instanceof NimbusEntity,"item summons and mounts in open air");
        var first=(NimbusEntity)p.getVehicle();
        h.assertTrue(stack.getCount()==1,"summon item is reusable");
        NimbusContent.ITEM.use(h.getLevel(),p,InteractionHand.MAIN_HAND);
        h.assertTrue(first.sitting(),"using item switches posture");
        p.stopRiding();p.setPos(air(h));var result=NimbusContent.ITEM.use(h.getLevel(),p,InteractionHand.MAIN_HAND);
        h.assertTrue(p.getVehicle() instanceof NimbusEntity&&p.getVehicle()!=first,"item summons again: result="+result.getResult()+" pos="+p.position()+" removed="+first.isRemoved());
        h.assertTrue(first.isRemoved(),"previous empty owned cloud replaced");
        p.getVehicle().discard();TestPlayers.remove(h,p);h.succeed();
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
        for(int i=0;i<60;i++) { n.tickCount++;n.tick(); }
        h.assertTrue(n.getDeltaMovement().lengthSqr()<.0001,"lost input brakes to hover");
        n.discard();TestPlayers.remove(h,p);h.succeed();
    }
}
