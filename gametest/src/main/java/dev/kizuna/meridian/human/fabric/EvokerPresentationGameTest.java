package dev.kizuna.meridian.human.fabric;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kizuna.meridian.core.Qi;
import dev.kizuna.meridian.human.server.HumanTechniqueAnimations;
import dev.kizuna.meridian.human.server.HumanTechniqueAnimations.CastHand;
import dev.kizuna.meridian.protocol.pb.BodyMessages.Realm;
import dev.kizuna.meridian.technique.fabric.TechniqueFabric;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.CastOutcome;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.CastPhases;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.Category;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.EffectIntent;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.PresentationAction;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueDefinition;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.gametest.framework.GameTestHelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

/** 五脏诀抬手动作：目录的表现对上 PA 剪辑的时长与效果帧，玩家起手即播、收势结束才停（C6c SPEC §6.1）。 */
public final class EvokerPresentationGameTest {
    private static final int TIMEOUT = 200;
    private static final int TECHNIQUES = 15;
    private static final String PA_MOD_ID = "kizuna_players_animation";
    private static final String CLIPS = "assets/" + PA_MOD_ID + "/animations/";
    private static final String CAST_EVENT = "cast";
    private static final long MILLIS_PER_SECOND = 1_000;
    private static final long MILLIS_PER_TICK = 50;
    private static final HumanTechniqueAnimations.Direction FORWARD = new HumanTechniqueAnimations.Direction(0, 0, 1);
    private static final String METAL_ARMS = "metal_arms";
    private static final List<String> METAL_KINDS = List.of("lung", "large_intestine");
    private static final double QI = 2;
    private static final int START_TICKS = 2;
    private static final int STOP_MARGIN_TICKS = 5;
    private static final Map<UUID, List<EffectIntent>> OBSERVED = new HashMap<>();

    static {
        HumanTechniquePresentation.EVENT.register((player, requestId, effect) -> {
            // 只记录本类登记过的玩家，别的用例的施放不进来。
            List<EffectIntent> events = OBSERVED.get(player.getUUID());
            if (events != null) {
                events.add(effect);
            }
        });
    }

    @GameTest
    public void everyClipMatchesItsCastTimings(GameTestHelper context) throws IOException {
        // 剪辑一次性、不比起手加收势短（收势收在身体停下来的时刻，剪辑尾上不动的那段不占施放）；
        // 强身、附伤的效果帧落在起手结束，炼元没有效果帧。
        ModContainer animations = FabricLoader.getInstance().getModContainer(PA_MOD_ID).orElseThrow();
        List<TechniqueDefinition> definitions = definitions();
        context.assertTrue(definitions.size() == TECHNIQUES, "五脏诀式数不符：" + definitions.size());
        for (TechniqueDefinition definition : definitions) {
            String presentation = definition.getEffects(0).getPresentation().getId();
            String clip = HumanTechniqueAnimations.resolve(presentation, FORWARD, CastHand.MAIN);
            JsonObject animation = clip(animations, clip);
            CastPhases phases = definition.getPhases();
            long castMillis = phases.getWarmupMillis() + phases.getActiveMillis() + phases.getRecoveryMillis();
            long clipMillis = Math.round(animation.get("animation_length").getAsDouble() * MILLIS_PER_SECOND);
            context.assertTrue("false".equals(animation.get("loop").getAsString()), "剪辑不该循环：" + clip);
            context.assertTrue(clipMillis >= castMillis, "剪辑比施放短：" + clip + " " + clipMillis
                    + " < " + castMillis);
            OptionalLong castFrame = castFrameMillis(animation);
            OptionalLong expected = definition.getCategory() == Category.BUFF
                    ? OptionalLong.of(phases.getWarmupMillis())
                    : OptionalLong.empty();
            context.assertTrue(castFrame.equals(expected), "效果帧对不上起手：" + clip + " " + castFrame
                    + " ≠ " + expected);
        }
        context.succeed();
    }

    @GameTest(maxTicks = TIMEOUT)
    public void castStartsTheClipAndRecoveryEndStopsIt(GameTestHelper context) {
        // 玩家放金臂：起手当即收到从头播的 START；收势结束前没有 STOP，结束后收到同一表现的 STOP。
        EvokerTestPlayer.ready(context, fixture -> {
            fixture.enterRealm(Realm.WANDERING_QI);
            List<String> meridians = EvokerTestPlayer.meridians(METAL_KINDS);
            fixture.setQi(meridians, Qi.QiAttribute.METAL, QI, EvokerTestPlayer.FREE_DENSITY);
            List<EffectIntent> events = new ArrayList<>();
            OBSERVED.put(fixture.player.getUUID(), events);
            CastOutcome outcome = fixture.cast(METAL_ARMS);
            context.assertTrue(EvokerPaymentGameTest.succeeded(outcome), "金臂起手失败：" + outcome);
            String id = EvokerTestPlayer.PREFIX + "evoker_" + METAL_ARMS;
            int castTicks = Math.toIntExact(castMillis(METAL_ARMS) / MILLIS_PER_TICK);
            List<EvokerTestPlayer> players = List.of(fixture);
            EvokerTestPlayer.later(context, START_TICKS, players, () -> {
                assertActions(context, events, id, List.of(PresentationAction.START));
                context.assertTrue(events.getFirst().getPresentation().getStartMillis() == 0, "剪辑没有从头播");
                int beforeEnd = castTicks - STOP_MARGIN_TICKS - START_TICKS;
                EvokerTestPlayer.later(context, beforeEnd, players, () -> {
                    assertActions(context, events, id, List.of(PresentationAction.START));
                    EvokerTestPlayer.later(context, 2 * STOP_MARGIN_TICKS, players, () -> {
                        assertActions(context, events, id, List.of(PresentationAction.START, PresentationAction.STOP));
                        OBSERVED.remove(fixture.player.getUUID());
                        EvokerTestPlayer.finish(context, players);
                    });
                });
            });
        });
    }

    private static void assertActions(GameTestHelper context, List<EffectIntent> events, String id,
                                      List<PresentationAction> expected) {
        // 记录到的表现按顺序只能是这一式的剪辑，动作序列与期望一致。
        List<PresentationAction> actions = events.stream().map(event -> event.getPresentation().getAction()).toList();
        boolean sameClip = events.stream().allMatch(event -> event.getPresentation().getId().equals(id));
        context.assertTrue(sameClip && actions.equals(expected), "表现序列不符：期望 " + expected + "，收到 " + events);
    }

    private static long castMillis(String technique) {
        // 施放全长取生产注册表里的起手、持续与收势。
        CastPhases phases = TechniqueFabric.service().registry().findLatest(EvokerTestPlayer.PREFIX + technique)
                .orElseThrow()
                .getPhases();
        return phases.getWarmupMillis() + phases.getActiveMillis() + phases.getRecoveryMillis();
    }

    private static OptionalLong castFrameMillis(JsonObject animation) {
        // 时间线的键是秒数字符串、值是事件名数组；只找效果帧。
        JsonObject timeline = animation.getAsJsonObject("timeline");
        for (var entry : timeline.entrySet()) {
            for (var event : entry.getValue().getAsJsonArray()) {
                if (event.getAsString().equals(CAST_EVENT)) {
                    return OptionalLong.of(Math.round(Double.parseDouble(entry.getKey()) * MILLIS_PER_SECOND));
                }
            }
        }
        return OptionalLong.empty();
    }

    private static JsonObject clip(ModContainer animations, String clip) throws IOException {
        // 读 PA 模组容器里打包后的剪辑；一个文件只有一段动画。
        var path = animations.findPath(CLIPS + clip + ".animation.json").orElseThrow();
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonObject clips = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("animations");
            return clips.entrySet().iterator().next().getValue().getAsJsonObject();
        }
    }

    private static List<TechniqueDefinition> definitions() {
        // 生产服务里本命名空间的全部功法。
        return TechniqueFabric.service().registry().definitions().stream()
                .filter(definition -> definition.getId().startsWith(EvokerTestPlayer.PREFIX))
                .toList();
    }
}
