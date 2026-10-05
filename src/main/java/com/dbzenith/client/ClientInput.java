package com.dbzenith.client;

import com.dbzenith.DBZenith;
import com.dbzenith.client.screen.StatScreen;
import com.dbzenith.network.DashPacket;
import com.dbzenith.network.InputPacket;
import com.dbzenith.network.ModNetwork;
import com.dbzenith.network.UseTechniquePacket;
import com.dbzenith.skill.Technique;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Turns key state into packets. Held keys send start/stop edges only. */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientInput {
    private static boolean wasCharging;
    private static boolean wasGuarding;
    private static boolean wasHeavy;

    private ClientInput() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            wasCharging = wasGuarding = wasHeavy = false;
            return;
        }

        boolean charging = ModKeys.CHARGE.isDown() && mc.screen == null;
        if (charging != wasCharging) {
            ModNetwork.sendToServer(new InputPacket(charging ? InputPacket.Action.CHARGE_START : InputPacket.Action.CHARGE_STOP));
            wasCharging = charging;
        }
        boolean guarding = ModKeys.GUARD.isDown() && mc.screen == null;
        if (guarding != wasGuarding) {
            ModNetwork.sendToServer(new InputPacket(guarding ? InputPacket.Action.GUARD_START : InputPacket.Action.GUARD_STOP));
            wasGuarding = guarding;
        }

        boolean heavy = ModKeys.HEAVY.isDown() && mc.screen == null;
        if (heavy != wasHeavy) {
            if (heavy) ModNetwork.sendToServer(new InputPacket(InputPacket.Action.HEAVY_START));
            else ModNetwork.sendToServer(new com.dbzenith.network.HeavyReleasePacket(mc.player.input.forwardImpulse, mc.player.input.leftImpulse));
            wasHeavy = heavy;
        }
        while (ModKeys.DASH.consumeClick()) {
            ModNetwork.sendToServer(new DashPacket(mc.player.input.forwardImpulse, mc.player.input.leftImpulse));
        }

        while (ModKeys.FLY.consumeClick()) ModNetwork.sendToServer(new InputPacket(InputPacket.Action.TOGGLE_FLIGHT));
        while (ModKeys.LOWER_RELEASE.consumeClick()) ModNetwork.sendToServer(new InputPacket(InputPacket.Action.LOWER_RELEASE));
        while (ModKeys.NEXT_TECHNIQUE.consumeClick()) {
            Technique t = ClientCombatState.cycle();
            if (t != null) {
                mc.player.displayClientMessage(Component.translatable("message.dbzenith.selected_technique",
                        t.name()), true);
            }
        }
        while (ModKeys.KI_ATTACK.consumeClick()) {
            if (ClientStruggle.active()) {                       // locked in a beam struggle: every press pushes
                ModNetwork.sendToServer(new com.dbzenith.network.BeamMashPacket());
                ClientStruggle.mashed();
                continue;
            }
            Technique t = ClientCombatState.selected();
            long now = mc.level.getGameTime();
            if (t != null && !ClientCombatState.onCooldown(t, now)) {
                ModNetwork.sendToServer(new UseTechniquePacket(t.id()));
                ClientCombatState.startCooldown(t, now);
            }
        }
        while (ModKeys.TRANSFORM.consumeClick()) {
            ModNetwork.sendToServer(new InputPacket(Screen.hasShiftDown() ? InputPacket.Action.TRANSFORM_DOWN : InputPacket.Action.TRANSFORM_UP));
        }
        while (ModKeys.STATS.consumeClick()) mc.setScreen(new StatScreen());
        while (ModKeys.RADIAL.consumeClick()) com.dbzenith.client.ui.RadialMenuScreen.open();
        while (ModKeys.KAIOKEN.consumeClick()) {
            ModNetwork.sendToServer(new InputPacket(Screen.hasShiftDown() ? InputPacket.Action.KAIOKEN_OFF : InputPacket.Action.KAIOKEN_UP));
        }
        while (ModKeys.SKILL.consumeClick()) {                          // shift: browse the universal skills
            com.dbzenith.data.PlayerData pd = ClientPlayerData.get();
            if (Screen.hasShiftDown()) mc.setScreen(new com.dbzenith.client.screen.RacialScreen(null, true));
            else if (pd.getSkillSelected().equals("instant_transmission") && pd.getSkillLevel("instant_transmission") > 0) {
                if (mc.level.getGameTime() >= pd.getRacialCooldown("instant_transmission")) ModNetwork.sendToServer(new com.dbzenith.network.RacialPackets.TransmitRequest());
                else ModNetwork.sendToServer(new InputPacket(InputPacket.Action.SKILL_USE));   // the server says how long
            } else ModNetwork.sendToServer(new InputPacket(InputPacket.Action.SKILL_USE));
        }
        while (ModKeys.RACIAL.consumeClick()) {                         // shift: browse the racial skills
            if (Screen.hasShiftDown()) mc.setScreen(new com.dbzenith.client.screen.RacialScreen(null));
            else ModNetwork.sendToServer(new InputPacket(InputPacket.Action.RACIAL_USE));
        }
    }
}
