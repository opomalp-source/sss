package com.dbzenith.npc;

import com.dbzenith.DBZenith;
import com.dbzenith.skill.Techniques;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/** Enemy fighters and bosses (original designs). Natural spawns are added by biome modifiers in data/dbzenith/forge. */
@Mod.EventBusSubscriber(modid = DBZenith.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModNpcs {
    public static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, DBZenith.MOD_ID);

    public static final KiFighter.Profile SPROUTLING_PROFILE = new KiFighter.Profile("entity.dbzenith.sproutling", 0, List.of(), 0);
    public static final KiFighter.Profile SOLDIER_PROFILE = new KiFighter.Profile("entity.dbzenith.ki_soldier", 25, List.of(Techniques.KI_BLAST), 60);
    public static final KiFighter.Profile ANDROID_PROFILE = new KiFighter.Profile("entity.dbzenith.android_unit", 35, List.of(Techniques.ARM_CANNON), 80);
    public static final KiFighter.Profile TYRANT_PROFILE = new KiFighter.Profile("entity.dbzenith.tyrant_lord", 90,
            List.of(Techniques.FINGER_BEAM, Techniques.SUPERNOVA_ORB, Techniques.KI_BLAST, Techniques.SEAL_ORB), 50);
    public static final KiFighter.Profile BRUTE_PROFILE = new KiFighter.Profile("entity.dbzenith.rampage_brute", 70,
            List.of(Techniques.WAVE_BEAM, Techniques.RAPID_VOLLEY), 70);

    public static final RegistryObject<EntityType<KiFighter>> SPROUTLING = TYPES.register("sproutling",
            () -> EntityType.Builder.<KiFighter>of((t, l) -> new KiFighter(t, l, SPROUTLING_PROFILE), MobCategory.MONSTER)
                    .sized(0.5f, 1.3f).clientTrackingRange(8).build("sproutling"));
    public static final RegistryObject<EntityType<KiFighter>> KI_SOLDIER = TYPES.register("ki_soldier",
            () -> EntityType.Builder.<KiFighter>of((t, l) -> new KiFighter(t, l, SOLDIER_PROFILE), MobCategory.MONSTER)
                    .sized(0.6f, 1.9f).clientTrackingRange(8).build("ki_soldier"));
    public static final RegistryObject<EntityType<KiFighter>> ANDROID_UNIT = TYPES.register("android_unit",
            () -> EntityType.Builder.<KiFighter>of((t, l) -> new KiFighter(t, l, ANDROID_PROFILE), MobCategory.MONSTER)
                    .sized(0.6f, 1.95f).clientTrackingRange(8).build("android_unit"));
    public static final RegistryObject<EntityType<BossFighter>> TYRANT_LORD = TYPES.register("tyrant_lord",
            () -> EntityType.Builder.<BossFighter>of((t, l) -> new BossFighter(t, l, TYRANT_PROFILE, BossEvent.BossBarColor.PURPLE, 0xC890FF),
                    MobCategory.MONSTER).sized(0.7f, 2.1f).clientTrackingRange(10).fireImmune().build("tyrant_lord"));
    public static final RegistryObject<EntityType<BossFighter>> RAMPAGE_BRUTE = TYPES.register("rampage_brute",
            () -> EntityType.Builder.<BossFighter>of((t, l) -> new BossFighter(t, l, BRUTE_PROFILE, BossEvent.BossBarColor.RED, 0xFF5030),
                    MobCategory.MONSTER).sized(1.2f, 3.0f).clientTrackingRange(10).build("rampage_brute"));

    public static final RegistryObject<EntityType<QuestGiverEntity>> MASTER = TYPES.register("martial_arts_master",
            () -> EntityType.Builder.<QuestGiverEntity>of((t, l) -> new QuestGiverEntity(t, l, com.dbzenith.quest.Quest.Giver.MASTER), MobCategory.CREATURE)
                    .sized(0.6f, 1.8f).clientTrackingRange(10).build("martial_arts_master"));
    public static final RegistryObject<EntityType<QuestGiverEntity>> PATROL_OFFICER = TYPES.register("patrol_officer",
            () -> EntityType.Builder.<QuestGiverEntity>of((t, l) -> new QuestGiverEntity(t, l, com.dbzenith.quest.Quest.Giver.PATROL), MobCategory.CREATURE)
                    .sized(0.6f, 1.9f).clientTrackingRange(10).build("patrol_officer"));

    private ModNpcs() {}

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(SPROUTLING.get(), KiFighter.attributes(16, 3, 0.32).build());
        event.put(KI_SOLDIER.get(), KiFighter.attributes(30, 4, 0.27).build());
        event.put(ANDROID_UNIT.get(), KiFighter.attributes(60, 6, 0.24).add(Attributes.ARMOR, 8).build());
        event.put(TYRANT_LORD.get(), KiFighter.attributes(400, 10, 0.3).add(Attributes.KNOCKBACK_RESISTANCE, 0.6).build());
        event.put(RAMPAGE_BRUTE.get(), KiFighter.attributes(600, 14, 0.27).add(Attributes.KNOCKBACK_RESISTANCE, 0.9).build());
        event.put(MASTER.get(), net.minecraft.world.entity.Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0.2).add(Attributes.MAX_HEALTH, 100).build());
        event.put(PATROL_OFFICER.get(), net.minecraft.world.entity.Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0.2).add(Attributes.MAX_HEALTH, 100).build());
    }

    @SubscribeEvent
    public static void spawnPlacements(SpawnPlacementRegisterEvent event) {
        for (var type : List.of(SPROUTLING, KI_SOLDIER, ANDROID_UNIT)) {
            event.register(type.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    Monster::checkMonsterSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
        }
    }
}
