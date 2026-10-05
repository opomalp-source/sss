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
            ModNetwork.sendToServer(new InputPacket(heavy ? InputPacket.Action.HEAVY_START : InputPacket.Action.HEAVY_STOP));
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
        while (ModKeys.OVERDRIVE.consumeClick()) {
            ModNetwork.sendToServer(new InputPacket(Screen.hasShiftDown() ? InputPacket.Action.OVERDRIVE_OFF : InputPacket.Action.OVERDRIVE_UP));
        }
        while (ModKeys.STATS.consumeClick()) mc.setScreen(new StatScreen());
        while (ModKeys.RADIAL.consumeClick()) com.dbzenith.client.ui.RadialMenuScreen.open();
    }
}
