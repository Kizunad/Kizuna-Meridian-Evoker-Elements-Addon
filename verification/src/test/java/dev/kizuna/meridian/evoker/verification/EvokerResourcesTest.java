package dev.kizuna.meridian.evoker.verification;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueCatalog;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueDefinition;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueEffect;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** 正式包的声音、字幕、语言与状态图标逐项对上目录，且不带任何 Java 入口。 */
public final class EvokerResourcesTest {
    private static final String ASSETS = "assets/" + EvokerContent.NAMESPACE + "/";
    private static final String PREFIX = EvokerContent.NAMESPACE + ":";
    private static final List<String> LANGUAGES = List.of("zh_cn", "en_us");
    private static final int ICON_PIXELS = 18;

    @Test
    public void everyCatalogSoundResolvesWithSubtitles() throws IOException {
        // 目录只写本仓事件；每个事件在 sounds.json 里指向原版事件或本仓 OGG，并带中英字幕。
        JsonObject sounds = json(ASSETS + "sounds.json");
        Set<String> used = soundEvents(EvokerContent.catalog());
        assertEquals(used, new TreeSet<>(sounds.keySet()));
        for (String event : used) {
            JsonObject entry = sounds.getAsJsonObject(event);
            String subtitle = entry.get("subtitle").getAsString();
            assertEquals("subtitles." + EvokerContent.NAMESPACE + "." + event, subtitle);
            assertLanguageKey(subtitle);
            for (JsonElement element : entry.getAsJsonArray("sounds")) {
                assertSoundTarget(event, element.getAsJsonObject());
            }
        }
    }

    @Test
    public void namesAndStatusIconsCoverTheCatalog() throws IOException {
        // 书、卷、式名都有中英条目；每个状态增益有效果名和 18×18 的原版状态图标。
        TechniqueCatalog catalog = EvokerContent.catalog();
        catalog.getBooksList().forEach(book -> assertLanguageKey("book." + translationPath(book.getId())));
        catalog.getVolumesList().forEach(volume -> assertLanguageKey("volume." + translationPath(volume.getId())));
        for (TechniqueDefinition definition : catalog.getDefinitionsList()) {
            assertLanguageKey("technique." + translationPath(definition.getId()));
        }
        Set<String> statuses = statusIds(catalog);
        assertFalse(statuses.isEmpty());
        for (String status : statuses) {
            assertLanguageKey("effect." + translationPath(status));
            Path icon = resource(ASSETS + "textures/mob_effect/" + status.substring(PREFIX.length()) + ".png");
            BufferedImage image = ImageIO.read(icon.toFile());
            assertNotNull(icon.toString(), image);
            assertEquals(ICON_PIXELS, image.getWidth());
            assertEquals(ICON_PIXELS, image.getHeight());
        }
    }

    @Test
    public void fabricPackageIsPureData() throws IOException {
        // 正式包没有入口、混入或 Java 源；功法目录路径与 shared 的唯一来源一致。
        JsonObject metadata = json("fabric.mod.json");
        assertFalse(metadata.has("entrypoints"));
        assertFalse(metadata.has("mixins"));
        assertEquals("data/meridian_evoker/technique_catalog.txtpb",
                metadata.getAsJsonObject("custom").get("meridian_technique:catalogs").getAsString());
        JsonObject depends = metadata.getAsJsonObject("depends");
        assertTrue(depends.has("meridian_technique"));
        assertTrue(depends.has("meridian_human_host"));
        try (Stream<Path> files = Files.walk(EvokerContent.fabricResources().getParent())) {
            assertTrue(files.noneMatch(path -> path.toString().endsWith(".java")));
        }
    }

    private static void assertSoundTarget(String event, JsonObject sound) {
        // 原版事件必须写 type=event；本仓文件按 Minecraft 规则落在 sounds/<路径>.ogg。
        String name = sound.get("name").getAsString();
        if (sound.has("type")) {
            assertEquals(event, "event", sound.get("type").getAsString());
            assertTrue(event, name.startsWith("minecraft:"));
            return;
        }
        assertTrue(event, name.startsWith(PREFIX));
        Path file = resource(ASSETS + "sounds/" + name.substring(PREFIX.length()) + ".ogg");
        assertTrue(file.toString(), Files.isRegularFile(file));
    }

    private static void assertLanguageKey(String key) {
        // 中英两份语言文件都要有同一条目，且不是空串。
        for (String language : LANGUAGES) {
            JsonObject lang = jsonUnchecked(ASSETS + "lang/" + language + ".json");
            assertTrue(language + " 缺少 " + key, lang.has(key));
            assertFalse(language + " 空条目 " + key, lang.get(key).getAsString().isBlank());
        }
    }

    private static Set<String> soundEvents(TechniqueCatalog catalog) {
        // 收集目录里全部声音效果的事件路径，只允许本仓命名空间。
        var events = new TreeSet<String>();
        for (TechniqueDefinition definition : catalog.getDefinitionsList()) {
            for (TechniqueEffect effect : definition.getEffectsList()) {
                if (!effect.hasSound()) {
                    continue;
                }
                String id = effect.getSound().getSoundId();
                assertTrue(id, id.startsWith(PREFIX));
                events.add(id.substring(PREFIX.length()));
            }
        }
        return events;
    }

    private static Set<String> statusIds(TechniqueCatalog catalog) {
        // 状态增益 ID 决定原版效果注册名、语言键和图标路径。
        var ids = new TreeSet<String>();
        catalog.getDefinitionsList().forEach(definition -> definition.getEffectsList().stream()
                .filter(TechniqueEffect::hasStatusBenefit)
                .forEach(effect -> ids.add(effect.getStatusBenefit().getId())));
        return ids;
    }

    private static String translationPath(String id) {
        // 语言键把 namespace:path 写成 namespace.path。
        return id.replace(':', '.');
    }

    private static JsonObject json(String path) throws IOException {
        // 资源按 UTF-8 读取并解析为对象。
        return JsonParser.parseString(Files.readString(resource(path), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static JsonObject jsonUnchecked(String path) {
        // 断言辅助里不便声明受检异常，读取失败直接转为测试失败。
        try {
            return json(path);
        } catch (IOException exception) {
            throw new AssertionError(path, exception);
        }
    }

    private static Path resource(String path) {
        // fabric 资源根目录下的相对路径。
        return EvokerContent.fabricResources().resolve(path);
    }
}
