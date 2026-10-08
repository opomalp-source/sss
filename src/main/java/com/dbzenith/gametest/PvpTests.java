package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.PvpRules;
import com.dbzenith.combat.PvpZones;
import com.dbzenith.data.ModCapabilities;
import com.dbzenith.data.PlayerData;
import com.dbzenith.registry.ModEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** CX-19 phase 1: PvP mode, the pull-in, the fight timer, the cooldown, safe zones, and effects judged like blows. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PvpTests {
    private static final String EMPTY = "empty";

    private PvpTests() {}

    /** Two survival players far from spawn (spawn and the tournament grounds are safe zones). */
    private static ServerPlayer[] pair(GameTestHelper helper, int x) {
        helper.getLevel().getServer().setPvpAllowed(true);                   // vanilla's switch first; the mod's rules sit on top of it
        ServerPlayer a = TestPlayers.create(helper), b = TestPlayers.create(helper);
        for (ServerPlayer p : new ServerPlayer[]{a, b}) {
            p.setGameMode(GameType.SURVIVAL);
            PlayerData d = ModCapabilities.getOrThrow(p);
            PvpRules.TEST_BYPASS.remove(p.getUUID());
            d.setPvp(false);
            d.setPvpReadyAt(0);
            d.setPvpCombatUntil(0);
        }
        BlockPos s = helper.getLevel().getSharedSpawnPos();
        a.teleportTo(s.getX() + x, s.getY() + 1, s.getZ() + 400);
        b.teleportTo(s.getX() + x + 1.5, s.getY() + 1, s.getZ() + 400);
        return new ServerPlayer[]{a, b};
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void pvpModeRules(GameTestHelper helper) {
        ServerPlayer[] p = pair(helper, 300);
        helper.runAfterDelay(70, () -> rules(helper, p));                   // new players are untouchable for their first 3 seconds
    }

    private static void rules(GameTestHelper helper, ServerPlayer[] p) {
        ServerPlayer a = p[0], b = p[1];
        PlayerData da = ModCapabilities.getOrThrow(a), db = ModCapabilities.getOrThrow(b);
        helper.assertTrue(PvpRules.toggle(a, true) && PvpRules.toggle(a, false) && !da.isPvp(), "a plain switch: on and straight back off (no cooldown by default)");

        // CX-20: a blow lands even out of PvP mode, and tags both into it
        float body = b.getHealth();
        b.invulnerableTime = 0;
        b.hurt(com.dbzenith.combat.ModDamageTypes.absorbed(b.level(), a), 2f);   // test players never wear off their spawn protection: a blow that ignores it
        helper.assertTrue(b.getHealth() < body, "the blow lands: " + b.getHealth() + " < " + body);
        helper.assertTrue(db.isPvp() && da.isPvp(), "and both are tagged into PvP mode");
        helper.assertTrue(!PvpRules.toggle(b, false) && db.isPvp(), "no switching off while tagged");
        db.setPvpCombatUntil(0);                                                // the tag runs out
        helper.assertTrue(db.isPvp(), "still in PvP mode after the tag");
        helper.assertTrue(PvpRules.toggle(b, false) && !db.isPvp(), "until they switch it off");

        // hit by a mob: tagged in too
        var zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(helper.getLevel());
        zombie.moveTo(b.position());
        b.invulnerableTime = 0;
        b.hurt(b.damageSources().mobAttack(zombie), 1f);
        helper.assertTrue(db.isPvp() && db.getPvpCombatUntil() > helper.getLevel().getGameTime(), "a mob's hit tags too");
        zombie.discard();

        // the server may say players out of PvP mode can't be hurt by players at all
        com.dbzenith.config.DBZConfig.SERVER.pvpHurtOutOfPvp.set(false);
        try {
            da.setPvp(false);
            db.setPvp(false);
            da.setPvpCombatUntil(0);
            db.setPvpCombatUntil(0);
            float before = b.getHealth();
            b.invulnerableTime = 0;
            b.hurt(com.dbzenith.combat.ModDamageTypes.absorbed(b.level(), a), 2f);
            helper.assertTrue(b.getHealth() == before && !db.isPvp(), "then no harm, and nobody is tagged");
            PvpRules.actor(a);                                                   // a technique's stun on someone out of PvP mode
            b.addEffect(new MobEffectInstance(ModEffects.STUN.get(), 40, 0));
            PvpRules.actor(null);
            helper.assertTrue(!b.hasEffect(ModEffects.STUN.get()), "effects are judged like blows");
        } finally {
            com.dbzenith.config.DBZConfig.SERVER.pvpHurtOutOfPvp.set(true);
        }
        TestPlayers.remove(helper, a);
        TestPlayers.remove(helper, b);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void safeZonesStopFights(GameTestHelper helper) {
        ServerPlayer[] p = pair(helper, 340);
        ServerPlayer a = p[0], b = p[1];
        PlayerData db = ModCapabilities.getOrThrow(b);
        ModCapabilities.getOrThrow(a).setPvp(true);
        db.setPvp(true);
        PvpZones zones = PvpZones.of(helper.getLevel().getServer());
        zones.add("test_zone", helper.getLevel().dimension().location().toString(), a.blockPosition().offset(-5, -5, -5), a.blockPosition().offset(5, 5, 5));
        helper.assertTrue(PvpRules.inSafeZone(a), "inside the zone");
        float body = b.getHealth();
        b.invulnerableTime = 0;
        b.hurt(com.dbzenith.combat.ModDamageTypes.absorbed(b.level(), a), 2f);   // test players never wear off their spawn protection: a blow that ignores it
        helper.assertTrue(b.getHealth() == body, "no fighting in a safe zone");
        zones.remove("test_zone");
        BlockPos s = helper.getLevel().getSharedSpawnPos();
        a.teleportTo(s.getX() + 0.5, s.getY() + 1, s.getZ() + 0.5);
        helper.assertTrue(PvpRules.inSafeZone(a), "world spawn is safe");
        TestPlayers.remove(helper, a);
        TestPlayers.remove(helper, b);
        helper.succeed();
    }
}
