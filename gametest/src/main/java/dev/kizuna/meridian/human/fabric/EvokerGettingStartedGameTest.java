package dev.kizuna.meridian.human.fabric;

import com.mojang.logging.LogUtils;
import dev.kizuna.meridian.core.BodySnapshot;
import dev.kizuna.meridian.core.Qi;
import dev.kizuna.meridian.core.Realm;
import dev.kizuna.meridian.human.pb.CultivationMessages.CultivationInput;
import dev.kizuna.meridian.human.server.HumanCultivation;
import dev.kizuna.meridian.protocol.pb.MeridianMessages.FailureCode;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.CastOutcome;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

/**
 * 照 Wiki「五脏诀上手」走：命令都是聊天栏里的正式调试命令。炼化 32 s（起手 31 s、收势 1 s）用掉本脏的余力，
 * 收势后 1 s 放强身被拒「没有余力」，歇到第 8 s 能放。心、肾只有气态配方：真炁以后吐纳直接出气态真元，
 * 只开自动吐纳四分钟就炼得出火、水行，三分钟时火行只比心火所需多一成上下；用 qi set 补气态真元是不等吐纳的快路径。生存路径不清空经脉：
 * 游炁吐纳攒下的游离态留在各脉，中焦突破后接着开自动周天把它炼成气态，同时真炁吐纳；心经几分钟就够，
 * 肾经离肺经最远，吐纳二十二分钟时六次实测肾炼出水行 0.28–0.47，有一次不够归水的 0.3，这里吐纳二十六分钟。
 */
public final class EvokerGettingStartedGameTest {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SECOND = 20;
    private static final int REFINE_TICKS = 32 * SECOND;
    private static final int TOO_SOON_TICKS = REFINE_TICKS + SECOND;
    private static final int RESTED_TICKS = REFINE_TICKS + 8 * SECOND;
    private static final int TIMEOUT = RESTED_TICKS + 2 * SECOND;
    private static final String FILL_FREE = "meridian qi fill";
    private static final String TRUE_QI = "meridian body realm true_qi";
    private static final String SET_GAS = "meridian qi set 3000 all lumen 0.1";
    private static final String NO_GAS_IN_HEART = "左手少阴心经、右手少阴心经里没有可炼的气态真元";
    private static final double LUNG_YIELD = 15 * 2 * 2 * 0.08;
    private static final double TOLERANCE = 1e-6;
    private static final int BREATHING_TICKS = 4 * 60 * SECOND;
    private static final int CAST_GAP_TICKS = 5 * SECOND;
    private static final int KIDNEY_START_TICKS = BREATHING_TICKS + RESTED_TICKS + CAST_GAP_TICKS;
    private static final int BREATHING_TIMEOUT = KIDNEY_START_TICKS + RESTED_TICKS + 2 * SECOND;
    private static final double FIRE_HEART_COST = 0.4;
    private static final double WATER_RETURN_COST = 0.3;
    private static final String SPRINT = "tick sprint " + BREATHING_TIMEOUT;
    private static final int HISTORY_CLEAR_TICKS = RESTED_TICKS;
    private static final int HISTORY_LANDED_TICKS = HISTORY_CLEAR_TICKS + 10 * SECOND;
    private static final int HISTORY_MINUTE_TICKS = HISTORY_CLEAR_TICKS + 60 * SECOND;
    private static final int HISTORY_TIMEOUT = HISTORY_MINUTE_TICKS + BREATHING_TIMEOUT;
    private static final String HISTORY_SPRINT = "tick sprint " + HISTORY_TIMEOUT;
    private static final String CLEAR_LUMENS = "meridian qi set 0";
    private static final String STOP_SPRINT = "tick sprint stop";
    private static final int MINUTE = 60 * SECOND;
    private static final int WANDERING_TICKS = 12 * MINUTE;
    private static final int CIRCUIT_TICKS = WANDERING_TICKS + 3 * MINUTE;
    private static final int LAUNCH_DEADLINE_TICKS = CIRCUIT_TICKS + 20 * SECOND;
    private static final int TRUE_QI_TICKS = CIRCUIT_TICKS + 90 * SECOND;
    private static final int TRUE_BREATHING_TICKS = 26 * MINUTE;
    private static final int LOG_INTERVAL_TICKS = MINUTE;
    private static final int HEART_TICKS = TRUE_QI_TICKS + TRUE_BREATHING_TICKS;
    private static final int FIRE_TICKS = HEART_TICKS + RESTED_TICKS;
    private static final int KIDNEY_TICKS = FIRE_TICKS + CAST_GAP_TICKS;
    private static final int WATER_TICKS = KIDNEY_TICKS + RESTED_TICKS;
    private static final int SURVIVAL_TIMEOUT = WATER_TICKS + 2 * SECOND;
    private static final String SURVIVAL_SPRINT = "tick sprint " + SURVIVAL_TIMEOUT;
    private static final String OPEN_ALL = "meridian opening complete all";
    private static final String CANDIDATE_MERIDIAN = "left_stomach_meridian";
    private static final String SET_CANDIDATES = "meridian qi set 55 " + CANDIDATE_MERIDIAN + " lumen 0.099";
    private static final String SET_WALL = "meridian qi set 60 all wall 0.099";
    private static final long START_AUTOMATIC_SEQUENCE = 1;
    private static final long BREAKTHROUGH_SEQUENCE = 2;
    private static final long STOP_SEQUENCE = 3;
    private static final double MINIMUM_FREE_RESIDUE = 10;
    private static final List<String> HEART = EvokerTestPlayer.meridians(List.of("heart"));
    private static final List<String> KIDNEY = EvokerTestPlayer.meridians(List.of("kidney"));

    @GameTest(maxTicks = TIMEOUT)
    public void metalArmsWaitsForTheLungsAfterRefining(GameTestHelper context) {
        // 游炁 qi fill 后肺炼：两肺 15 批各 2 元按 8% 炼得 4.8 金行；收势后 1 s 两肺没有余力，第 8 s 放得出金臂。
        EvokerTestPlayer.ready(context, fixture -> {
            run(context, fixture, FILL_FREE);
            var refine = fixture.cast("refine_lung");
            context.assertTrue(EvokerPaymentGameTest.succeeded(refine), "肺炼起手失败：" + refine);
            EvokerTestPlayer.later(context, TOO_SOON_TICKS, List.of(fixture), () -> {
                double metal = EvokerTestPlayer.attributeQi(fixture.settled(), Qi.QiAttribute.METAL);
                context.assertTrue(Math.abs(metal - LUNG_YIELD) < TOLERANCE, "一遍肺炼的金行产量不符：" + metal);
                assertTired(context, fixture.cast("metal_arms"), "左肺没有余力：要 10 W × 2 s");
            });
            EvokerTestPlayer.later(context, RESTED_TICKS, List.of(fixture), () -> {
                var arms = fixture.cast("metal_arms");
                context.assertTrue(EvokerPaymentGameTest.succeeded(arms), "两肺歇过后金臂仍起手失败：" + arms);
                EvokerTestPlayer.finish(context, List.of(fixture));
            });
        });
    }

    @GameTest(maxTicks = TIMEOUT)
    public void fireHeartAfterHeartRefineOnGasQi(GameTestHelper context) {
        // 改境界到真炁、补气态真元后心炼：收势后 1 s 心脏没有余力，第 8 s 放得出心火。
        strengthenAfterGasRefine(context, "refine_heart", "fire_heart", "心脏没有余力：要 20 W × 1.5 s");
    }

    @GameTest(maxTicks = TIMEOUT)
    public void waterReturnAfterKidneyRefineOnGasQi(GameTestHelper context) {
        // 同上，肾炼后歇过放归水。
        strengthenAfterGasRefine(context, "refine_kidney", "water_return", "左肾没有余力：要 10 W × 2 s");
    }

    @GameTest(maxTicks = TIMEOUT)
    public void heartRefineOnFreeQiIsRejectedAtStart(GameTestHelper context) {
        // 真炁但只用 qi fill 补了游离态：心没有游离态配方，心炼起手就拒并写出缺哪种真元，不再空站 32 s。
        EvokerTestPlayer.ready(context, fixture -> {
            run(context, fixture, TRUE_QI);
            run(context, fixture, FILL_FREE);
            var refine = fixture.cast("refine_heart");
            context.assertTrue(NO_GAS_IN_HEART.equals(refine.getReason()), "心炼起手的拒绝说明不符：" + refine);
            EvokerTestPlayer.finish(context, List.of(fixture));
        });
    }

    @GameTest(maxTicks = BREATHING_TIMEOUT)
    public void trueQiBreathingAloneRefinesFireAndWater(GameTestHelper context) {
        // 真炁、内腔清空，只开自动吐纳四分钟，再像关掉修炼窗口一样停下吐纳：心炼攒够心火的火行，放出心火；
        // 肾炼炼出够一次归水的水行，水行留在肾经，放得出归水。这几分钟按 tick 加速跑完，用例结束停止加速。
        EvokerTestPlayer.ready(context, fixture -> {
            run(context, fixture, TRUE_QI);
            HumanGameTestAccess.breathe(fixture.player);
            console(context, SPRINT);
            refineFireAndWaterAfterBreathing(context, fixture, "清空内腔");
        });
    }

    @GameTest(maxTicks = HISTORY_TIMEOUT)
    public void wikiPathRefinesFireAndWaterAfterClearingAFilledBody(GameTestHelper context) {
        // 照上手 Wiki 和实机走查的前史：qi fill 补满、肺炼、歇过放金臂，改真炁后 qi set 0 清空内腔。清空那一刻还在路上的
        // 扩散批次和脾外壁放回的游离态之后才到，等一分钟再清一次，然后只开自动吐纳四分钟，心炼放心火、肾炼放归水。
        // 第一次清空当刻、10 s 后、60 s 后（第二次清空之前）各记一次。
        EvokerTestPlayer.ready(context, fixture -> {
            console(context, HISTORY_SPRINT);
            run(context, fixture, FILL_FREE);
            var lung = fixture.cast("refine_lung");
            context.assertTrue(EvokerPaymentGameTest.succeeded(lung), "肺炼起手失败：" + lung);
            EvokerTestPlayer.later(context, HISTORY_CLEAR_TICKS, List.of(fixture), () -> {
                assertCast(context, fixture, "metal_arms");
                run(context, fixture, TRUE_QI);
                run(context, fixture, CLEAR_LUMENS);
                logTransits("走查前史清空当刻", fixture.settled());
            });
            EvokerTestPlayer.later(context, HISTORY_LANDED_TICKS, List.of(fixture),
                    () -> logTransits("走查前史清空 10 s 后", fixture.body().snapshot()));
            EvokerTestPlayer.later(context, HISTORY_MINUTE_TICKS, List.of(fixture), () -> {
                logTransits("走查前史清空 60 s 后", fixture.body().snapshot());
                run(context, fixture, CLEAR_LUMENS);
                logTransits("走查前史第二次清空当刻", fixture.settled());
                HumanGameTestAccess.breathe(fixture.player);
                refineFireAndWaterAfterBreathing(context, fixture, "走查前史");
            });
        });
    }

    private static void refineFireAndWaterAfterBreathing(GameTestHelper context, EvokerTestPlayer fixture,
                                                         String body) {
        // 吐纳刚开始：四分钟后像关掉修炼窗口一样停下，心炼、歇过放心火；再肾炼，水行留在肾经，歇过放归水。
        // 炼完先记一笔再核对，不够时日志里也看得到炼出多少。
        EvokerTestPlayer.later(context, BREATHING_TICKS, List.of(fixture), () -> {
            HumanGameTestAccess.endBreathing(fixture.player);
            logQi(body + "真炁吐纳之后", fixture.settled());
            startRefine(context, fixture, "refine_heart");
        });
        EvokerTestPlayer.later(context, BREATHING_TICKS + RESTED_TICKS, List.of(fixture), () -> {
            logQi(body + "心炼之后", fixture.settled());
            assertRefined(context, fixture, Qi.QiAttribute.FIRE, FIRE_HEART_COST);
            assertCast(context, fixture, "fire_heart");
        });
        EvokerTestPlayer.later(context, KIDNEY_START_TICKS, List.of(fixture),
                () -> startRefine(context, fixture, "refine_kidney"));
        EvokerTestPlayer.later(context, KIDNEY_START_TICKS + RESTED_TICKS, List.of(fixture), () -> {
            logQi(body + "肾炼之后", fixture.settled());
            assertRefined(context, fixture, Qi.QiAttribute.WATER, WATER_RETURN_COST);
            assertCast(context, fixture, "water_return");
            console(context, STOP_SPRINT);
            EvokerTestPlayer.finish(context, List.of(fixture));
        });
    }

    private static void logTransits(String moment, BodySnapshot body) {
        // 报告用：此刻在路上的批次数与其中的游离态元数；清空经脉不动在途批次，它们之后照常到达。
        double freeInTransit = body.transits().stream()
                .flatMap(transit -> transit.cargo().entries().stream())
                .filter(qi -> qi.phase() == Qi.QiPhase.FREE)
                .mapToDouble(Qi::amount)
                .sum();
        LOGGER.info("{}：在途 {} 批，其中游离态 {} 元", moment, body.transits().size(), freeInTransit);
        logQi(moment, body);
    }

    @GameTest(maxTicks = SURVIVAL_TIMEOUT)
    public void survivalPathRefinesFireAndWaterWithoutClearingMeridians(GameTestHelper context) {
        // 新角色开脉后游炁自动吐纳十二分钟，游离态攒满各脉；关窗歇三分钟等脑耐力回满，开中焦自动周天、突破真炁。
        // 突破后周天不停，把八脉里的游离态炼成气态，同时真炁吐纳二十六分钟；然后停周天，心炼放心火、肾炼放归水都要成功
        // （心经要是已经被吐纳留下的火行占满，就不用炼，直接放心火）。
        // 只用调试命令开脉、放候选与外壁燃料，不清空经脉。
        EvokerTestPlayer.unopened(context, fixture -> {
            run(context, fixture, OPEN_ALL);
            HumanGameTestAccess.breathe(fixture.player);
            console(context, SURVIVAL_SPRINT);
            EvokerTestPlayer.later(context, WANDERING_TICKS, List.of(fixture), () -> {
                HumanGameTestAccess.endBreathing(fixture.player);
                logQi("生存路径游炁吐纳之后", fixture.settled());
            });
            scheduleBreakthrough(context, fixture);
            EvokerTestPlayer.later(context, TRUE_QI_TICKS, List.of(fixture), () -> {
                enteredTrueQi(context, fixture);
                HumanGameTestAccess.breathe(fixture.player);
                logWhileBreathing(fixture);
            });
            EvokerTestPlayer.later(context, HEART_TICKS, List.of(fixture), () -> {
                HumanGameTestAccess.endBreathing(fixture.player);
                cultivate(context, fixture, CultivationInput.Operation.STOP, STOP_SEQUENCE);
                logQi("生存路径真炁吐纳之后", fixture.settled());
                refineHeartIfThereIsGas(context, fixture);
            });
            EvokerTestPlayer.later(context, FIRE_TICKS, List.of(fixture), () -> {
                assertRefined(context, fixture, Qi.QiAttribute.FIRE, FIRE_HEART_COST);
                assertCast(context, fixture, "fire_heart");
            });
            EvokerTestPlayer.later(context, KIDNEY_TICKS, List.of(fixture),
                    () -> startRefine(context, fixture, "refine_kidney"));
            EvokerTestPlayer.later(context, WATER_TICKS, List.of(fixture), () -> {
                assertRefined(context, fixture, Qi.QiAttribute.WATER, WATER_RETURN_COST);
                logQi("生存路径肾炼之后", fixture.settled());
                assertCast(context, fixture, "water_return");
                console(context, STOP_SPRINT);
                EvokerTestPlayer.finish(context, List.of(fixture));
            });
        });
    }

    private static void scheduleBreakthrough(GameTestHelper context, EvokerTestPlayer fixture) {
        // 歇够后开自动周天，第一圈发出当刻按突破；开周天 20 s 后突破要正在进行。
        var pressed = new CompletableFuture<FailureCode>();
        EvokerTestPlayer.later(context, CIRCUIT_TICKS, List.of(fixture), () -> {
            startCircuit(context, fixture);
            pressBreakthroughOnLaunch(context, fixture, pressed);
        });
        EvokerTestPlayer.later(context, LAUNCH_DEADLINE_TICKS, List.of(fixture),
                () -> assertBreakthroughRunning(context, fixture, pressed));
    }

    private static void startCircuit(GameTestHelper context, EvokerTestPlayer fixture) {
        // 读残卷后开自动周天，周天接管八脉，八脉的自然扩散停下。候选是周天提纯的产物：调试命令只覆盖左胃经一脉，
        // 其余经脉的游离态原样保留。
        HumanGameTestAccess.readMiddleJiaoScroll(fixture.player);
        cultivate(context, fixture, CultivationInput.Operation.START_AUTOMATIC, START_AUTOMATIC_SEQUENCE);
        BodySnapshot body = fixture.settled();
        var state = HumanCultivation.state(body);
        context.assertTrue(state.getRunning(), "自动周天没有开起来：" + state.getReason());
        double overwritten = phaseQi(body, List.of(CANDIDATE_MERIDIAN), Qi.QiPhase.FREE);
        LOGGER.info("生存路径：左胃经被候选覆盖掉的游离态 {} 元", overwritten);
        run(context, fixture, SET_CANDIDATES);
    }

    private static void pressBreakthroughOnLaunch(GameTestHelper context, EvokerTestPlayer fixture,
                                                  CompletableFuture<FailureCode> pressed) {
        // 周天要等身上已有的普通运输都到了才发出第一圈。第一圈一发出就用调试命令放够 60 元外壁燃料、按突破，
        // 和玩家看到按钮就点一样：自动周天运转时每秒耗脑 1 W 且不回复，脑耐力掉到 80% 就停。
        // 之后 60 秒的燃料、动员与候选检查都走正式周天。逐 tick 观察里不抛断言，按键结果留给后面核对。
        fixture.everyTick(() -> {
            boolean launched = fixture.body().snapshot().transits().stream()
                    .anyMatch(transit -> transit.id().equals(HumanCultivation.CIRCUIT));
            if (launched && !pressed.isDone()) {
                run(context, fixture, SET_WALL);
                pressed.complete(press(fixture, CultivationInput.Operation.START_BREAKTHROUGH, BREAKTHROUGH_SEQUENCE));
            }
        });
    }

    private static void assertBreakthroughRunning(GameTestHelper context, EvokerTestPlayer fixture,
                                                  CompletableFuture<FailureCode> pressed) {
        // 开周天后几秒内第一圈就该发出；按下后突破正在进行。
        BodySnapshot body = fixture.settled();
        var state = HumanCultivation.state(body);
        String waiting = state.getReason() + "，在途 " + body.transits().size() + " 批";
        context.assertTrue(pressed.isDone(), "开周天 20 s 后第一圈还没发出：" + waiting);
        context.assertTrue(pressed.join() == FailureCode.FAILURE_CODE_UNSPECIFIED, "突破按键被拒：" + pressed.join());
        context.assertTrue(state.getBreakthrough(), "突破没有开始：" + state.getReason());
    }

    private static void enteredTrueQi(GameTestHelper context, EvokerTestPlayer fixture) {
        // 连续 60 秒后已是真炁；周天接着转，经脉里仍留着游炁期攒下的游离态。
        BodySnapshot body = fixture.settled();
        var state = HumanCultivation.state(body);
        context.assertTrue(body.realm() == Realm.TRUE_QI, "突破没有成功：" + state.getReason());
        context.assertTrue(state.getAutomatic(), "突破后自动周天停了：" + state.getReason());
        logQi("生存路径突破之后", body);
        double residue = phaseQi(body, allMeridians(body), Qi.QiPhase.FREE);
        context.assertTrue(residue >= MINIMUM_FREE_RESIDUE, "突破后经脉里的游离态太少，算不上留有余量：" + residue);
    }

    private static void logWhileBreathing(EvokerTestPlayer fixture) {
        // 报告用：真炁吐纳期间每分钟记一次心、肾经的相态和周天状态，脑耐力停机、续上当刻各记一次。
        long start = fixture.tick();
        var waiting = new AtomicBoolean();
        fixture.everyTick(() -> {
            long elapsed = fixture.tick() - start;
            if (elapsed <= 0 || elapsed > TRUE_BREATHING_TICKS) {
                return;
            }
            BodySnapshot body = fixture.body().snapshot();
            var state = HumanCultivation.state(body);
            String moment = "生存路径真炁吐纳 " + elapsed / (double) MINUTE + " min";
            if (state.getAutomaticWaiting() != waiting.get()) {
                waiting.set(state.getAutomaticWaiting());
                logCircuit(moment, body);
            }
            if (elapsed % LOG_INTERVAL_TICKS == 0) {
                logQi(moment, body);
                logCircuit(moment, body);
            }
        });
    }

    private static void logCircuit(String moment, BodySnapshot body) {
        // 报告用：周天在转还是在等脑耐力、转了几圈、脑耐力占比和停下的原因。
        var state = HumanCultivation.state(body);
        LOGGER.info("{}：周天运转 {} 等耐力 {} 自动 {} 共 {} 圈，脑耐力 {}，原因「{}」", moment, state.getRunning(),
                state.getAutomaticWaiting(), state.getAutomatic(), state.getLaps(), state.getBrainStaminaRatio(),
                state.getReason());
    }

    private static void cultivate(GameTestHelper context, EvokerTestPlayer fixture,
                                  CultivationInput.Operation operation, long sequence) {
        // 中焦窗口按一次键，要被接受。
        FailureCode result = press(fixture, operation, sequence);
        context.assertTrue(result == FailureCode.FAILURE_CODE_UNSPECIFIED, operation + " 被拒：" + result);
    }

    private static FailureCode press(EvokerTestPlayer fixture, CultivationInput.Operation operation, long sequence) {
        // 与中焦窗口的按键同一入口：只带操作和序号，时刻取服务端。
        var input = CultivationInput.newBuilder()
                .setSequence(sequence)
                .setOperation(operation)
                .build();
        return fixture.body().cultivate(HumanTechniqueHost.nowMillis(), input);
    }

    private static void logQi(String moment, BodySnapshot body) {
        // 报告用：全身与心、肾经内腔各相态的元数。
        List<String> all = allMeridians(body);
        LOGGER.info("{}：全身游离 {} 气态 {}；心经游离 {} 气态 {} 火行 {}；肾经游离 {} 气态 {} 水行 {}", moment,
                phaseQi(body, all, Qi.QiPhase.FREE), phaseQi(body, all, Qi.QiPhase.GAS),
                phaseQi(body, HEART, Qi.QiPhase.FREE), phaseQi(body, HEART, Qi.QiPhase.GAS),
                EvokerTestPlayer.meridianQi(body, HEART, Qi.QiAttribute.FIRE),
                phaseQi(body, KIDNEY, Qi.QiPhase.FREE), phaseQi(body, KIDNEY, Qi.QiPhase.GAS),
                EvokerTestPlayer.meridianQi(body, KIDNEY, Qi.QiAttribute.WATER));
    }

    private static double phaseQi(BodySnapshot body, List<String> meridianIds, Qi.QiPhase phase) {
        // 指定经脉内腔里某一相态的无属性真元。
        return body.meridianStates().stream()
                .filter(meridian -> meridianIds.contains(meridian.id().value()))
                .flatMap(meridian -> meridian.lumenQi().entries().stream())
                .filter(qi -> qi.attribute().equals(Qi.QiAttribute.NONE) && qi.phase() == phase)
                .mapToDouble(Qi::amount)
                .sum();
    }

    private static List<String> allMeridians(BodySnapshot body) {
        // 全身经脉 ID。
        return body.meridianStates().stream()
                .map(meridian -> meridian.id().value())
                .toList();
    }

    private static void assertCast(GameTestHelper context, EvokerTestPlayer fixture, String techniqueId) {
        // 炼完歇过放强身，要起得了手。
        var outcome = fixture.cast(techniqueId);
        context.assertTrue(EvokerPaymentGameTest.succeeded(outcome), techniqueId + " 起手失败：" + outcome);
    }

    private static void refineHeartIfThereIsGas(GameTestHelper context, EvokerTestPlayer fixture) {
        // 吐纳把本属性真元直接留在本行的经里。当地火行多时，火行会把心经占满，周天炼出的气态真元进不来，
        // 心炼没有料可炼：这时不炼，只核对吐纳留下的火行已经够放一次心火。心经里有气态真元就照旧起手炼。
        BodySnapshot body = fixture.settled();
        if (phaseQi(body, HEART, Qi.QiPhase.GAS) > 0) {
            startRefine(context, fixture, "refine_heart");
            return;
        }
        double fire = EvokerTestPlayer.meridianQi(body, HEART, Qi.QiAttribute.FIRE);
        context.assertTrue(fire >= FIRE_HEART_COST,
                "心经里没有气态真元可炼，吐纳留下的火行也不够一次心火：" + fire);
        LOGGER.info("生存路径：心经被吐纳留下的火行 {} 元占着，没有气态真元，心炼这一步跳过", fire);
    }

    private static void startRefine(GameTestHelper context, EvokerTestPlayer fixture, String refineId) {
        // 炼化起手要过预检，本脏经里有气态真元才起得了手。
        var refine = fixture.cast(refineId);
        context.assertTrue(EvokerPaymentGameTest.succeeded(refine), refineId + " 只靠吐纳起手失败：" + refine);
    }

    private static void assertRefined(GameTestHelper context, EvokerTestPlayer fixture, Qi.QiAttribute attribute,
                                      double cost) {
        // 炼完歇过：全身这一行真元够一次强身。
        double refined = EvokerTestPlayer.attributeQi(fixture.settled(), attribute);
        context.assertTrue(refined >= cost, attribute.id().value() + " 要 " + cost + "，只靠吐纳炼出 " + refined);
    }

    private static void strengthenAfterGasRefine(GameTestHelper context, String refineId, String strengthenId,
                                                 String tired) {
        // 真炁的炼化走气态配方：先改境界、把全身内腔覆盖成气态真元，再炼；收势后先被拒，歇过再放。
        EvokerTestPlayer.ready(context, fixture -> {
            run(context, fixture, TRUE_QI);
            run(context, fixture, SET_GAS);
            var refine = fixture.cast(refineId);
            context.assertTrue(EvokerPaymentGameTest.succeeded(refine), refineId + " 起手失败：" + refine);
            EvokerTestPlayer.later(context, TOO_SOON_TICKS, List.of(fixture),
                    () -> assertTired(context, fixture.cast(strengthenId), tired));
            EvokerTestPlayer.later(context, RESTED_TICKS, List.of(fixture), () -> {
                var outcome = fixture.cast(strengthenId);
                context.assertTrue(EvokerPaymentGameTest.succeeded(outcome), strengthenId + " 歇过后仍起手失败：" + outcome);
                EvokerTestPlayer.finish(context, List.of(fixture));
            });
        });
    }

    private static void assertTired(GameTestHelper context, CastOutcome outcome, String reason) {
        // 收势刚过：本脏余力还没回来，拒绝说明写出缺的功率和时长。
        context.assertTrue(reason.equals(outcome.getReason()), "收势后 1 s 的拒绝说明不符：" + outcome);
    }

    private static void run(GameTestHelper context, EvokerTestPlayer fixture, String command) {
        // 以这名玩家的身份执行，和玩家自己在聊天栏输入的相同；控制台源带 OP 权限。
        console(context, "execute as " + fixture.player.getScoreboardName() + " run " + command);
    }

    private static void console(GameTestHelper context, String command) {
        // 控制台源执行，输出不进日志。
        MinecraftServer server = context.getLevel().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }
}
