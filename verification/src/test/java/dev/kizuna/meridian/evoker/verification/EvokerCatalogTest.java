package dev.kizuna.meridian.evoker.verification;

import dev.kizuna.meridian.protocol.pb.BodyMessages.CurvePoint;
import dev.kizuna.meridian.protocol.pb.BodyMessages.QiPhase;
import dev.kizuna.meridian.protocol.pb.BodyMessages.Realm;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.Category;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.CastPhases;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.ModuleKind;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.OrganRefine;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.QiCost;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.QiCostMatching;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.QiDistribution;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.StatusBenefit;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueDefinition;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueEffect;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueSlot;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.WorkCost;
import dev.kizuna.meridian.technique.server.TechniqueRegistry;
import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** 发行目录经正式 Human 内容和 Technique 校验器注册，并逐式对照原稿 §3、§5.1、§5.2 的表。 */
public final class EvokerCatalogTest {
    private static final String BOOK = "meridian_evoker:five_organs";
    private static final String VOLUME = "meridian_evoker:nourish";
    private static final int TECHNIQUES = 10;
    private static final double STRENGTHEN_MOVEMENT = 0.4;
    private static final double REFINE_MOVEMENT = 0;
    private static final long REFINE_START_MILLIS = 1_000;
    private static final long REFINE_WINDOW_MILLIS = 30_000;
    private static final long REFINE_BATCH_MILLIS = 2_000;
    private static final long REFINE_WARMUP_MILLIS = 31_000;
    private static final long REFINE_RECOVERY_MILLIS = 1_000;
    private static final long REFINE_COOLDOWN_MILLIS = 30_000;
    private static final double REFINE_WORK_SECONDS = 30;
    private static final int REFINE_BREATHS = 6;
    private static final int SOUNDS_PER_BREATH = 3;
    private static final double TOLERANCE = 1e-9;

    /** 强身一式：行、最低境界、本脏数、肌肉标签、本行真元、本脏功率与时长、冷却。 */
    private record Strengthen(String element, Realm realm, int zang, String muscles, double qi, double watts,
                              double seconds, long cooldownMillis) {
    }

    /** 炼元一式：行、最低境界、本脏数、每个本脏的功率。 */
    private record Refine(String element, Realm realm, int zang, double watts) {
    }

    private static final Map<String, Strengthen> STRENGTHEN = Map.of(
            "meridian_evoker:metal_arms",
            new Strengthen("metal", Realm.WANDERING_QI, 2, "human:arm_muscle", 0.3, 10, 2, 22_000),
            "meridian_evoker:earth_legs",
            new Strengthen("earth", Realm.WANDERING_QI, 1, "human:leg_muscle", 0.3, 12, 2, 43_000),
            "meridian_evoker:fire_heart",
            new Strengthen("fire", Realm.TRUE_QI, 1, "human:arm_muscle", 0.4, 20, 1.5, 16_000),
            "meridian_evoker:water_return",
            new Strengthen("water", Realm.TRUE_QI, 2, "human:leg_muscle", 0.3, 10, 2, 18_000),
            "meridian_evoker:wood_sinew",
            new Strengthen("wood", Realm.ESSENCE_TRANSFORMATION, 1, "human:leg_muscle", 0.3, 12, 2, 22_000));

    private static final Map<String, Refine> REFINE = Map.of(
            "meridian_evoker:refine_lung", new Refine("metal", Realm.WANDERING_QI, 2, 5),
            "meridian_evoker:refine_spleen", new Refine("earth", Realm.WANDERING_QI, 1, 8),
            "meridian_evoker:refine_heart", new Refine("fire", Realm.TRUE_QI, 1, 10),
            "meridian_evoker:refine_kidney", new Refine("water", Realm.TRUE_QI, 2, 5),
            "meridian_evoker:refine_liver", new Refine("wood", Realm.ESSENCE_TRANSFORMATION, 1, 8));

    @Test
    public void catalogRegistersOneVolumeOfTenTechniques() throws IOException {
        // 只有上卷进目录；卷内十式全部登记，书卷 ID 照原稿 §3。
        TechniqueRegistry registry = EvokerContent.registry();
        assertEquals(1, registry.books().size());
        assertEquals(BOOK, registry.books().getFirst().getId());
        assertEquals(1, registry.volumes().size());
        var volume = registry.findVolume(VOLUME).orElseThrow();
        assertEquals(BOOK, volume.getBookId());
        assertEquals(TECHNIQUES, volume.getTechniquesCount());
        assertEquals(TECHNIQUES, registry.definitions().size());
        assertEquals(STRENGTHEN.size() + REFINE.size(), TECHNIQUES);
    }

    @Test
    public void strengthenPaysElementQiAndOrganWorkAndRunsCurveWithinCooldown() throws IOException {
        // 强身付本行真元、本脏做功；状态在起手结束时加上，曲线跑完前冷却未到。
        TechniqueRegistry registry = EvokerContent.registry();
        for (var entry : STRENGTHEN.entrySet()) {
            TechniqueDefinition definition = registry.findLatest(entry.getKey()).orElseThrow();
            Strengthen expected = entry.getValue();
            assertEquals(Category.BUFF, definition.getCategory());
            assertEquals(expected.realm(), definition.getMinimumRealm());
            assertEquals(STRENGTHEN_MOVEMENT, definition.getMovementSpeedMultiplier(), TOLERANCE);
            assertZangAndChannels(definition, expected.element(), expected.zang());
            assertEquals(expected.muscles(), slot(definition, "muscles").getTags(0));

            QiCost qi = definition.getCost().getQi(0);
            assertEquals("channels", qi.getSlotId());
            assertEquals("meridian:" + expected.element(), qi.getAttribute());
            assertEquals(QiPhase.FREE, qi.getPhase());
            assertEquals(QiCostMatching.ATTRIBUTE_AT_LEAST_PHASE, qi.getMatching());
            assertEquals(QiDistribution.BY_BALANCE, qi.getDistribution());
            assertEquals(expected.qi(), qi.getAmount().getValue(), TOLERANCE);
            assertWork(definition.getCost().getWork(0), expected.watts(), expected.seconds());
            assertEquals(expected.cooldownMillis(), definition.getCooldownMillis());
            assertZeroAiWeight(definition);
            assertStatusWithinCooldown(definition);
        }
    }

    @Test
    public void refineRunsOrganRefineInsideWarmupWithoutQiCost() throws IOException {
        // 炼元不扣真元，只有本脏做功；炼化窗口放在起手里，每 2 s 一批。
        TechniqueRegistry registry = EvokerContent.registry();
        for (var entry : REFINE.entrySet()) {
            TechniqueDefinition definition = registry.findLatest(entry.getKey()).orElseThrow();
            Refine expected = entry.getValue();
            assertEquals(Category.CULTIVATION, definition.getCategory());
            assertEquals(expected.realm(), definition.getMinimumRealm());
            assertEquals(REFINE_MOVEMENT, definition.getMovementSpeedMultiplier(), TOLERANCE);
            assertZangAndChannels(definition, expected.element(), expected.zang());
            assertEquals(0, definition.getCost().getQiCount());
            assertWork(definition.getCost().getWork(0), expected.watts(), REFINE_WORK_SECONDS);
            CastPhases phases = definition.getPhases();
            assertEquals(REFINE_WARMUP_MILLIS, phases.getWarmupMillis());
            assertEquals(0, phases.getActiveMillis());
            assertEquals(REFINE_RECOVERY_MILLIS, phases.getRecoveryMillis());
            assertTrue(phases.getInterruptibleByDamage());
            assertEquals(REFINE_COOLDOWN_MILLIS, definition.getCooldownMillis());
            assertZeroAiWeight(definition);

            TechniqueEffect first = definition.getEffects(0);
            assertEquals(REFINE_START_MILLIS, first.getAtMillis());
            OrganRefine refine = first.getOrganRefine();
            assertEquals("zang", refine.getOrganSlotId());
            assertEquals("channels", refine.getMeridianSlotId());
            assertEquals(EvokerContent.RECIPE_FAMILY, refine.getRecipeFamily());
            assertEquals(REFINE_BATCH_MILLIS, refine.getBatchMillis());
            assertEquals(REFINE_WINDOW_MILLIS, refine.getDurationMillis());
            long sounds = definition.getEffectsList().stream().filter(TechniqueEffect::hasSound).count();
            assertEquals(REFINE_BREATHS * SOUNDS_PER_BREATH, sounds);
        }
    }

    private static void assertZangAndChannels(TechniqueDefinition definition, String element, int zang) {
        // 本脏按 zang 标签，成对的脏允许坏一个；本行表里经至少开一条。
        TechniqueSlot organs = slot(definition, "zang");
        assertEquals(ModuleKind.ORGAN, organs.getModule());
        assertEquals(List.of("human:zang_" + element), organs.getTagsList());
        assertEquals(1, organs.getMinimumCount());
        assertEquals(zang, organs.getMaximumCount());
        TechniqueSlot channels = slot(definition, "channels");
        assertEquals(ModuleKind.MERIDIAN, channels.getModule());
        assertEquals(List.of("human:meridian_" + element), channels.getTagsList());
        assertTrue(channels.getRequireOpened());
        assertEquals(1, channels.getMinimumCount());
    }

    private static void assertWork(WorkCost work, double watts, double seconds) {
        // 本脏做功只填功率与时长，器官不声明力量。
        assertEquals("zang", work.getSlotId());
        assertFalse(work.hasForce());
        assertEquals(watts, work.getPower().getValue(), TOLERANCE);
        assertEquals(seconds, work.getDuration().getValue(), TOLERANCE);
    }

    private static void assertZeroAiWeight(TechniqueDefinition definition) {
        // C6a 先不让生物选这十式，C6c 再按 §8.4 定。
        assertTrue(definition.hasAiHints());
        assertEquals(0, definition.getAiHints().getCategoryWeight().getValue(), TOLERANCE);
    }

    private static void assertStatusWithinCooldown(TechniqueDefinition definition) {
        // 状态在起手结束时加上；效果开始加曲线长度不超过施放时长加冷却。
        CastPhases phases = definition.getPhases();
        long warmup = phases.getWarmupMillis();
        long castMillis = warmup + phases.getActiveMillis() + phases.getRecoveryMillis();
        TechniqueEffect effect = definition.getEffectsList().stream()
                .filter(TechniqueEffect::hasStatusBenefit)
                .findFirst()
                .orElseThrow();
        assertEquals(warmup, effect.getAtMillis());
        StatusBenefit status = effect.getStatusBenefit();
        assertEquals(definition.getId(), status.getId());
        List<CurvePoint> curve = status.getTissue().getModifiers(0).getCurveList();
        long curveEnd = curve.getLast().getOffsetMillis();
        assertEquals(curveEnd, status.getDurationMillis());
        assertTrue(definition.getId(), warmup + curveEnd <= castMillis + definition.getCooldownMillis());
    }

    private static TechniqueSlot slot(TechniqueDefinition definition, String id) {
        // 槽位按局部 ID 查找，缺少时直接失败。
        return definition.getSlotsList().stream()
                .filter(slot -> slot.getId().equals(id))
                .findFirst()
                .orElseThrow();
    }
}
