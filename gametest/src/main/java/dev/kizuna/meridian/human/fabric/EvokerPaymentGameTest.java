package dev.kizuna.meridian.human.fabric;

import dev.kizuna.meridian.core.Qi;
import dev.kizuna.meridian.protocol.pb.BodyMessages.Realm;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.CastOutcome;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueFailure;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.List;

/** 起手门槛：只有本行真元付费，本脏损毁或疲惫、境界不足、经脉未开都由生产施放入口拒绝，并说清缺哪一样。 */
public final class EvokerPaymentGameTest {
    private static final int TIMEOUT = 40;
    private static final double QI = 2;
    private static final double METAL_ARMS_COST = 0.3;
    private static final double TOLERANCE = 1e-9;
    private static final double TIRED_WATTS = 20;
    private static final double TIRED_SECONDS = 29.5;
    private static final String NO_METAL_QI = "左手阳明大肠经 等 4 条经脉的金行真元不够：要 0.3，可付 0";
    private static final List<String> METAL_ZANG = EvokerTestPlayer.meridians(List.of("lung"));
    private static final List<String> METAL_FU = EvokerTestPlayer.meridians(List.of("large_intestine"));
    private static final List<String> METAL = EvokerTestPlayer.meridians(List.of("lung", "large_intestine"));
    private static final List<String> EARTH = EvokerTestPlayer.meridians(List.of("spleen", "stomach"));
    private static final List<String> FIRE = EvokerTestPlayer.meridians(List.of("heart", "small_intestine"));
    private static final List<String> WATER = EvokerTestPlayer.meridians(List.of("kidney", "bladder"));
    private static final List<String> WOOD = EvokerTestPlayer.meridians(List.of("liver", "gallbladder"));

    @GameTest(maxTicks = TIMEOUT)
    public void attributeLessQiCannotPayElementCost(GameTestHelper context) {
        // 本行经脉里只有无属性真元：费用按属性匹配，拒绝原因是本行真元不足。
        EvokerTestPlayer.ready(context, fixture -> {
            fixture.setQi(METAL, Qi.QiAttribute.NONE, QI, EvokerTestPlayer.FREE_DENSITY);
            assertNoMetalQi(context, fixture.cast("metal_arms"));
            EvokerTestPlayer.finish(context, List.of(fixture));
        });
    }

    @GameTest(maxTicks = TIMEOUT)
    public void otherElementQiCannotPayElementCost(GameTestHelper context) {
        // 金臂的表里经里放的是火行真元，同样不能付金行费用。
        EvokerTestPlayer.ready(context, fixture -> {
            fixture.setQi(METAL, Qi.QiAttribute.FIRE, QI, EvokerTestPlayer.FREE_DENSITY);
            assertNoMetalQi(context, fixture.cast("metal_arms"));
            EvokerTestPlayer.finish(context, List.of(fixture));
        });
    }

    @GameTest(maxTicks = TIMEOUT)
    public void zangMeridianBalanceAlonePaysTheCost(GameTestHelper context) {
        // 金行真元只在肺经：按余额分摊时全部由肺经付，大肠经不被扣，全身金行恰好少一份费用。
        EvokerTestPlayer.ready(context, fixture -> {
            fixture.setQi(METAL_ZANG, Qi.QiAttribute.METAL, QI, EvokerTestPlayer.FREE_DENSITY);
            double before = EvokerTestPlayer.attributeQi(fixture.settled(), Qi.QiAttribute.METAL);
            CastOutcome outcome = fixture.cast("metal_arms");
            context.assertTrue(succeeded(outcome), "只有本脏经余额时起手失败：" + outcome);
            var body = fixture.settled();
            double paid = before - EvokerTestPlayer.attributeQi(body, Qi.QiAttribute.METAL);
            context.assertTrue(Math.abs(paid - METAL_ARMS_COST) < TOLERANCE, "金行扣费不符：" + paid);
            double fu = EvokerTestPlayer.meridianQi(body, METAL_FU, Qi.QiAttribute.METAL);
            context.assertTrue(fu == 0, "大肠经出现了金行真元：" + fu);
            EvokerTestPlayer.finish(context, List.of(fixture));
        });
    }

    @GameTest(maxTicks = TIMEOUT)
    public void destroyedSpleenCannotBind(GameTestHelper context) {
        // 脾 HP 归零：厚土的本脏槽绑不上，按模块不可用拒绝。
        EvokerTestPlayer.ready(context, fixture -> {
            fixture.setQi(EARTH, Qi.QiAttribute.EARTH, QI, EvokerTestPlayer.FREE_DENSITY);
            fixture.destroyOrgan("spleen");
            CastOutcome outcome = fixture.cast("earth_legs");
            context.assertTrue(outcome.getFailure() == TechniqueFailure.MODULE_UNAVAILABLE && !outcome.hasBinding()
                    && outcome.getReason().equals("脾脏伤了，这一式用不了"), "脾损毁后厚土没有按模块不可用拒绝：" + outcome);
            EvokerTestPlayer.finish(context, List.of(fixture));
        });
    }

    @GameTest(maxTicks = TIMEOUT)
    public void pairedKidneyCastsWithTheRemainingSide(GameTestHelper context) {
        // 左肾损毁：归水的本脏槽只绑右肾，照常起手。
        EvokerTestPlayer.ready(context, fixture -> {
            fixture.enterRealm(Realm.TRUE_QI);
            fixture.setQi(WATER, Qi.QiAttribute.WATER, QI, EvokerTestPlayer.FREE_DENSITY);
            fixture.destroyOrgan("left_kidney");
            CastOutcome outcome = fixture.cast("water_return");
            context.assertTrue(succeeded(outcome), "一侧肾损毁后归水起手失败：" + outcome);
            var zang = outcome.getBinding().getSlotsList().stream()
                    .filter(slot -> slot.getSlotId().equals("zang"))
                    .findFirst()
                    .orElseThrow();
            context.assertTrue(zang.getModuleIdsList().equals(List.of("right_kidney")),
                    "本脏槽绑定不符：" + zang.getModuleIdsList());
            EvokerTestPlayer.finish(context, List.of(fixture));
        });
    }

    @GameTest(maxTicks = TIMEOUT)
    public void tiredSpleenIsRejectedByEndurance(GameTestHelper context) {
        // 脾先做功到只剩 10 J：厚土要 12 W × 2 s，按器官耐力不足拒绝。
        EvokerTestPlayer.ready(context, fixture -> {
            fixture.setQi(EARTH, Qi.QiAttribute.EARTH, QI, EvokerTestPlayer.FREE_DENSITY);
            fixture.exertOrgan("spleen", TIRED_WATTS, TIRED_SECONDS);
            CastOutcome outcome = fixture.cast("earth_legs");
            context.assertTrue(outcome.getFailure() == TechniqueFailure.INSUFFICIENT_CAPABILITY
                    && outcome.getReason().equals("脾脏没有余力：要 12 W × 2 s"), "疲惫的脾没有按耐力拒绝：" + outcome);
            EvokerTestPlayer.finish(context, List.of(fixture));
        });
    }

    @GameTest(maxTicks = TIMEOUT)
    public void minimumRealmFollowsTheElementLayer(GameTestHelper context) {
        // 游炁不能起心火；真炁不能起舒筋与肝炼。本行真元备足，拒绝只来自境界。
        EvokerTestPlayer.ready(context, fixture -> {
            fixture.setQi(FIRE, Qi.QiAttribute.FIRE, QI, EvokerTestPlayer.FREE_DENSITY);
            fixture.setQi(WOOD, Qi.QiAttribute.WOOD, QI, EvokerTestPlayer.FREE_DENSITY);
            assertRealmTooLow(context, fixture.cast("fire_heart"), "境界不够：这一式要真炁，你现在是游炁");
            fixture.enterRealm(Realm.TRUE_QI);
            assertRealmTooLow(context, fixture.cast("wood_sinew"), "境界不够：这一式要化元，你现在是真炁");
            assertRealmTooLow(context, fixture.cast("refine_liver"), "境界不够：这一式要化元，你现在是真炁");
            EvokerTestPlayer.finish(context, List.of(fixture));
        });
    }

    @GameTest(maxTicks = TIMEOUT)
    public void unopenedChannelsAreNamedWithHowManyMoreToOpen(GameTestHelper context) {
        // 新角色一条经脉都没开：金臂的表里经槽列出四条候选经脉，至少还要开通一条。
        EvokerTestPlayer.unopened(context, fixture -> {
            CastOutcome outcome = fixture.cast("metal_arms");
            String reason = outcome.getReason();
            context.assertTrue(outcome.getFailure() == TechniqueFailure.MERIDIAN_NOT_OPENED && !outcome.hasBinding()
                    && reason.startsWith("经脉未通：") && reason.contains("左手太阴肺经")
                    && reason.contains("右手阳明大肠经") && reason.endsWith("，还要开通 1 条"),
                    "经脉未开的拒绝没有点名：" + outcome);
            EvokerTestPlayer.finish(context, List.of(fixture));
        });
    }

    private static void assertNoMetalQi(GameTestHelper context, CastOutcome outcome) {
        // 拒绝码按原稿 §4.1；说明写出缺哪行真元、要多少、能付多少。
        context.assertTrue(outcome.getFailure() == TechniqueFailure.INSUFFICIENT_ATTRIBUTE_QI
                && outcome.getReason().equals(NO_METAL_QI) && !outcome.hasBinding(),
                "本行真元不足的拒绝不符：" + outcome);
    }

    private static void assertRealmTooLow(GameTestHelper context, CastOutcome outcome, String reason) {
        // 境界门槛在绑定与付费之前判定，说明写出这一式要的境界和现在的境界。
        context.assertTrue(outcome.getFailure() == TechniqueFailure.REALM_TOO_LOW && !outcome.hasBinding()
                && outcome.getReason().equals(reason), "境界不足的拒绝不符：" + outcome);
    }

    static boolean succeeded(CastOutcome outcome) {
        // 成功的起手带真实绑定且没有失败码。
        return outcome.hasBinding() && outcome.getFailure() == TechniqueFailure.TECHNIQUE_FAILURE_UNSPECIFIED;
    }
}
