package com.dbzenith.registry;

import com.dbzenith.DBZenith;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Every sound the mod makes. All of them are synthesized by tools/SfxGen.java (no samples); sounds.json picks a
 * random variant each time.
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, DBZenith.MOD_ID);

    public static final RegistryObject<SoundEvent> PUNCH_LIGHT = sound("punch_light");
    public static final RegistryObject<SoundEvent> PUNCH_HEAVY = sound("punch_heavy");
    public static final RegistryObject<SoundEvent> WHOOSH = sound("whoosh");
    public static final RegistryObject<SoundEvent> KI_FIRE = sound("ki_fire");
    public static final RegistryObject<SoundEvent> KI_HIT = sound("ki_hit");
    public static final RegistryObject<SoundEvent> EXPLOSION = sound("explosion");
    public static final RegistryObject<SoundEvent> EXPLOSION_BIG = sound("explosion_big");
    public static final RegistryObject<SoundEvent> BEAM_FIRE = sound("beam_fire");
    public static final RegistryObject<SoundEvent> GUARD_BLOCK = sound("guard_block");
    public static final RegistryObject<SoundEvent> PARRY = sound("parry");
    public static final RegistryObject<SoundEvent> GUARD_BREAK = sound("guard_break");
    public static final RegistryObject<SoundEvent> DEFLECT = sound("deflect");
    public static final RegistryObject<SoundEvent> DASH = sound("dash");
    public static final RegistryObject<SoundEvent> VANISH = sound("vanish");
    public static final RegistryObject<SoundEvent> TELEPORT = sound("teleport");
    public static final RegistryObject<SoundEvent> POWERUP = sound("powerup");
    public static final RegistryObject<SoundEvent> TRANSFORM = sound("transform");
    public static final RegistryObject<SoundEvent> POWER_DOWN = sound("power_down");
    public static final RegistryObject<SoundEvent> AURA_CHARGE = sound("aura_charge");
    public static final RegistryObject<SoundEvent> AURA_HUM = sound("aura_hum");
    public static final RegistryObject<SoundEvent> AURA_CALM = sound("aura_calm");
    public static final RegistryObject<SoundEvent> KAIOKEN = sound("kaioken");
    public static final RegistryObject<SoundEvent> ZENKAI = sound("zenkai");
    public static final RegistryObject<SoundEvent> FLIGHT = sound("flight");
    public static final RegistryObject<SoundEvent> SKILL = sound("skill");
    public static final RegistryObject<SoundEvent> UI_CLICK = sound("ui_click");
    public static final RegistryObject<SoundEvent> UI_OPEN = sound("ui_open");
    public static final RegistryObject<SoundEvent> STUN = sound("stun");
    public static final RegistryObject<SoundEvent> LAND = sound("land");

    private ModSounds() {}

    private static RegistryObject<SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(DBZenith.MOD_ID, name)));
    }

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
