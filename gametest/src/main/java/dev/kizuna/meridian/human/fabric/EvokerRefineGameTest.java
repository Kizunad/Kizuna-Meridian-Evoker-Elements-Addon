package dev.kizuna.meridian.human.fabric;

import dev.kizuna.meridian.core.BodySnapshot;
import dev.kizuna.meridian.core.Qi;
import dev.kizuna.meridian.human.server.HumanCultivation;
import dev.kizuna.meridian.human.server.HumanOrganRefining;
import dev.kizuna.meridian.protocol.pb.BodyMessages.Realm;
import dev.kizuna.meridian.protocol.pb.MeridianMessages.CommandReceipt;
import dev.kizuna.meridian.technique.fabric.TechniqueFabric;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.CastOutcome;
import dev.kizuna.meridian.technique.server.TechniqueService;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 炼元五式：每 2 s 起一批，投料受缓存与吞吐所限，按境界炼化率送回本脏经，余量释放，全身真元守恒。 */
public final class EvokerRefineGameTest {
    private static final int BATCH_TIMEOUT = 320;
    private static final int SOUND_TIMEOUT = 660;
    private static final int FIRST_BATCH_TICK = 20;
    private static final int BATCH_TICKS = 40;
    private static final int BATCH_TOLERANCE_TICKS = 1;
    private static final int LUNG_BATCHES = 7;
    private static final int LUNG_COMPLETED = 6;
    private static final int LUNG_SAMPLE_TICKS = 270;
    private static final int FIRST_RETURN_TICKS = 65;
    private static final int HEART_SAMPLE_TICKS = 70;
    private static final int STAGGER_TICKS = 25;
    private static final int INTERRUPTED_SAMPLE_TICKS = 75;
    private static final int BREATH_STOPPED_TICKS = 25;
    private static final int BREATH_OBSERVED_TICKS = 80;
    private static final int RESERVE_FILL_TICKS = 25;
    private static final int RESERVE_OBSERVED_TICKS = 150;
    private static final int SOUND_SAMPLE_TICKS = 610;
    private static final double LUNG_OCCUPANCY = 0.85;
    private static final double SPLEEN_OCCUPANCY = 0.7;
    private static final double THROUGHPUT_INPUT = 2;
    private static final double FREE_BUFFER_INPUT = 1;
    private static final double FREE_RATE = 0.08;
    private static final double GAS_RATE = 0.20;
    private static final double TOLERANCE = 1e-9;
    private static final double LEDGER_TOLERANCE = 1e-6;
    private static final long STAGGER_MILLIS = 500;
    private static final double STAGGER_SPEED = 0.5;
    private static final List<String> LUNGS = List.of("left_lung", "right_lung");
    private static final List<String> LUNG_MERIDIANS = EvokerTestPlayer.meridians(List.of("lung"));
    private static final List<String> REFINES = List.of(
            "refine_lung", "refine_spleen", "refine_heart", "refine_kidney", "refine_liver");

    /** 一批开始的观察：相对施放的 tick 与投料量。 */
    private record Batch(long tick, double input) {
    }

    /** 每 tick 读身体，按器官记下每一批新起的加工；加工完成时刻变了就是新的一批。 */
    private static final class BatchLog {
        private final EvokerTestPlayer fixture;
        private final long started;
        private final Map<String, Long> completions = new HashMap<>();
        private final Map<String, List<Batch>> batches = new HashMap<>();

        BatchLog(EvokerTestPlayer fixture, long started, List<String> organIds) {
            // 只观察不结算，记录的是生产路径自己提交的加工。
            this.fixture = fixture;
            this.started = started;
            organIds.forEach(organId -> batches.put(organId, new ArrayList<>()));
            fixture.everyTick(this::observe);
        }

        private void observe() {
            // 读当前快照里每个器官的加工，完成时刻是这一批的标识。
            BodySnapshot snapshot = fixture.body().snapshot();
            for (var entry : batches.entrySet()) {
                String organId = entry.getKey();
                var process = EvokerTestPlayer.organ(snapshot, organId).process();
                if (process == null || Objects.equals(completions.get(organId), process.completionMillis())) {
                    continue;
                }
                completions.put(organId, process.completionMillis());
                entry.getValue().add(new Batch(fixture.tick() - started, process.input().totalQi()));
            }
        }

        List<Batch> of(String organId) {
            // 只读副本。
            return List.copyOf(batches.get(organId));
        }
    }

    @GameTest(maxTicks = BATCH_TIMEOUT)
    public void lungRefinesEveryTwoSecondsAndConservesQi(GameTestHelper context) {
        // 游炁两肺：每批各取 2 元游离无属性真元，3% 炼成金行送回肺经，其余释放；身体加释放的总量不变。
        EvokerTestPlayer.ready(context, fixture -> {
            fixture.fillEvenly(LUNG_OCCUPANCY, EvokerTestPlayer.FREE_DENSITY);
            double before = fixture.settled().totalQi();
            Set<String> receipts = fixture.receiptKeys();
            long started = fixture.tick();
            CastOutcome outcome = fixture.cast("refine_lung");
            context.assertTrue(EvokerPaymentGameTest.succeeded(outcome), "肺炼起手失败：" + outcome);
            var log = new BatchLog(fixture, started, LUNGS);
            double perBatch = THROUGHPUT_INPUT * FREE_RATE * LUNGS.size();
            EvokerTestPlayer.later(context, FIRST_RETURN_TICKS, List.of(fixture), () -> {
                double returned = EvokerTestPlayer.meridianQi(fixture.settled(), LUNG_MERIDIANS, Qi.QiAttribute.METAL);
                context.assertTrue(Math.abs(returned - perBatch) < LEDGER_TOLERANCE,
                        "第一批产物没有送回肺经：" + returned);
            });
            EvokerTestPlayer.later(context, LUNG_SAMPLE_TICKS, List.of(fixture), () -> {
                for (String lung : LUNGS) {
                    assertBatches(context, log.of(lung), LUNG_BATCHES, THROUGHPUT_INPUT);
                }
                BodySnapshot body = fixture.settled();
                double metal = EvokerTestPlayer.attributeQi(body, Qi.QiAttribute.METAL);
                context.assertTrue(Math.abs(metal - perBatch * LUNG_COMPLETED) < TOLERANCE, "金行产量不符：" + metal);
                assertLedger(context, fixture, receipts, before);
                EvokerTestPlayer.finish(context, List.of(fixture));
            });
        });
    }

    @GameTest(maxTicks = BATCH_TIMEOUT)
    public void heartTakesThroughputBoundGasBatchAtTrueRate(GameTestHelper context) {
        // 真炁心：缓存能装 10 元气态真元，投料受吞吐限为 2 元，7.5% 炼成火行。
        EvokerTestPlayer.ready(context, fixture -> {
            fixture.enterRealm(Realm.TRUE_QI);
            fixture.fillEvenly(LUNG_OCCUPANCY, EvokerTestPlayer.GAS_DENSITY);
            long started = fixture.tick();
            CastOutcome outcome = fixture.cast("refine_heart");
            context.assertTrue(EvokerPaymentGameTest.succeeded(outcome), "心炼起手失败：" + outcome);
            var log = new BatchLog(fixture, started, List.of("heart"));
            EvokerTestPlayer.later(context, HEART_SAMPLE_TICKS, List.of(fixture), () -> {
                List<Batch> batches = log.of("heart");
                context.assertTrue(!batches.isEmpty(), "心炼没有起批");
                assertBatch(context, batches.getFirst(), 0, THROUGHPUT_INPUT);
                double fire = EvokerTestPlayer.attributeQi(fixture.settled(), Qi.QiAttribute.FIRE);
                context.assertTrue(Math.abs(fire - THROUGHPUT_INPUT * GAS_RATE) < TOLERANCE, "火行产量不符：" + fire);
                EvokerTestPlayer.finish(context, List.of(fixture));
            });
        });
    }

    @GameTest(maxTicks = BATCH_TIMEOUT)
    public void interruptedSpleenFinishesItsBatchWithoutAnother(GameTestHelper context) {
        // 游炁脾缓存只装 1 元；第一批加工中被受击打断：这一批照常完成送回，之后不再起批，任务清除。
        EvokerTestPlayer.ready(context, fixture -> {
            fixture.fillEvenly(SPLEEN_OCCUPANCY, EvokerTestPlayer.FREE_DENSITY);
            long started = fixture.tick();
            CastOutcome outcome = fixture.cast("refine_spleen");
            context.assertTrue(EvokerPaymentGameTest.succeeded(outcome), "脾炼起手失败：" + outcome);
            var log = new BatchLog(fixture, started, List.of("spleen"));
            EvokerTestPlayer.later(context, STAGGER_TICKS, List.of(fixture), () -> {
                var stagger = new TechniqueService.Stagger(STAGGER_MILLIS, STAGGER_SPEED);
                var interrupted = TechniqueFabric.service().stagger(fixture.player.getUUID(), stagger);
                context.assertTrue(interrupted.isPresent(), "受击没有打断脾炼");
            });
            EvokerTestPlayer.later(context, INTERRUPTED_SAMPLE_TICKS, List.of(fixture), () -> {
                BodySnapshot body = fixture.settled();
                List<Batch> batches = log.of("spleen");
                context.assertTrue(batches.size() == 1, "打断后仍在起批：" + batches);
                assertBatch(context, batches.getFirst(), 0, FREE_BUFFER_INPUT);
                double earth = EvokerTestPlayer.attributeQi(body, Qi.QiAttribute.EARTH);
                context.assertTrue(Math.abs(earth - FREE_BUFFER_INPUT * FREE_RATE) < TOLERANCE,
                        "被打断的一批没有完成：" + earth);
                context.assertTrue(HumanOrganRefining.state(body).getTasksCount() == 0, "炼化任务没有清除");
                EvokerTestPlayer.finish(context, List.of(fixture));
            });
        });
    }

    @GameTest(maxTicks = BATCH_TIMEOUT)
    public void lungRefineStopsBreathing(GameTestHelper context) {
        // 吐纳中起肺炼：第一批取料前吐纳停下，此后没有新的环境摄入。
        EvokerTestPlayer.ready(context, fixture -> {
            fixture.fillEvenly(SPLEEN_OCCUPANCY, EvokerTestPlayer.FREE_DENSITY);
            HumanGameTestAccess.breathe(fixture.player);
            context.assertTrue(fixture.body().breathingActive(), "测试吐纳没有开始");
            CastOutcome outcome = fixture.cast("refine_lung");
            context.assertTrue(EvokerPaymentGameTest.succeeded(outcome), "吐纳中肺炼起手失败：" + outcome);
            var stopped = new ArrayList<Set<String>>();
            EvokerTestPlayer.later(context, BREATH_STOPPED_TICKS, List.of(fixture), () -> {
                fixture.settled();
                context.assertTrue(!fixture.body().breathingActive() && !fixture.body().breathingAutomatic(),
                        "肺炼开始后吐纳仍在进行");
                stopped.add(fixture.receiptKeys());
            });
            EvokerTestPlayer.later(context, BREATH_OBSERVED_TICKS, List.of(fixture), () -> {
                fixture.settled();
                List<CommandReceipt> intake = fixture.receiptsSince(stopped.getFirst()).stream()
                        .filter(receipt -> receipt.hasQiCapture() || receipt.hasOrganIntake())
                        .toList();
                context.assertTrue(intake.isEmpty(), "肺炼期间仍有环境摄入：" + intake.size());
                EvokerTestPlayer.finish(context, List.of(fixture));
            });
        });
    }

    @GameTest(maxTicks = BATCH_TIMEOUT)
    public void spleenRefinePausesReserveDeposit(GameTestHelper context) {
        // 两人内腔同时注满：未炼的对照组中焦储备开始入库，脾炼中的一方整段不入库。
        EvokerTestPlayer.ready(context, 2, players -> {
            EvokerTestPlayer refiner = players.getFirst();
            EvokerTestPlayer control = players.getLast();
            players.forEach(fixture -> fixture.fillEvenly(SPLEEN_OCCUPANCY, EvokerTestPlayer.FREE_DENSITY));
            CastOutcome outcome = refiner.cast("refine_spleen");
            context.assertTrue(EvokerPaymentGameTest.succeeded(outcome), "脾炼起手失败：" + outcome);
            var deposited = new HashMap<EvokerTestPlayer, Boolean>();
            EvokerTestPlayer.later(context, RESERVE_FILL_TICKS, players, () -> {
                for (EvokerTestPlayer fixture : players) {
                    fixture.fillLumens();
                    deposited.put(fixture, false);
                    fixture.everyTick(() -> {
                        if (HumanCultivation.state(fixture.settled()).getReserveProcessing()) {
                            deposited.put(fixture, true);
                        }
                    });
                }
            });
            EvokerTestPlayer.later(context, RESERVE_OBSERVED_TICKS, players, () -> {
                context.assertTrue(deposited.get(control), "对照组没有开始中焦入库，用例前提不成立");
                context.assertTrue(!deposited.get(refiner), "脾炼期间中焦储备仍在入库");
                EvokerTestPlayer.finish(context, players);
            });
        });
    }

    @GameTest(maxTicks = SOUND_TIMEOUT)
    public void refineBreathCuesFollowTheTable(GameTestHelper context) {
        // 五人化元同时各起一式炼元：六轮吸气、吐字与本行提示音都在表定时刻、按玩家类别下发。
        EvokerTestPlayer.ready(context, REFINES.size(), players -> {
            long started = players.getFirst().tick();
            for (int index = 0; index < REFINES.size(); index++) {
                EvokerTestPlayer fixture = players.get(index);
                fixture.enterRealm(Realm.ESSENCE_TRANSFORMATION);
                fixture.fillEvenly(SPLEEN_OCCUPANCY, EvokerTestPlayer.LIQUID_DENSITY);
                CastOutcome outcome = fixture.cast(REFINES.get(index));
                context.assertTrue(EvokerPaymentGameTest.succeeded(outcome),
                        "炼元起手失败：" + REFINES.get(index) + " " + outcome);
            }
            EvokerTestPlayer.later(context, SOUND_SAMPLE_TICKS, players, () -> {
                for (int index = 0; index < REFINES.size(); index++) {
                    EvokerCues.assertTimeline(context, players.get(index), REFINES.get(index), started);
                }
                EvokerTestPlayer.finish(context, players);
            });
        });
    }

    private static void assertBatches(GameTestHelper context, List<Batch> batches, int count, double input) {
        // 起批时刻从 1 s 起每 2 s 一次，每批投料相同。
        context.assertTrue(batches.size() == count, "起批次数不符：" + batches);
        for (int index = 0; index < count; index++) {
            assertBatch(context, batches.get(index), index, input);
        }
    }

    private static void assertBatch(GameTestHelper context, Batch batch, int index, double input) {
        // 第 index 批的时刻容一个 tick 的观察差，投料精确。
        long expected = FIRST_BATCH_TICK + (long) index * BATCH_TICKS;
        context.assertTrue(Math.abs(batch.tick() - expected) <= BATCH_TOLERANCE_TICKS,
                "第 " + index + " 批时刻不符：" + batch);
        context.assertTrue(Math.abs(batch.input() - input) < TOLERANCE, "第 " + index + " 批投料不符：" + batch);
    }

    private static void assertLedger(GameTestHelper context, EvokerTestPlayer fixture, Set<String> before,
                                     double total) {
        // 施放以来的新回执：没有环境摄入；身体现存总量加释放总量等于施放前总量。
        List<CommandReceipt> receipts = fixture.receiptsSince(before);
        boolean captured = receipts.stream().anyMatch(receipt -> receipt.hasQiCapture() || receipt.hasOrganIntake());
        context.assertTrue(!captured, "炼化期间出现了环境摄入");
        double released = EvokerTestPlayer.released(receipts);
        double now = fixture.settled().totalQi();
        context.assertTrue(Math.abs(now + released - total) < LEDGER_TOLERANCE,
                "真元不守恒：施放前 " + total + " 现存 " + now + " 释放 " + released);
    }
}
