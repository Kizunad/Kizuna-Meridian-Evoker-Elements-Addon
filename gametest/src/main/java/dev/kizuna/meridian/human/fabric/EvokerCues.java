package dev.kizuna.meridian.human.fabric;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 五脏诀动作声的期望时间表：照原稿 §9.2 与动画稿 §5.12 手写，不从生产目录推导。 */
final class EvokerCues {
    private static final String NAMESPACE = "meridian_evoker";
    private static final double POSITION_TOLERANCE = 0.13;
    private static final double FLOAT_TOLERANCE = 0.00001;
    private static final long MILLIS_PER_TICK = 50;
    private static final long REFINE_START_MILLIS = 1_000;
    private static final long BREATH_CYCLE_MILLIS = 5_000;
    private static final int BREATHS = 6;
    private static final float BREATH_VOLUME = 0.3F;
    private static final float BREATH_PITCH = 1.0F;

    /** 一个声音：事件路径、相对施放的 tick、音量、音高。 */
    record Cue(String path, long tick, float volume, float pitch) {
    }

    /** 炼元一式的呼吸节拍：吸气与吐字在每轮里的偏移、吐字音节、本行提示音。 */
    private record Breath(long inhaleMillis, long exhaleMillis, String syllable, String element,
                          float cueVolume, float cuePitch) {
    }

    private static final Map<String, List<Cue>> STRENGTHEN = Map.of(
            "metal_arms", List.of(
                    cue("metal_arms.flap", 1_000, 0.6F, 0.8F),
                    cue("metal_arms.flap", 2_100, 0.6F, 0.8F),
                    cue("metal_arms.cast", 2_600, 0.6F, 1.2F)),
            "earth_legs", List.of(
                    cue("earth_legs.step", 1_400, 0.8F, 0.9F),
                    cue("earth_legs.step", 2_700, 0.8F, 0.9F),
                    cue("earth_legs.cast", 3_200, 0.6F, 0.8F)),
            "fire_heart", List.of(
                    cue("fire_heart.inhale", 800, 0.4F, 1.2F),
                    cue("fire_heart.cast", 1_800, 0.7F, 1.3F)),
            "water_return", List.of(
                    cue("water_return.step", 900, 0.6F, 1.0F),
                    cue("water_return.cast", 2_400, 0.8F, 1.0F)),
            "wood_sinew", List.of(
                    cue("wood_sinew.pounce", 2_800, 0.5F, 0.6F),
                    cue("wood_sinew.cast", 2_800, 0.5F, 0.8F)));

    private static final Map<String, Breath> REFINE = Map.of(
            "refine_lung", new Breath(0, 2_000, "si", "metal", 0.15F, 1.8F),
            "refine_spleen", new Breath(0, 1_000, "hu", "earth", 0.2F, 0.8F),
            "refine_heart", new Breath(1_000, 3_500, "he", "fire", 0.3F, 1.0F),
            "refine_kidney", new Breath(0, 2_000, "chui", "water", 0.4F, 1.0F),
            "refine_liver", new Breath(2_500, 500, "xu", "wood", 0.3F, 1.0F));

    private EvokerCues() {
        // 只提供静态期望表与核对。
    }

    static List<Cue> expected(String technique) {
        // 强身直接查表；炼元按六轮呼吸展开吸气、吐字与本行提示音。
        if (STRENGTHEN.containsKey(technique)) {
            return STRENGTHEN.get(technique);
        }
        Breath breath = REFINE.get(technique);
        if (breath == null) {
            throw new IllegalArgumentException("未知测试功法：" + technique);
        }
        var cues = new ArrayList<Cue>();
        for (int cycle = 0; cycle < BREATHS; cycle++) {
            long start = REFINE_START_MILLIS + cycle * BREATH_CYCLE_MILLIS;
            long exhale = start + breath.exhaleMillis();
            cues.add(cue("refine.inhale", start + breath.inhaleMillis(), BREATH_VOLUME, BREATH_PITCH));
            cues.add(cue("refine.exhale_" + breath.syllable(), exhale, BREATH_VOLUME, BREATH_PITCH));
            cues.add(cue("refine.cue_" + breath.element(), exhale, breath.cueVolume(), breath.cuePitch()));
        }
        return cues;
    }

    static void assertTimeline(GameTestHelper context, EvokerTestPlayer fixture, String technique, long started) {
        // 只核对本人位置的本仓事件；同一 tick 的几个声音不计先后，连接在下一 tick 才捕获，容一个 tick。
        var heard = new ArrayList<>(fixture.sounds().stream()
                .filter(sound -> sound.tick() > started)
                .filter(sound -> sound.id().getNamespace().equals(NAMESPACE))
                .filter(sound -> atPlayer(fixture, sound))
                .toList());
        List<Cue> cues = expected(technique);
        context.assertTrue(heard.size() == cues.size(),
                "动作声数量不符：" + technique + " 期望 " + cues.size() + " 实际 " + describe(heard, started));
        for (Cue cue : cues) {
            var match = heard.stream()
                    .filter(sound -> matches(cue, sound, started))
                    .findFirst();
            context.assertTrue(match.isPresent(), "缺少动作声：" + technique + " " + cue
                    + " 实际 " + describe(heard, started));
            heard.remove(match.orElseThrow());
        }
    }

    private static boolean matches(Cue cue, EvokerTestPlayer.HeardSound sound, long started) {
        // 事件、时刻窗口、玩家声音类别、音量与音高全部一致才算同一个声音。
        var packet = sound.packet();
        long offset = sound.tick() - started;
        boolean onTime = offset >= cue.tick() && offset <= cue.tick() + 1;
        return sound.id().getPath().equals(cue.path())
                && onTime
                && packet.getSource() == SoundSource.PLAYERS
                && Math.abs(packet.getVolume() - cue.volume()) < FLOAT_TOLERANCE
                && Math.abs(packet.getPitch() - cue.pitch()) < FLOAT_TOLERANCE;
    }

    private static boolean atPlayer(EvokerTestPlayer fixture, EvokerTestPlayer.HeardSound sound) {
        // 并排的用例玩家也会听到彼此，只取本人位置播出的声音。
        var packet = sound.packet();
        var player = fixture.player;
        return Math.abs(packet.getX() - player.getX()) < POSITION_TOLERANCE
                && Math.abs(packet.getY() - player.getY()) < POSITION_TOLERANCE
                && Math.abs(packet.getZ() - player.getZ()) < POSITION_TOLERANCE;
    }

    private static String describe(List<EvokerTestPlayer.HeardSound> heard, long started) {
        // 失败信息列出实际事件与相对 tick，便于对表。
        return heard.stream()
                .map(sound -> sound.id().getPath() + "@" + (sound.tick() - started))
                .toList()
                .toString();
    }

    private static Cue cue(String path, long millis, float volume, float pitch) {
        // 原稿按毫秒写时刻，网络观察按 tick。
        return new Cue(path, millis / MILLIS_PER_TICK, volume, pitch);
    }
}
