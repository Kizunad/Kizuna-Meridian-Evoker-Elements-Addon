package dev.kizuna.meridian.human.fabric;

import dev.kizuna.meridian.core.Qi;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.CastOutcome;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;

/**
 * 照 Wiki「五脏诀上手」走：命令都是聊天栏里的正式调试命令。炼化 32 s（起手 31 s、收势 1 s）用掉本脏的余力，
 * 收势后 1 s 放强身被拒「没有余力」，歇到第 8 s 能放。心、肾只有气态配方，真炁后要用 qi set 补气态真元才炼得出火、水行。
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
    private static final double LUNG_YIELD = 15 * 2 * 2 * 0.03;
    private static final double TOLERANCE = 1e-6;

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
    public void heartRefineOnFreeQiYieldsNoFire(GameTestHelper context) {
        // 真炁但只用 qi fill 补了游离态：心没有游离态配方，心炼站满 32 s 也炼不出火行，心火提示火行真元可付 0。
        EvokerTestPlayer.ready(context, fixture -> {
            run(context, fixture, TRUE_QI);
            run(context, fixture, FILL_FREE);
            var refine = fixture.cast("refine_heart");
            context.assertTrue(EvokerPaymentGameTest.succeeded(refine), "心炼起手失败：" + refine);
            EvokerTestPlayer.later(context, RESTED_TICKS, List.of(fixture), () -> {
                double fire = EvokerTestPlayer.attributeQi(fixture.settled(), Qi.QiAttribute.FIRE);
                context.assertTrue(fire == 0, "游离态真元也炼出了火行：" + fire);
                var heart = fixture.cast("fire_heart");
                context.assertTrue("火行真元不够：要 0.4，可付 0".equals(heart.getReason()), "心火拒绝说明不符：" + heart);
                EvokerTestPlayer.finish(context, List.of(fixture));
            });
        });
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
        MinecraftServer server = context.getLevel().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                "execute as " + fixture.player.getScoreboardName() + " run " + command);
    }
}
