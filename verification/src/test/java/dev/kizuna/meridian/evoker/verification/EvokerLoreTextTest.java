package dev.kizuna.meridian.evoker.verification;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 《附》五式正文里的生克口诀：每式写明打在哪一行偏盛的人身上重、遇哪一行偏盛的人轻，
 * 和相克顺序（金克木、木克土、土克水、水克火、火克金）一致；世界内文本不许有机制说法和英文。
 */
public final class EvokerLoreTextTest {
    private static final String INFUSE_VOLUME = "meridian_evoker:infuse";
    /** 相克顺序：每一行克它后面那一行，最后一行克第一行。 */
    private static final List<String> OVERCOMES = List.of("金", "木", "土", "水", "火");
    private static final Map<String, String> ELEMENTS = Map.of(
            "meridian_evoker:metal_edge", "金",
            "meridian_evoker:wood_thunder", "木",
            "meridian_evoker:earth_quake", "土",
            "meridian_evoker:water_frost", "水",
            "meridian_evoker:fire_burn", "火");
    private static final List<String> MECHANIC_WORDS = List.of(
            "分数", "倍率", "机制", "判定", "冷却", "窗口", "界面", "按键", "右键", "左键", "主手", "副手", "×", "%");
    private static final Pattern LATIN = Pattern.compile("[A-Za-z]");

    @Test
    public void everyInfusionNamesWhatItOvercomesAndWhatOvercomesIt() throws IOException {
        // 按相克顺序算出每式克谁、被谁克，正文里两句都要对得上。
        var registry = EvokerContent.registry();
        var volume = registry.findVolume(INFUSE_VOLUME).orElseThrow();
        assertEquals(ELEMENTS.size(), volume.getTechniquesCount());
        for (var reference : volume.getTechniquesList()) {
            String element = ELEMENTS.get(reference.getId());
            int index = OVERCOMES.indexOf(element);
            String victim = OVERCOMES.get((index + 1) % OVERCOMES.size());
            String master = OVERCOMES.get((index + OVERCOMES.size() - 1) % OVERCOMES.size());
            String text = description(reference.getId());
            assertTrue(reference.getId() + " 没写克" + victim,
                    text.contains("打在" + victim + "行偏盛的人身上重一半"));
            assertTrue(reference.getId() + " 没写被" + master + "克",
                    text.contains("遇" + master + "行偏盛的人只剩一半"));
        }
    }

    @Test
    public void textUsesNoMechanicWords() throws IOException {
        // 全书十五式正文里找机制说法和英文字母，一处都不许有。
        List<String> found = new ArrayList<>();
        for (var definition : EvokerContent.registry().definitions()) {
            String text = definition.getPresentation().getDescription();
            for (String word : MECHANIC_WORDS) {
                if (text.contains(word)) {
                    found.add(definition.getId() + "：" + word);
                }
            }
            if (LATIN.matcher(text).find()) {
                found.add(definition.getId() + "：英文字母");
            }
        }
        assertTrue("正文里有机制说法：" + found, found.isEmpty());
    }

    private static String description(String techniqueId) throws IOException {
        // 从注册后的目录取这一式的正文，和卷轴上显示的是同一份。
        return EvokerContent.registry().definitions().stream()
                .filter(definition -> definition.getId().equals(techniqueId))
                .findFirst()
                .orElseThrow()
                .getPresentation()
                .getDescription();
    }
}
