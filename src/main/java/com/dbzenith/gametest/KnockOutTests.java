package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.Carry;
import com.dbzenith.combat.Grip;
import com.dbzenith.combat.KnockOut;
import com.dbzenith.combat.Limbs;
import com.dbzenith.combat.SeveredLimb;
import com.dbzenith.config.DBZConfig;
import com.dbzenith.npc.KiFighter;
import com.dbzenith.npc.ModNpcs;
import com.dbzenith.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * CX-51 and the knocked-out rework: knocked out at zero health; carried on the shoulder; blows only cost time, a grip
 * finishes; a hard hit breaks a grip; helped up; bled out; saved by a Senzu Bean; cut limbs bleed and fly off.
 */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public class KnockOutTests {
    private static final String EMPTY = "empty";
    private static final int SPAWN_PROTECTION = 62;

    @GameTest(template = EMPTY, batch = "knockout", timeoutTicks = SPAWN_PROTECTION + 40)
    public static void playerKnockedOutCarriedThenSavedBySenzu(GameTestHelper h) {
        ServerPlayer p = TestPlayers.create(h);
        ServerPlayer friend = TestPlayers.create(h);
        p.setGameMode(GameType.SURVIVAL);
        h.runAfterDelay(SPAWN_PROTECTION, () -> {
            p.hurt(p.damageSources().generic(), 100_000f);
            h.assertTrue(p.isAlive() && p.getHealth() > 0, "a lethal blow knocks out instead of killing");
            h.assertTrue(KnockOut.isDown(p), "down on the ground");
            p.heal(1000);
            h.assertTrue(KnockOut.isDown(p) && p.getHealth() < p.getMaxHealth() * 0.2f, "no healing on your own while down");
            friend.teleportTo(p.getX() + 1, p.getY(), p.getZ());
            h.assertTrue(Carry.pickUp(friend, p) && Carry.carrierOf(p) == friend, "a friend picks them up");
            h.assertTrue(p.getVehicle() == null, "not by riding (it misbehaved for the carried player's own game)");
            h.runAfterDelay(2, () -> {
                Vec3 shoulder = Carry.shoulder(friend.getX(), friend.getY(), friend.getZ(), friend.getYRot(), friend.getBbHeight());
                h.assertTrue(p.position().distanceTo(shoulder) < 0.05, "held on the carrier's shoulder: " + p.position() + " vs " + shoulder);
                Carry.drop(friend, false);
                h.assertTrue(!Carry.isCarried(p) && KnockOut.isDown(p), "put down, still down");
                ItemStack senzu = new ItemStack(ModItems.SENZU_BEAN.get(), 2);
                friend.setGameMode(GameType.SURVIVAL);
                h.assertTrue(KnockOut.feed(friend, p, senzu), "a Senzu Bean is fed to them");
                h.assertTrue(!KnockOut.isDown(p) && p.getHealth() >= p.getMaxHealth() - 0.01f, "back up at full health");
                h.assertTrue(senzu.getCount() == 1, "the bean is used up");
                TestPlayers.remove(h, p);
                TestPlayers.remove(h, friend);
                h.succeed();
            });
        });
    }

    @GameTest(template = EMPTY, batch = "knockout_grip", timeoutTicks = SPAWN_PROTECTION + 60)   // apart from the bleed-out test, which shortens NPC knockouts for a moment
    public static void blowsCostTimeOnlyAGripFinishes(GameTestHelper h) {
        ServerPlayer p = TestPlayers.create(h);
        p.setGameMode(GameType.SURVIVAL);
        KiFighter foe = h.spawnWithNoFreeWill(ModNpcs.KI_SOLDIER.get(), new BlockPos(1, 1, 1));
        h.runAfterDelay(SPAWN_PROTECTION, () -> {
            foe.hurt(foe.damageSources().playerAttack(p), 1_000_000f);
            h.assertTrue(foe.isAlive() && KnockOut.isDown(foe), "the NPC fighter goes down instead of dying");
            int before = KnockOut.ticksLeft(foe);
            foe.hurt(foe.damageSources().playerAttack(p), 1_000_000f);
            h.assertTrue(foe.isAlive() && KnockOut.isDown(foe), "a blow no longer finishes them");
            h.assertTrue(KnockOut.ticksLeft(foe) < before, "but it costs them time: " + before + " -> " + KnockOut.ticksLeft(foe));
            p.teleportTo(foe.getX(), foe.getY(), foe.getZ() - 1.5);
            h.assertTrue(Grip.start(p, foe, Grip.Finisher.STOMP), "a grip begins");
            h.assertTrue(Grip.isGripping(p) && Grip.isGripped(foe), "both held in it");
            h.runAfterDelay(Grip.Finisher.STOMP.killAt + 2, () -> {
                h.assertTrue(!foe.isAlive(), "the grip finishes them");
                TestPlayers.remove(h, p);
                h.succeed();
            });
        });
    }

    @GameTest(template = EMPTY, batch = "knockout", timeoutTicks = SPAWN_PROTECTION + 80)
    public static void aHardHitBreaksTheGrip(GameTestHelper h) {
        ServerPlayer p = TestPlayers.create(h);
        ServerPlayer gripper = TestPlayers.create(h);
        p.setGameMode(GameType.SURVIVAL);
        gripper.setGameMode(GameType.SURVIVAL);
        h.runAfterDelay(SPAWN_PROTECTION, () -> {
            Vec3 spot = Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(1, 2, 2)));   // both in known places, in the open
            p.teleportTo(spot.x, spot.y, spot.z);
            gripper.teleportTo(spot.x, spot.y, spot.z - 1.5);
            p.hurt(p.damageSources().generic(), 100_000f);
            h.assertTrue(Grip.start(gripper, p, Grip.Finisher.KI_EXECUTION), "a grip begins: down " + KnockOut.isDown(p) + " gripperDown " + KnockOut.isDown(gripper) + " dist " + gripper.distanceTo(p) + " sight " + gripper.hasLineOfSight(p) + " stunned " + com.dbzenith.registry.ModEffects.isStunned(gripper));
            h.runAfterDelay(10, () -> {
                gripper.invulnerableTime = 0;
                gripper.hurt(gripper.damageSources().generic(), gripper.getMaxHealth() * 0.2f);
            });
            h.runAfterDelay(13, () -> h.assertTrue(!Grip.isGripping(gripper), "a hard hit breaks the hold: health " + gripper.getHealth() + "/" + gripper.getMaxHealth()));
            h.runAfterDelay(Grip.Finisher.KI_EXECUTION.killAt + 6, () -> {
                h.assertTrue(p.isAlive() && KnockOut.isDown(p), "they live on, still down");
                TestPlayers.remove(h, p);
                TestPlayers.remove(h, gripper);
                h.succeed();
            });
        });
    }

    @GameTest(template = EMPTY, batch = "knockout", timeoutTicks = SPAWN_PROTECTION + KnockOut.HELP_TICKS + 40)
    public static void aFriendHelpsThemUp(GameTestHelper h) {
        ServerPlayer p = TestPlayers.create(h);
        ServerPlayer friend = TestPlayers.create(h);
        p.setGameMode(GameType.SURVIVAL);
        h.runAfterDelay(SPAWN_PROTECTION, () -> {
            p.hurt(p.damageSources().generic(), 100_000f);
            friend.teleportTo(p.getX() + 1, p.getY(), p.getZ());
            for (int t = 0; t <= KnockOut.HELP_TICKS + 8; t += 4) h.runAfterDelay(t, () -> KnockOut.help(friend, p));   // holding right-click repeats it
            h.runAfterDelay(KnockOut.HELP_TICKS + 14, () -> {
                h.assertTrue(!KnockOut.isDown(p), "back on their feet");
                h.assertTrue(p.getHealth() >= p.getMaxHealth() * 0.2f && p.getHealth() < p.getMaxHealth() * 0.6f, "with about a quarter of their health: " + p.getHealth());
                TestPlayers.remove(h, p);
                TestPlayers.remove(h, friend);
                h.succeed();
            });
        });
    }

    @GameTest(template = EMPTY, batch = "knockout", timeoutTicks = 120)
    public static void downedFighterBleedsOut(GameTestHelper h) {
        int was = DBZConfig.SERVER.knockOutNpcSeconds.get();
        DBZConfig.SERVER.knockOutNpcSeconds.set(1);
        KiFighter foe = h.spawnWithNoFreeWill(ModNpcs.KI_SOLDIER.get(), new BlockPos(1, 1, 1));
        foe.hurt(foe.damageSources().generic(), 1_000_000f);
        DBZConfig.SERVER.knockOutNpcSeconds.set(was);
        h.assertTrue(KnockOut.isDown(foe), "down");
        h.runAfterDelay(40, () -> {
            h.assertTrue(!foe.isAlive(), "bled out after its time");
            h.succeed();
        });
    }

    @GameTest(template = EMPTY, batch = "knockout", timeoutTicks = 60)
    public static void aCutLimbBleedsAndFliesOff(GameTestHelper h) {
        KiFighter foe = h.spawnWithNoFreeWill(ModNpcs.KI_SOLDIER.get(), new BlockPos(1, 1, 1));
        Limbs.sever(foe, Limbs.Limb.LEFT_ARM);
        h.assertTrue(Limbs.of(foe).bleeding(Limbs.Limb.LEFT_ARM), "a fresh cut bleeds");
        h.runAfterDelay(2, () -> {
            h.assertTrue(!h.getLevel().getEntitiesOfClass(SeveredLimb.class, foe.getBoundingBox().inflate(6)).isEmpty(), "the limb flies off");
            float hp = foe.getHealth();
            h.runAfterDelay(25, () -> {
                h.assertTrue(foe.getHealth() < hp, "bleeding drains health");
                h.assertTrue(Limbs.stopBleeding(foe) && !Limbs.of(foe).bleeding(Limbs.Limb.LEFT_ARM), "a bandage stops it");
                h.succeed();
            });
        });
    }
}
