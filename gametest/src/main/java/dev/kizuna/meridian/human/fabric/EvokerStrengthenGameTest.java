package dev.kizuna.meridian.human.fabric;

import dev.kizuna.meridian.core.Anatomy;
import dev.kizuna.meridian.core.BodySnapshot;
import dev.kizuna.meridian.core.CapabilityModifier;
import dev.kizuna.meridian.core.CapabilityModifier.CurvePoint;
import dev.kizuna.meridian.core.CapabilityModifier.TissuePeaks;
import dev.kizuna.meridian.core.Qi;
import dev.kizuna.meridian.core.TissueCapabilities;
import dev.kizuna.meridian.protocol.pb.BodyMessages.Realm;
import dev.kizuna.meridian.technique.fabric.world.TechniqueStatusBenefits;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.CastOutcome;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueFailure;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** 强身五式：起手结束时加上状态，平台期按原稿 §5.1 的峰值倍率作用于绑定肌肉，曲线跑完前不能再起。 */
public final class EvokerStrengthenGameTest {
    private static final int TIMEOUT = 320;
    private static final double QI = 2;
    private static final double TOLERANCE = 1e-6;
    private static final long MILLIS_PER_TICK = 50;
    private static final double NEUTRAL = 1;

    /** 一式强身的施放条件、取样时刻与期望：境界、本行、表里经、起手时长、峰值与曲线。 */
    private record Strengthen(String technique, Realm realm, Qi.QiAttribute attribute, List<String> kinds,
                              long warmupMillis, TissuePeaks peaks, List<CurvePoint> curve) {
        int ticksAfterStatus(long offsetMillis) {
            // 曲线从起手结束开始计时，取样点换算成相对施放的 tick。
            return Math.toIntExact((warmupMillis + offsetMillis) / MILLIS_PER_TICK);
        }
    }

    private static final Strengthen METAL_ARMS = new Strengthen("metal_arms", Realm.WANDERING_QI,
            Qi.QiAttribute.METAL, List.of("lung", "large_intestine"), 2_600,
            new TissuePeaks(1.25, NEUTRAL, NEUTRAL, NEUTRAL, NEUTRAL),
            List.of(new CurvePoint(0, 0), new CurvePoint(1_000, 1), new CurvePoint(20_000, 1),
                    new CurvePoint(24_000, 0)));
    private static final Strengthen EARTH_LEGS = new Strengthen("earth_legs", Realm.WANDERING_QI,
            Qi.QiAttribute.EARTH, List.of("spleen", "stomach"), 3_200,
            new TissuePeaks(NEUTRAL, NEUTRAL, 1.5, NEUTRAL, NEUTRAL),
            List.of(new CurvePoint(0, 0), new CurvePoint(2_000, 1), new CurvePoint(40_000, 1),
                    new CurvePoint(45_000, 0)));
    private static final Strengthen FIRE_HEART = new Strengthen("fire_heart", Realm.TRUE_QI,
            Qi.QiAttribute.FIRE, List.of("heart", "small_intestine"), 1_800,
            new TissuePeaks(NEUTRAL, 1.4, NEUTRAL, NEUTRAL, NEUTRAL),
            List.of(new CurvePoint(0, 0), new CurvePoint(500, 1), new CurvePoint(8_000, 1),
                    new CurvePoint(10_000, -0.5), new CurvePoint(16_000, -0.5), new CurvePoint(18_000, 0)));
    private static final Strengthen WATER_RETURN = new Strengthen("water_return", Realm.TRUE_QI,
            Qi.QiAttribute.WATER, List.of("kidney", "bladder"), 2_400,
            new TissuePeaks(NEUTRAL, NEUTRAL, NEUTRAL, 3, NEUTRAL),
            List.of(new CurvePoint(0, 0), new CurvePoint(2_000, 1), new CurvePoint(15_000, 1),
                    new CurvePoint(20_000, 0)));
    private static final Strengthen WOOD_SINEW = new Strengthen("wood_sinew", Realm.ESSENCE_TRANSFORMATION,
            Qi.QiAttribute.WOOD, List.of("liver", "gallbladder"), 2_800,
            new TissuePeaks(1.2, 1.2, NEUTRAL, NEUTRAL, NEUTRAL),
            List.of(new CurvePoint(0, 0), new CurvePoint(1_000, 1), new CurvePoint(20_000, 1),
                    new CurvePoint(24_000, 0)));
    private static final long METAL_SAMPLE_MILLIS = 5_000;
    private static final long EARTH_SAMPLE_MILLIS = 10_000;
    private static final long FIRE_SAMPLE_MILLIS = 4_000;
    private static final long FIRE_EXHAUSTED_MILLIS = 12_000;
    private static final double FIRE_EXHAUSTED_BURST = 0.8;
    private static final long WATER_SAMPLE_MILLIS = 8_000;
    private static final long WOOD_SAMPLE_MILLIS = 8_000;

    /** 一块肌肉施放前的能力，作为倍率的分母。 */
    private record Baseline(Anatomy.TissueCapacity capacity, double endurance) {
    }

    /** 一次已起手的强身：绑定的肌肉、施放 tick 与施放前能力。 */
    private record Cast(EvokerTestPlayer fixture, Strengthen strengthen, List<String> muscles, long started,
                        Map<String, Baseline> baselines) {
    }

    @GameTest(maxTicks = TIMEOUT)
    public void metalArmsRaisesArmStrength(GameTestHelper context) {
        // 金臂平台期两臂力量 ×1.25，功率不变。
        start(context, METAL_ARMS, cast -> sample(context, cast, METAL_SAMPLE_MILLIS, () -> {
            assertRatios(context, cast, METAL_ARMS.peaks().strength(), NEUTRAL, NEUTRAL);
            finishCast(context, cast);
        }));
    }

    @GameTest(maxTicks = TIMEOUT)
    public void earthLegsRaisesLegEndurance(GameTestHelper context) {
        // 厚土平台期两腿耐力 ×1.5，力量与功率不变。
        start(context, EARTH_LEGS, cast -> sample(context, cast, EARTH_SAMPLE_MILLIS, () -> {
            assertRatios(context, cast, NEUTRAL, NEUTRAL, EARTH_LEGS.peaks().endurance());
            finishCast(context, cast);
        }));
    }

    @GameTest(maxTicks = TIMEOUT)
    public void fireHeartBurstsThenExhausts(GameTestHelper context) {
        // 心火平台期两臂功率 ×1.4；火退后进入虚脱段，功率降到 0.8。
        start(context, FIRE_HEART, cast -> sample(context, cast, FIRE_SAMPLE_MILLIS, () -> {
            assertRatios(context, cast, NEUTRAL, FIRE_HEART.peaks().burst(), NEUTRAL);
            assertCommon(context, cast);
            long remaining = cast.strengthen().ticksAfterStatus(FIRE_EXHAUSTED_MILLIS)
                    - cast.strengthen().ticksAfterStatus(FIRE_SAMPLE_MILLIS);
            EvokerTestPlayer.later(context, Math.toIntExact(remaining), List.of(cast.fixture()), () -> {
                assertRatios(context, cast, NEUTRAL, FIRE_EXHAUSTED_BURST, NEUTRAL);
                EvokerTestPlayer.finish(context, List.of(cast.fixture()));
            });
        }));
    }

    @GameTest(maxTicks = TIMEOUT)
    public void waterReturnRaisesLegRecovery(GameTestHelper context) {
        // 归水只改两腿恢复；Framework 不公开恢复倍率查询，倍率由修正记录的峰值核对，其余能力不变。
        start(context, WATER_RETURN, cast -> sample(context, cast, WATER_SAMPLE_MILLIS, () -> {
            assertRatios(context, cast, NEUTRAL, NEUTRAL, NEUTRAL);
            finishCast(context, cast);
        }));
    }

    @GameTest(maxTicks = TIMEOUT)
    public void woodSinewRaisesLegStrengthAndBurst(GameTestHelper context) {
        // 舒筋平台期两腿力量与功率同为 ×1.2。
        start(context, WOOD_SINEW, cast -> sample(context, cast, WOOD_SAMPLE_MILLIS, () -> {
            assertRatios(context, cast, WOOD_SINEW.peaks().strength(), WOOD_SINEW.peaks().burst(), NEUTRAL);
            finishCast(context, cast);
        }));
    }

    private static void start(GameTestHelper context, Strengthen strengthen, Consumer<Cast> next) {
        // 到达本式境界、本行表里经备足真元，记下施放前的肌肉能力后起手。
        EvokerTestPlayer.ready(context, fixture -> {
            fixture.enterRealm(strengthen.realm());
            List<String> meridians = EvokerTestPlayer.meridians(strengthen.kinds());
            fixture.setQi(meridians, strengthen.attribute(), QI, EvokerTestPlayer.FREE_DENSITY);
            BodySnapshot before = fixture.settled();
            long started = fixture.tick();
            CastOutcome outcome = fixture.cast(strengthen.technique());
            context.assertTrue(EvokerPaymentGameTest.succeeded(outcome),
                    "强身起手失败：" + strengthen.technique() + " " + outcome);
            List<String> muscles = outcome.getBinding().getSlotsList().stream()
                    .filter(slot -> slot.getSlotId().equals("muscles"))
                    .findFirst()
                    .orElseThrow()
                    .getModuleIdsList();
            context.assertTrue(!muscles.isEmpty(), "强身没有绑定肌肉：" + outcome);
            next.accept(new Cast(fixture, strengthen, muscles, started, baselines(before, muscles)));
        });
    }

    private static void sample(GameTestHelper context, Cast cast, long offsetMillis, Runnable assertion) {
        // 在状态曲线的指定偏移取样。
        int ticks = cast.strengthen().ticksAfterStatus(offsetMillis);
        EvokerTestPlayer.later(context, ticks, List.of(cast.fixture()), assertion);
    }

    private static void finishCast(GameTestHelper context, Cast cast) {
        // 平台期取样后核对共同事实并结束用例。
        assertCommon(context, cast);
        EvokerTestPlayer.finish(context, List.of(cast.fixture()));
    }

    private static void assertCommon(GameTestHelper context, Cast cast) {
        // 原版状态在身、修正记录对上原稿、曲线未完时再起被冷却拒绝、动作声照表下发。
        Strengthen strengthen = cast.strengthen();
        String id = EvokerTestPlayer.PREFIX + strengthen.technique();
        var effect = TechniqueStatusBenefits.get(id).effect();
        context.assertTrue(cast.fixture().player.hasEffect(effect), "强身状态不在玩家身上：" + id);
        assertModifiers(context, cast);
        CastOutcome recast = cast.fixture().cast(strengthen.technique());
        context.assertTrue(recast.getFailure() == TechniqueFailure.COOLING_DOWN,
                "曲线未完就能再起：" + strengthen.technique() + " " + recast);
        EvokerCues.assertTimeline(context, cast.fixture(), strengthen.technique(), cast.started());
    }

    private static void assertModifiers(GameTestHelper context, Cast cast) {
        // 每块绑定肌肉恰有一条本式修正，峰值与曲线逐点等于原稿表。
        var modifiers = cast.fixture().settled().modifiers();
        for (String muscle : cast.muscles()) {
            List<CapabilityModifier> own = modifiers.stream()
                    .filter(modifier -> modifier.target().tissueId() != null)
                    .filter(modifier -> modifier.target().tissueId().value().equals(muscle))
                    .toList();
            context.assertTrue(own.size() == 1, "肌肉修正条数不符：" + muscle + " " + own);
            CapabilityModifier modifier = own.getFirst();
            context.assertTrue(modifier.peaks().tissue().equals(cast.strengthen().peaks()),
                    "峰值不符：" + muscle + " " + modifier.peaks());
            context.assertTrue(modifier.curve().points().equals(cast.strengthen().curve()),
                    "曲线不符：" + muscle + " " + modifier.curve());
        }
    }

    private static void assertRatios(GameTestHelper context, Cast cast, double strength, double burst,
                                     double endurance) {
        // 当前可用能力除以施放前能力：力量、功率与耐力倍率逐块肌肉核对。
        BodySnapshot body = cast.fixture().settled();
        for (String muscle : cast.muscles()) {
            var tissueId = new Anatomy.TissueId(muscle);
            Baseline baseline = cast.baselines().get(muscle);
            var available = TissueCapabilities.available(body, tissueId);
            double strengthRatio = available.strengthNewtons() / baseline.capacity().strengthNewtons();
            double burstRatio = available.burstWatts() / baseline.capacity().burstWatts();
            double enduranceRatio = TissueCapabilities.enduranceMultiplier(body, tissueId) / baseline.endurance();
            context.assertTrue(Math.abs(strengthRatio - strength) < TOLERANCE, "力量倍率不符：" + muscle + " "
                    + strengthRatio);
            context.assertTrue(Math.abs(burstRatio - burst) < TOLERANCE, "功率倍率不符：" + muscle + " "
                    + burstRatio);
            context.assertTrue(Math.abs(enduranceRatio - endurance) < TOLERANCE, "耐力倍率不符：" + muscle + " "
                    + enduranceRatio);
        }
    }

    private static Map<String, Baseline> baselines(BodySnapshot body, List<String> muscles) {
        // 施放前的能力含境界与伤势影响，倍率只看本式修正。
        var baselines = new HashMap<String, Baseline>();
        for (String muscle : muscles) {
            var tissueId = new Anatomy.TissueId(muscle);
            var capacity = TissueCapabilities.available(body, tissueId);
            baselines.put(muscle, new Baseline(capacity, TissueCapabilities.enduranceMultiplier(body, tissueId)));
        }
        return baselines;
    }
}
