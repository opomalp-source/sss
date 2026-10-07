package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.HeavyStrike;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.ki.DashHandler;
import com.dbzenith.ki.KiTicker;
import com.dbzenith.network.PublicStatePacket;
import com.dbzenith.skill.KiBeamEntity;
import com.dbzenith.skill.TechniqueHandler;
import com.dbzenith.skill.Techniques;
import com.dbzenith.stats.Attribute;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Phase 1 (second half): heavy hit, dash + afterimage, beams, DEX speed, public state. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MovementAndBeamTests {
    private static final String EMPTY = "empty";
    private static final int SPAWN_PROTECTION = 62;

    private MovementAndBeamTests() {}

    @GameTest(template = EMPTY, timeoutTicks = SPAWN_PROTECTION + 40)
    public static void dashMovesCostsAndEvades(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper);
        player.setGameMode(GameType.SURVIVAL);
        PlayerData d = ModCapabilities.getOrThrow(player);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        helper.runAfterDelay(SPAWN_PROTECTION, () -> {
            // sideways: a forward dash at whatever stands in front (other tests share the area) would be a super dash
            double stamina = d.getStamina();
            helper.assertTrue(DashHandler.dash(player, 0, 1), "dash should succeed");
            helper.assertTrue(player.getDeltaMovement().horizontalDistance() > 1.0, "dash should set a fast velocity");
            helper.assertTrue(d.getStamina() < stamina, "dash costs stamina");
            helper.assertTrue(!DashHandler.dash(player, 0, 1), "dash should be on cooldown");
            double body = d.getBody();
            player.hurt(player.damageSources().mobAttack(zombie), 4f);
            helper.assertTrue(d.getBody() == body, "attacks during the afterimage window should miss");
            TestPlayers.remove(helper, player);
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void beamDamagesAlongItsLine(GameTestHelper helper) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        float start = zombie.getHealth();
        ServerPlayer player = TestPlayers.create(helper);
        player.setGameMode(GameType.SURVIVAL);
        player.moveTo(zombie.getX() - 3, zombie.getY(), zombie.getZ(), -90f, 0f); // yaw -90 faces +X; close, so neighbouring tests cannot get in the way
        player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, zombie.position().add(0, 0.3, 0));   // through it into the ground: the beam stops there, clear of other tests
        helper.assertTrue(TechniqueHandler.use(player, Techniques.FINGER_BEAM, true) == TechniqueHandler.Result.FIRED, "beam should fire");
        helper.assertTrue(!helper.getLevel().getEntitiesOfClass(KiBeamEntity.class, player.getBoundingBox().inflate(3)).isEmpty(),
                "a beam entity should exist at the caster");
        helper.succeedWhen(() -> {
            helper.assertTrue(zombie.getHealth() < start, "zombie in the beam's path should be hurt");
            helper.getLevel().getEntitiesOfClass(KiBeamEntity.class, player.getBoundingBox().inflate(64), b -> b.getOwner() == player).forEach(KiBeamEntity::discard);
            TestPlayers.remove(helper, player);
        });
    }

    @GameTest(template = EMPTY)
    public static void dexterityRaisesMoveSpeed(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper);
        PlayerData d = ModCapabilities.getOrThrow(player);
        double base = player.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
        d.setAttribute(Attribute.DEXTERITY, 400);
        KiTicker.tick(player, d);
        double now = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        helper.assertTrue(now > base * 1.1, "DEX 400 should raise move speed: " + now + " vs base " + base);
        d.setAttribute(Attribute.DEXTERITY, 10);
        KiTicker.tick(player, d);
        helper.assertTrue(player.getAttributeValue(Attributes.MOVEMENT_SPEED) < now, "lowering DEX should lower the bonus");
        TestPlayers.remove(helper, player);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void publicStateReflectsCharging(GameTestHelper helper) {
        PlayerData d = new PlayerData();
        d.initDefaultsIfNeeded();
        PublicStatePacket idle = PublicStatePacket.of(1, d);
        d.setCharging(true);
        PublicStatePacket charging = PublicStatePacket.of(1, d);
        helper.assertTrue(!idle.has(PublicStatePacket.CHARGING) && charging.has(PublicStatePacket.CHARGING), "charging flag");
        helper.assertTrue(idle.stateHash() != charging.stateHash(), "state change must change the hash (triggers a send)");
        d.setCharging(false);
        d.setMeditating(true);
        helper.assertTrue(PublicStatePacket.of(1, d).has(PublicStatePacket.MEDITATING), "meditation is visible to others (sitting pose)");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void impactsSurviveTheWireAndLandOnTheVictim(GameTestHelper helper) {
        ServerPlayer attacker = TestPlayers.create(helper);
        Zombie victim = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 3));
        attacker.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(1.5, 2, 0.5)));
        var hit = com.dbzenith.network.ImpactPacket.melee(attacker, victim, com.dbzenith.network.ImpactPacket.HEAVY);
        // the flash sits on the victim's side facing the attacker, at chest height
        helper.assertTrue(hit.z() < victim.getZ() && Math.abs(hit.y() - victim.getBoundingBox().getCenter().y) < 0.01,
                "impact faces the attacker at the victim's middle");
        var buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        com.dbzenith.network.ImpactPacket.encode(hit, buf);
        var back = com.dbzenith.network.ImpactPacket.decode(buf);
        helper.assertTrue(back.equals(hit), "impact packet round-trips: " + back + " vs " + hit);
        var orphan = com.dbzenith.network.ImpactPacket.at(victim.position(), victim.getLookAngle(), com.dbzenith.network.ImpactPacket.EXPLOSION, 3f, 0xFF8800, -1);
        buf.clear();
        com.dbzenith.network.ImpactPacket.encode(orphan, buf);
        helper.assertTrue(com.dbzenith.network.ImpactPacket.decode(buf).attackerId() == -1, "no attacker (-1) survives the varint offset");
        helper.assertTrue(com.dbzenith.transform.Forms.SUPER_SAIYAN_BLUE.calmAura() && !com.dbzenith.transform.Forms.SUPER_SAIYAN.calmAura(),
                "god-ki forms burn calm, the others roar");
        TestPlayers.remove(helper, attacker);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void techniquesPickTheirCastAnimation(GameTestHelper helper) {
        java.util.function.ToIntFunction<com.dbzenith.skill.Technique> kind = t -> com.dbzenith.network.AnimEventPacket.forTechnique(1, t).kind();
        helper.assertTrue(kind.applyAsInt(com.dbzenith.skill.Techniques.KI_BLAST) == com.dbzenith.network.AnimEventPacket.BLAST, "ki blast: palm thrust");
        helper.assertTrue(kind.applyAsInt(com.dbzenith.skill.Techniques.RAPID_VOLLEY) == com.dbzenith.network.AnimEventPacket.VOLLEY, "volley: alternating palms");
        helper.assertTrue(kind.applyAsInt(com.dbzenith.skill.Techniques.EXPLOSIVE_WAVE) == com.dbzenith.network.AnimEventPacket.WAVE, "explosive wave: arms flung out");
        helper.assertTrue(kind.applyAsInt(com.dbzenith.skill.Techniques.KI_HEAL) == com.dbzenith.network.AnimEventPacket.FOCUS, "heal: focus");
        var beam = com.dbzenith.network.AnimEventPacket.forTechnique(1, com.dbzenith.skill.Techniques.WAVE_BEAM);
        helper.assertTrue(beam.kind() == com.dbzenith.network.AnimEventPacket.BEAM && beam.data() == com.dbzenith.skill.Techniques.WAVE_BEAM.lifeTicks(),
                "beams hold the pose as long as the beam burns");
        var sphere = com.dbzenith.network.AnimEventPacket.forTechnique(1, com.dbzenith.skill.Techniques.GATHERING_SPHERE);
        helper.assertTrue(sphere.kind() == com.dbzenith.network.AnimEventPacket.THROW && sphere.data() == com.dbzenith.skill.Techniques.GATHERING_SPHERE.holdTicks(),
                "giant spheres are held overhead, then thrown");
        helper.succeed();
    }
}
