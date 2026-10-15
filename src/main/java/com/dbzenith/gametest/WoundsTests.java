package com.dbzenith.gametest;

import com.dbzenith.DBZenith;
import com.dbzenith.combat.Wounds;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** CX-52: badly hurt fighters move slower, fire slower and come round slower. */
@GameTestHolder(DBZenith.MOD_ID)
@PrefixGameTestTemplate(false)
public class WoundsTests {
    @GameTest(template = "empty", batch = "knockout", timeoutTicks = 40)
    public static void badlyHurtIsSlower(GameTestHelper h) {
        ServerPlayer p = TestPlayers.create(h);
        p.setGameMode(GameType.SURVIVAL);
        double before = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
        h.assertTrue(Wounds.wounded(p) == 0 && Wounds.cooldown(p) == 1 && Wounds.kiSpeed(p) == 1, "unhurt: no effect");
        p.setHealth(p.getMaxHealth() * 0.6f);
        h.assertTrue(Wounds.wounded(p) == 0, "above a third of their health: still fine");
        p.setHealth(p.getMaxHealth() * 0.1f);
        float w = Wounds.wounded(p);
        h.assertTrue(w > 0.6f && w < 0.8f, "at a tenth: badly hurt " + w);
        h.assertTrue(Wounds.cooldown(p) > 1.4 && Wounds.kiSpeed(p) < 0.8f && Wounds.flight(p) < 0.8f, "slower ki, longer cooldowns, slower flight");
        Wounds.update(p);
        Delays.after(h, 1, () -> {
            double after = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
            h.assertTrue(after < before * 0.75, "moves slower: " + before + " -> " + after);
            TestPlayers.remove(h, p);
            h.succeed();
        });
    }
}
