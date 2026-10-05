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

    @GameTest(template = EMPTY)
    public static void heavyHitMultipliesMelee(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper);
        player.setGameMode(GameType.SURVIVAL);
        PlayerData d = ModCapabilities.getOrThrow(player);
        d.setAttribute(Attribute.STRENGTH, 60);
        d.recomputeIfStale();
        Zombie a = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(0, 1, 0));
        Zombie b = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
        float startA = a.getHealth();
        float startB = b.getHealth();
        player.attack(a);
        float normal = startA - a.getHealth();

        d.startHeavyCharge();
        for (int i = 0; i < 40; i++) d.tickHeavyCharge();
        double mult = HeavyStrike.release(player, d);
        helper.assertTrue(mult >= 2.9, "full charge should arm ~3x, got " + mult);
        player.attack(b);
        float heavy = startB - b.getHealth();
        helper.assertTrue(heavy > normal * 2, "heavy hit " + heavy + " should be > 2x normal " + normal);
        helper.assertTrue(d.getHeavyArmedMultiplier() == 0, "heavy charge should be consumed");
        TestPlayers.remove(helper, player);
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = SPAWN_PROTECTION + 40)
    public static void dashMovesCostsAndEvades(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper);
        player.setGameMode(GameType.SURVIVAL);
        PlayerData d = ModCapabilities.getOrThrow(player);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        helper.runAfterDelay(SPAWN_PROTECTION, () -> {
            double stamina = d.getStamina();
            helper.assertTrue(DashHandler.dash(player, 1, 0), "dash should succeed");
            helper.assertTrue(player.getDeltaMovement().horizontalDistance() > 1.0, "dash should set a fast velocity");
            helper.assertTrue(d.getStamina() < stamina, "dash costs stamina");
            helper.assertTrue(!DashHandler.dash(player, 1, 0), "dash should be on cooldown");
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
        helper.assertTrue(TechniqueHandler.use(player, Techniques.FINGER_BEAM, true) == TechniqueHandler.Result.FIRED, "beam should fire");
        helper.assertTrue(!helper.getLevel().getEntitiesOfClass(KiBeamEntity.class, player.getBoundingBox().inflate(3)).isEmpty(),
                "a beam entity should exist at the caster");
        helper.succeedWhen(() -> {
            helper.assertTrue(zombie.getHealth() < start, "zombie in the beam's path should be hurt");
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
