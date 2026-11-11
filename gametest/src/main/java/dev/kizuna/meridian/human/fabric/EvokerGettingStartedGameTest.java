package dev.kizuna.meridian.human.fabric;

import dev.kizuna.meridian.core.Qi;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.CastOutcome;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;

/**
 * 照 Wiki「五脏诀上手」走：命令都是聊天栏里的正式调试命令。炼化 32 s（起手 31 s、收势 1 s）用掉本脏的余力，
 * 收势后 1 s 放强身被拒「没有余力」，歇到第 8 s 能放。心、肾只有气态配方：真炁以后吐纳直接出气态真元，
 * 只开自动吐纳三分钟就炼得出火、水行；用 qi set 补气态真元是不等吐纳的快路径。
 */
public final class EvokerGettingStartedGameTest {
    private static final int SECOND = 20;
    private static final int REFINE_TICKS = 32 * SECOND;
    private static final int TOO_SOON_TICKS = REFINE_TICKS + SECOND;
    private static final int RESTED_TICKS = REFINE_TICKS + 8 * SECOND;
    private static final int TIMEOUT = RESTED_TICKS + 2 * SECOND;
    private static final String FILL_FREE = "meridian qi fill";
    private static final String TRUE_QI = "meridian body realm true_qi";
    private static final String SET_GAS = "meridian qi set 3000 all lumen 0.1";
    private static final String NO_GAS_IN_HEART = "左手少阴心经、右手少阴心经里没有可炼的气态真元";
    private static final double LUNG_YIELD = 15 * 2 * 2 * 0.03;
    private static final double TOLERANCE = 1e-6;
    private static final int BREATHING_TICKS = 3 * 60 * SECOND;
    private static final int CAST_GAP_TICKS = 5 * SECOND;
    private static final int KIDNEY_START_TICKS = BREATHING_TICKS + RESTED_TICKS + CAST_GAP_TICKS;
    private static final int BREATHING_TIMEOUT = KIDNEY_START_TICKS + RESTED_TICKS + 2 * SECOND;
    private static final double FIRE_HEART_COST = 0.4;
    private static final double WATER_RETURN_COST = 0.3;
    private static final String SPRINT = "tick sprint " + BREATHING_TIMEOUT;
    private static final String STOP_SPRINT = "tick sprint stop";

    @GameTest(maxTicks = TIMEOUT)
    public void metalArmsWaitsForTheLungsAfterRefining(GameTestHelper context) {
        // 游炁 qi fill 后肺炼：两肺 15 批各 2 元按 3% 炼得 1.8 金行；收势后 1 s 两肺没有余力，第 8 s 放得出金臂。
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
        // 真炁、内腔清空，只开自动吐纳三分钟，再像关掉修炼窗口一样停下吐纳：心炼攒够心火的火行，放出心火；
        // 肾炼炼出够一次归水的水行。肾经炼出的水行会被自然扩散带进阴跷、冲脉、心包经，这里只核对炼出的量。
        // 这几分钟按 tick 加速跑完，用例结束停止加速。
        EvokerTestPlayer.ready(context, fixture -> {
            run(context, fixture, TRUE_QI);
            HumanGameTestAccess.breathe(fixture.player);
            console(context, SPRINT);
            EvokerTestPlayer.later(context, BREATHING_TICKS, List.of(fixture), () -> {
                HumanGameTestAccess.endBreathing(fixture.player);
                startRefine(context, fixture, "refine_heart");
            });
            EvokerTestPlayer.later(context, BREATHING_TICKS + RESTED_TICKS, List.of(fixture), () -> {
                assertRefined(context, fixture, Qi.QiAttribute.FIRE, FIRE_HEART_COST);
                var fireHeart = fixture.cast("fire_heart");
                context.assertTrue(EvokerPaymentGameTest.succeeded(fireHeart), "只靠吐纳放心火失败：" + fireHeart);
            });
            EvokerTestPlayer.later(context, KIDNEY_START_TICKS, List.of(fixture),
                    () -> startRefine(context, fixture, "refine_kidney"));
            EvokerTestPlayer.later(context, KIDNEY_START_TICKS + RESTED_TICKS, List.of(fixture), () -> {
                assertRefined(context, fixture, Qi.QiAttribute.WATER, WATER_RETURN_COST);
                console(context, STOP_SPRINT);
                EvokerTestPlayer.finish(context, List.of(fixture));
            });
        });
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
