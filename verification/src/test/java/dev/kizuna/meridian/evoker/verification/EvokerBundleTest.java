package dev.kizuna.meridian.evoker.verification;

import dev.kizuna.meridian.human.server.HumanRefineRecipes;
import dev.kizuna.meridian.protocol.QiDensity;
import dev.kizuna.meridian.protocol.pb.AddonMessages.AddonBundle;
import dev.kizuna.meridian.protocol.pb.BodyMessages.OrganRecipe;
import dev.kizuna.meridian.protocol.pb.BodyMessages.Qi;
import dev.kizuna.meridian.protocol.pb.BodyMessages.QiPhase;
import dev.kizuna.meridian.protocol.pb.BodyMessages.Realm;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** 五脏炼化配方包经 Framework 与 Human 一起登记，每种器官 × 相态一条，炼化率照原稿 §4.3。 */
public final class EvokerBundleTest {
    private static final double INPUT_AMOUNT = 1;
    private static final long BATCH_MILLIS = 2_000;
    private static final double TOLERANCE = 1e-12;
    private static final Map<QiPhase, Double> RATES = Map.of(
            QiPhase.FREE, 0.08,
            QiPhase.GAS, 0.20,
            QiPhase.LIQUID, 0.40);
    private static final Map<QiPhase, Realm> PHASE_REALMS = Map.of(
            QiPhase.FREE, Realm.WANDERING_QI,
            QiPhase.GAS, Realm.TRUE_QI,
            QiPhase.LIQUID, Realm.ESSENCE_TRANSFORMATION);
    // 器官种类 → 本行；相态表里心、肾从真炁起，肝从化元起（本行开脉层）。
    private static final Map<String, String> ELEMENTS = Map.of(
            "human:lung", "metal",
            "human:spleen", "earth",
            "human:heart", "fire",
            "human:left_kidney", "water",
            "human:right_kidney", "water",
            "human:liver", "wood");
    private static final Map<String, List<QiPhase>> PHASES = Map.of(
            "human:lung", List.of(QiPhase.FREE, QiPhase.GAS, QiPhase.LIQUID),
            "human:spleen", List.of(QiPhase.FREE, QiPhase.GAS, QiPhase.LIQUID),
            "human:heart", List.of(QiPhase.GAS, QiPhase.LIQUID),
            "human:left_kidney", List.of(QiPhase.GAS, QiPhase.LIQUID),
            "human:right_kidney", List.of(QiPhase.GAS, QiPhase.LIQUID),
            "human:liver", List.of(QiPhase.LIQUID));

    @Test
    public void bundleRegistersWithHumanAndCoversEveryOrganPhase() throws IOException {
        // 依赖 human 修订 1，器官种类引用 Human 的符号；每条配方产物与投入同一密度。
        AddonBundle bundle = EvokerContent.bundle();
        assertEquals(EvokerContent.NAMESPACE, bundle.getManifest().getNamespace());
        assertEquals("human", bundle.getManifest().getDependencies(0).getNamespace());
        var registered = EvokerContent.addons().recipes().definitions().stream()
                .filter(recipe -> recipe.getId().startsWith(EvokerContent.RECIPE_FAMILY + "_"))
                .toList();
        assertEquals(bundle.getOrganRecipesCount(), registered.size());

        var covered = new TreeMap<String, List<QiPhase>>();
        for (OrganRecipe recipe : registered) {
            Qi input = single(recipe.getInput().getEntriesList());
            Qi output = single(recipe.getOutput().getEntriesList());
            QiPhase phase = QiDensity.phase(input.getDensity());
            assertEquals("meridian:none", input.getAttribute());
            assertEquals(INPUT_AMOUNT, input.getAmount(), TOLERANCE);
            assertEquals(input.getDensity(), output.getDensity(), TOLERANCE);
            assertEquals(QiDensity.baseline(phase), input.getDensity(), TOLERANCE);
            assertEquals("meridian:" + ELEMENTS.get(recipe.getOrganKind()), output.getAttribute());
            assertEquals(RATES.get(phase), output.getAmount(), TOLERANCE);
            assertEquals(PHASE_REALMS.get(phase), recipe.getMinimumRealm());
            assertEquals(BATCH_MILLIS, recipe.getDurationMillis());
            covered.computeIfAbsent(recipe.getOrganKind(), kind -> new ArrayList<>()).add(phase);
        }
        assertEquals(PHASES.keySet(), covered.keySet());
        for (var entry : PHASES.entrySet()) {
            assertEquals(entry.getKey(), entry.getValue(), covered.get(entry.getKey()).stream().sorted().toList());
        }
    }

    @Test
    public void humanRefineHostAcceptsTheRecipeFamily() throws IOException {
        // Human 宿主开服预读同一配方族：形状、单一投料与产物、器官 × 相态唯一都要过。
        List<OrganRecipe> recipes = EvokerContent.addons().recipes().definitions();
        var host = new HumanRefineRecipes(namespace -> recipes);
        host.validate(EvokerContent.RECIPE_FAMILY);
        assertTrue(recipes.stream().anyMatch(recipe -> recipe.getId().startsWith(EvokerContent.RECIPE_FAMILY)));
    }

    private static Qi single(List<Qi> entries) {
        // 炼化配方的投入与产物都只有一项明细。
        assertEquals(1, entries.size());
        return entries.getFirst();
    }
}
