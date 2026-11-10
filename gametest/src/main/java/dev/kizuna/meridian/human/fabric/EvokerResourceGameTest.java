package dev.kizuna.meridian.human.fabric;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kizuna.meridian.technique.fabric.TechniqueFabric;
import dev.kizuna.meridian.technique.fabric.world.TechniqueStatusBenefits;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueDefinition;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueEffect;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/** 实际加载的五脏诀内容包：目录进生产注册表，每个声音都能解析并带字幕，状态增益注册成原版效果。 */
public final class EvokerResourceGameTest {
    private static final String MOD_ID = "meridian_evoker";
    private static final String ASSETS = "assets/" + MOD_ID + "/";
    private static final int TECHNIQUES = 10;
    private static final List<String> STRENGTHEN = List.of(
            "metal_arms", "earth_legs", "fire_heart", "water_return", "wood_sinew");

    @GameTest
    public void catalogSoundsAndStatusesResolveInTheLoadedPack(GameTestHelper context) throws IOException {
        // 读模组容器里的最终资源，不从源码目录绕过打包；原版事件以服务端注册表核对。
        ModContainer mod = FabricLoader.getInstance().getModContainer(MOD_ID).orElseThrow();
        JsonObject sounds = read(mod, "sounds.json");
        JsonObject chinese = read(mod, "lang/zh_cn.json");
        JsonObject english = read(mod, "lang/en_us.json");
        for (var entry : sounds.entrySet()) {
            JsonObject event = entry.getValue().getAsJsonObject();
            String subtitle = event.get("subtitle").getAsString();
            context.assertTrue(chinese.has(subtitle) && english.has(subtitle), "字幕缺失：" + subtitle);
            for (var sound : event.getAsJsonArray("sounds")) {
                assertTarget(context, mod, entry.getKey(), sound.getAsJsonObject());
            }
        }
        for (TechniqueDefinition definition : definitions()) {
            assertCatalogSounds(context, sounds, definition);
        }
        context.assertTrue(definitions().size() == TECHNIQUES, "生产注册表里五脏诀式数不符：" + definitions().size());
        for (String technique : STRENGTHEN) {
            var registered = TechniqueStatusBenefits.get(EvokerTestPlayer.PREFIX + technique);
            context.assertTrue(registered.effect().isBound(), "状态增益没有注册成原版效果：" + technique);
        }
        for (var root : mod.getRootPaths()) {
            try (var files = Files.walk(root)) {
                context.assertTrue(files.noneMatch(path -> path.toString().endsWith(".class")), "内容包包含 Java 类");
            }
        }
        context.succeed();
    }

    private static void assertTarget(GameTestHelper context, ModContainer mod, String event, JsonObject sound) {
        // 原版目标必须在注册表里；本仓录音按 Minecraft 规则落在 sounds/<路径>.ogg。
        Identifier id = Identifier.parse(sound.get("name").getAsString());
        if (sound.has("type")) {
            context.assertTrue(sound.get("type").getAsString().equals("event")
                    && id.getNamespace().equals("minecraft") && BuiltInRegistries.SOUND_EVENT.containsKey(id),
                    "没有对应的原版声音：" + event + " → " + id);
            return;
        }
        String file = ASSETS + "sounds/" + id.getPath() + ".ogg";
        context.assertTrue(id.getNamespace().equals(MOD_ID) && mod.findPath(file).isPresent(),
                "本仓录音缺失：" + event + " → " + file);
    }

    private static void assertCatalogSounds(GameTestHelper context, JsonObject sounds, TechniqueDefinition definition) {
        // 目录只写本仓事件 ID，且每个都在 sounds.json 里登记。
        for (TechniqueEffect effect : definition.getEffectsList()) {
            if (!effect.hasSound()) {
                continue;
            }
            Identifier id = Identifier.parse(effect.getSound().getSoundId());
            context.assertTrue(id.getNamespace().equals(MOD_ID) && sounds.has(id.getPath()),
                    "目录声音没有登记：" + definition.getId() + " " + id);
        }
    }

    private static List<TechniqueDefinition> definitions() {
        // 生产服务里本命名空间的全部功法。
        return TechniqueFabric.service().registry().definitions().stream()
                .filter(definition -> definition.getId().startsWith(EvokerTestPlayer.PREFIX))
                .toList();
    }

    private static JsonObject read(ModContainer mod, String file) throws IOException {
        // 资源按 UTF-8 读取并解析为对象。
        var path = mod.findPath(ASSETS + file).orElseThrow();
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
