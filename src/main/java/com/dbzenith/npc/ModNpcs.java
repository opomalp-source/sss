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
    public static final KiFighter.Profile NAMEKIAN_PROFILE = new KiFighter.Profile("entity.dbzenith.namekian_warrior", 30, List.of(Techniques.KI_BLAST), 70);
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

    /** The training dummy (CX-19 phase 9). */
    public static final RegistryObject<EntityType<TrainingDummy>> TRAINING_DUMMY = TYPES.register("training_dummy",
            () -> EntityType.Builder.<TrainingDummy>of(TrainingDummy::new, MobCategory.MISC).sized(0.6f, 1.9f).clientTrackingRange(8).build("training_dummy"));

    public static final RegistryObject<EntityType<KiFighter>> NAMEKIAN_WARRIOR = TYPES.register("namekian_warrior",
            () -> EntityType.Builder.<KiFighter>of((t, l) -> new NeutralFighter(t, l, NAMEKIAN_PROFILE), MobCategory.CREATURE)
                    .sized(0.6f, 2.0f).clientTrackingRange(8).build("namekian_warrior"));

    public static final RegistryObject<EntityType<QuestGiverEntity>> MASTER = TYPES.register("martial_arts_master",
            () -> EntityType.Builder.<QuestGiverEntity>of((t, l) -> new QuestGiverEntity(t, l, com.dbzenith.quest.Quest.Giver.MASTER), MobCategory.CREATURE)
                    .sized(0.6f, 1.8f).clientTrackingRange(10).build("martial_arts_master"));
    public static final RegistryObject<EntityType<QuestGiverEntity>> PATROL_OFFICER = TYPES.register("patrol_officer",
            () -> EntityType.Builder.<QuestGiverEntity>of((t, l) -> new QuestGiverEntity(t, l, com.dbzenith.quest.Quest.Giver.PATROL), MobCategory.CREATURE)
                    .sized(0.6f, 1.9f).clientTrackingRange(10).build("patrol_officer"));

    // ------------------------------------------------------------------ the other world (CX-12)

    public static final KiFighter.Profile OGRE_PROFILE = new KiFighter.Profile("entity.dbzenith.ogre_guard", 0, List.of(), 0);
    public static final KiFighter.Profile DAMNED_PROFILE = new KiFighter.Profile("entity.dbzenith.damned_warrior", 40, List.of(Techniques.KI_BLAST), 70);

    private static RegistryObject<EntityType<OtherworldNpc>> otherworld(String id, OtherworldNpc.Role role, float w, float h) {
        return TYPES.register(id, () -> EntityType.Builder.<OtherworldNpc>of((t, l) -> new OtherworldNpc(t, l, role), MobCategory.MISC)
                .sized(w, h).clientTrackingRange(10).fireImmune().build(id));
    }

    public static final RegistryObject<EntityType<OtherworldNpc>> ENMA = otherworld("enma", OtherworldNpc.Role.ENMA, 1.9f, 5.8f);
    public static final RegistryObject<EntityType<OtherworldNpc>> OGRE_CLERK_RED = otherworld("ogre_clerk_red", OtherworldNpc.Role.OGRE_CLERK, 0.7f, 2.1f);
    public static final RegistryObject<EntityType<OtherworldNpc>> OGRE_CLERK_BLUE = otherworld("ogre_clerk_blue", OtherworldNpc.Role.OGRE_CLERK, 0.7f, 2.1f);
    public static final RegistryObject<EntityType<OtherworldNpc>> NORTH_KAI = otherworld("north_kai", OtherworldNpc.Role.NORTH_KAI, 0.6f, 1.6f);
    public static final RegistryObject<EntityType<OtherworldNpc>> GRAND_KAI = otherworld("grand_kai", OtherworldNpc.Role.GRAND_KAI, 0.6f, 2.0f);
    public static final RegistryObject<EntityType<OtherworldNpc>> BEERUS = otherworld("beerus", OtherworldNpc.Role.BEERUS, 0.6f, 1.9f);
    public static final RegistryObject<EntityType<OtherworldNpc>> WHIS = otherworld("whis", OtherworldNpc.Role.WHIS, 0.6f, 2.1f);
    public static final RegistryObject<EntityType<TrainingMonkey>> TRAINING_MONKEY = TYPES.register("training_monkey",
            () -> EntityType.Builder.<TrainingMonkey>of(TrainingMonkey::new, MobCategory.MISC).sized(0.5f, 0.9f).clientTrackingRange(10).build("training_monkey"));
    public static final RegistryObject<EntityType<TrainingCricket>> TRAINING_CRICKET = TYPES.register("training_cricket",
            () -> EntityType.Builder.<TrainingCricket>of(TrainingCricket::new, MobCategory.MISC).sized(0.35f, 0.3f).clientTrackingRange(10).build("training_cricket"));
    public static final RegistryObject<EntityType<KiFighter>> OGRE_GUARD = TYPES.register("ogre_guard",
            () -> EntityType.Builder.<KiFighter>of((t, l) -> new NeutralFighter(t, l, OGRE_PROFILE), MobCategory.CREATURE)
                    .sized(0.8f, 2.4f).clientTrackingRange(10).fireImmune().build("ogre_guard"));
    public static final RegistryObject<EntityType<KiFighter>> DAMNED_WARRIOR = TYPES.register("damned_warrior",
            () -> EntityType.Builder.<KiFighter>of((t, l) -> new KiFighter(t, l, DAMNED_PROFILE), MobCategory.MONSTER)
                    .sized(0.6f, 1.9f).clientTrackingRange(8).fireImmune().build("damned_warrior"));

    // ------------------------------------------------------------------ the World Martial Arts Tournament (CX-17c)

    public static final RegistryObject<EntityType<OtherworldNpc>> TOURNAMENT_ANNOUNCER = otherworld("tournament_announcer", OtherworldNpc.Role.ANNOUNCER, 0.6f, 1.9f);
    public static final java.util.Map<com.dbzenith.tournament.Roster, RegistryObject<EntityType<com.dbzenith.tournament.TournamentFighter>>> TOURNAMENT_FIGHTERS = new java.util.EnumMap<>(com.dbzenith.tournament.Roster.class);

    static {
        for (com.dbzenith.tournament.Roster r : com.dbzenith.tournament.Roster.values()) {
            float w = r == com.dbzenith.tournament.Roster.SPOPOVICH ? 0.7f : 0.6f, h = r == com.dbzenith.tournament.Roster.SPOPOVICH ? 2.2f : r == com.dbzenith.tournament.Roster.YAMU ? 1.7f : 1.9f;
            TOURNAMENT_FIGHTERS.put(r, TYPES.register(r.id(), () -> EntityType.Builder.<com.dbzenith.tournament.TournamentFighter>of(
                    (t, l) -> new com.dbzenith.tournament.TournamentFighter(t, l, r), MobCategory.MISC).sized(w, h).clientTrackingRange(10).build(r.id())));
        }
    }

    public static EntityType<com.dbzenith.tournament.TournamentFighter> tournamentFighter(com.dbzenith.tournament.Roster r) {
        return TOURNAMENT_FIGHTERS.get(r).get();
    }

    private ModNpcs() {}

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(SPROUTLING.get(), KiFighter.attributes(16, 3, 0.32).build());
        event.put(KI_SOLDIER.get(), KiFighter.attributes(30, 4, 0.27).build());
        event.put(TRAINING_DUMMY.get(), TrainingDummy.attributes().build());
        event.put(ANDROID_UNIT.get(), KiFighter.attributes(60, 6, 0.24).add(Attributes.ARMOR, 8).build());
        event.put(TYRANT_LORD.get(), KiFighter.attributes(400, 6, 0.3).add(Attributes.KNOCKBACK_RESISTANCE, 0.6).build());
        event.put(RAMPAGE_BRUTE.get(), KiFighter.attributes(500, 9, 0.27).add(Attributes.KNOCKBACK_RESISTANCE, 0.9).build());
        event.put(NAMEKIAN_WARRIOR.get(), KiFighter.attributes(50, 5, 0.25).build());
        event.put(MASTER.get(), net.minecraft.world.entity.Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0.2).add(Attributes.MAX_HEALTH, 100).build());
        event.put(PATROL_OFFICER.get(), net.minecraft.world.entity.Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0.2).add(Attributes.MAX_HEALTH, 100).build());
        var resident = net.minecraft.world.entity.Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0.2).add(Attributes.MAX_HEALTH, 200).build();
        for (var t : List.of(ENMA, OGRE_CLERK_RED, OGRE_CLERK_BLUE, NORTH_KAI, GRAND_KAI, BEERUS, WHIS, TOURNAMENT_ANNOUNCER)) event.put(t.get(), resident);
        event.put(TRAINING_MONKEY.get(), net.minecraft.world.entity.Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0.34).add(Attributes.MAX_HEALTH, 40).build());
        event.put(TRAINING_CRICKET.get(), net.minecraft.world.entity.Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0.3).add(Attributes.MAX_HEALTH, 10).build());
        event.put(OGRE_GUARD.get(), KiFighter.attributes(220, 10, 0.25).add(Attributes.KNOCKBACK_RESISTANCE, 0.7).build());
        event.put(DAMNED_WARRIOR.get(), KiFighter.attributes(90, 8, 0.29).build());
        for (var t : TOURNAMENT_FIGHTERS.values()) event.put(t.get(), KiFighter.attributes(120, 8, 0.3).add(Attributes.KNOCKBACK_RESISTANCE, 0.3).build());
    }

    @SubscribeEvent
    public static void spawnPlacements(SpawnPlacementRegisterEvent event) {
        for (var type : List.of(SPROUTLING, KI_SOLDIER, ANDROID_UNIT)) {
            event.register(type.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    Monster::checkMonsterSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
        }
        event.register(DAMNED_WARRIOR.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (t, l, r, p, rnd) -> l.getBlockState(p.below()).isSolid(), SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(NAMEKIAN_WARRIOR.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                net.minecraft.world.entity.Mob::checkMobSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
    }
}
