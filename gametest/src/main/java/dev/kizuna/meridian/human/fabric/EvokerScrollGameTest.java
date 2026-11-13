package dev.kizuna.meridian.human.fabric;

import dev.kizuna.meridian.core.Qi;
import dev.kizuna.meridian.technique.fabric.TechniqueScrollDrops;
import dev.kizuna.meridian.technique.fabric.TechniqueScrollItem;
import dev.kizuna.meridian.technique.fabric.world.TechniqueStatusBenefits;
import dev.kizuna.meridian.technique.server.TechniqueStatusText;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.illager.Evoker;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 《养》的生存来源与两处提示：玩家杀死会这一卷的唤魔者才按 25% 掉卷轴；卷轴每式一行写境界与费用；
 * 状态说明按目录曲线写出当前倍率。唤魔者走 Threatening 的正式生成命令，掉落走原版掉落表。
 */
public final class EvokerScrollGameTest {
    private static final int TIMEOUT = 200;
    private static final int TECHNIQUE_LINES = 10;
    private static final int BIRTH_TICKS = 2;
    private static final int LOOT_ROLLS = 200;
    private static final int LEAST_DROPS = 25;
    private static final int MOST_DROPS = 75;
    private static final int LAST_HURT_TICKS = 100;
    private static final float LETHAL_DAMAGE = 1_000.0F;
    private static final int MAX_HITS = 5;
    private static final int REHIT_TICKS = 11;
    private static final int KILL_TICKS = BIRTH_TICKS + REHIT_TICKS * (MAX_HITS - 1) + 3;
    private static final int STATUS_TICKS = 100;
    private static final double QI = 2;
    private static final Identifier SCROLL = Identifier.fromNamespaceAndPath("meridian_evoker", "nourish_scroll");
    private static final List<String> METAL = EvokerTestPlayer.meridians(List.of("lung", "large_intestine"));
    private static final Map<UUID, Boolean> KNOWN_AT_DEATH = new HashMap<>();
    private static boolean listening;

    @GameTest(maxTicks = TIMEOUT)
    public void cultivatedEvokersDropNourishOnlyWhenKilledByPlayer(GameTestHelper context) {
        // 原版掉落入口各抽 200 次：游炁、真炁唤魔者被玩家打死约四分之一掉卷；凡人和非玩家击杀一卷不掉。
        EvokerTestPlayer.ready(context, fixture -> {
            ServerLevel level = context.getLevel();
            Evoker trueQi = spawn(context, "true_qi", new BlockPos(1, 2, 3));
            Evoker wandering = spawn(context, "wandering_qi", new BlockPos(3, 2, 3));
            Evoker mortal = spawn(context, "mortal", new BlockPos(5, 2, 3));
            EvokerTestPlayer.later(context, BIRTH_TICKS, List.of(fixture), () -> {
                context.assertTrue(knows(trueQi) && knows(wandering), "修炼者唤魔者没有学会《养》");
                context.assertTrue(!knows(mortal), "凡人唤魔者也学会了《养》");
                DamageSource byPlayer = level.damageSources().playerAttack(fixture.player);
                int trueQiDrops = roll(level, trueQi, byPlayer, fixture.player);
                int wanderingDrops = roll(level, wandering, byPlayer, fixture.player);
                System.out.printf(Locale.ROOT, "[五脏诀掉落] 玩家击杀 %d 次：真炁 %d 卷，游炁 %d 卷%n",
                        LOOT_ROLLS, trueQiDrops, wanderingDrops);
                context.assertTrue(within(trueQiDrops) && within(wanderingDrops),
                        "掉卷次数偏离 25%：真炁 " + trueQiDrops + "，游炁 " + wanderingDrops);
                context.assertTrue(roll(level, mortal, byPlayer, fixture.player) == 0, "凡人唤魔者也掉卷");
                context.assertTrue(roll(level, trueQi, level.damageSources().mobAttack(mortal), null) == 0,
                        "不是玩家打死的唤魔者也掉卷");
                EvokerTestPlayer.finish(context, List.of(fixture));
            });
        });
    }

    @GameTest(maxTicks = TIMEOUT)
    public void killedEvokerStillKnowsTheVolumeWhenLootRolls(GameTestHelper context) {
        // 真打死一只真炁唤魔者：抽掉落那一刻功法服务仍认得它会《养》。打不死就等无敌帧过去再补一刀。
        listenForDeaths();
        EvokerTestPlayer.ready(context, fixture -> {
            ServerLevel level = context.getLevel();
            Evoker evoker = spawn(context, "true_qi", new BlockPos(1, 2, 3));
            for (int hit = 0; hit < MAX_HITS; hit++) {
                EvokerTestPlayer.later(context, BIRTH_TICKS + hit * REHIT_TICKS, List.of(fixture), () -> {
                    if (evoker.isAlive()) {
                        evoker.hurtServer(level, level.damageSources().playerAttack(fixture.player), LETHAL_DAMAGE);
                    }
                });
            }
            EvokerTestPlayer.later(context, KILL_TICKS, List.of(fixture), () -> {
                context.assertTrue(!evoker.isAlive(), "补了 " + MAX_HITS + " 刀还没打死真炁唤魔者");
                Boolean known = KNOWN_AT_DEATH.get(evoker.getUUID());
                context.assertTrue(known != null, "打死唤魔者时没有抽原版唤魔者掉落表");
                context.assertTrue(known, "抽掉落时功法服务已经不认得这只唤魔者会《养》");
                EvokerTestPlayer.finish(context, List.of(fixture));
            });
        });
    }

    @GameTest(maxTicks = TIMEOUT)
    public void scrollTooltipListsRealmAndCostOfEveryTechnique(GameTestHelper context) {
        // 卷轴提示每式一行，末尾再有一行讲怎么学；境界与费用读生产目录，炼元五式写不耗真元。
        EvokerTestPlayer.ready(context, fixture -> {
            var lines = new ArrayList<String>();
            ItemStack stack = new ItemStack(scrollItem());
            stack.getItem().appendHoverText(stack, Item.TooltipContext.of(context.getLevel()), TooltipDisplay.DEFAULT,
                    line -> lines.add(line.getString()), TooltipFlag.NORMAL);
            System.out.println("[五脏诀卷轴提示] " + lines);
            context.assertTrue(lines.size() == TECHNIQUE_LINES + 1, "卷轴提示不是十式各一行再加一行学法：" + lines);
            assertLine(context, lines, "游炁，金行真元 0.3");
            assertLine(context, lines, "真炁，火行真元");
            assertLine(context, lines, "化元，木行真元");
            assertLine(context, lines, "游炁，不耗真元");
            EvokerTestPlayer.finish(context, List.of(fixture));
        });
    }

    @GameTest(maxTicks = TIMEOUT)
    public void metalArmsStatusTextFollowsTheCatalogCurve(GameTestHelper context) {
        // 放出金臂后看原版剩余时间：说明里两臂力量已到 ×1.25，写出保持到 20 s、24 s 归零，以及剩余秒数。
        EvokerTestPlayer.ready(context, fixture -> {
            fixture.setQi(METAL, Qi.QiAttribute.METAL, QI, EvokerTestPlayer.FREE_DENSITY);
            context.assertTrue(EvokerPaymentGameTest.succeeded(fixture.cast("metal_arms")), "金臂起手失败");
            EvokerTestPlayer.later(context, STATUS_TICKS, List.of(fixture), () -> {
                var registered = TechniqueStatusBenefits.get("meridian_evoker:metal_arms");
                var effect = fixture.player.getEffect(registered.effect());
                context.assertTrue(effect != null, "金臂收势后没有状态");
                List<String> lines = TechniqueStatusText.lines(registered.benefit(), effect.getDuration(),
                        slot -> slot.equals("muscles") ? "两臂" : "?");
                System.out.println("[五脏诀状态说明] " + lines);
                context.assertTrue(lines.getFirst().equals("两臂力量 ×1.25（最高 ×1.25 到第 20 s，第 24 s 归零）"),
                        "金臂说明不符：" + lines);
                context.assertTrue(lines.getLast().matches("第 [0-9.]+ s，剩 [0-9.]+ s"), "末行不是已过与剩余：" + lines);
                EvokerTestPlayer.finish(context, List.of(fixture));
            });
        });
    }

    private static Evoker spawn(GameTestHelper context, String tier, BlockPos relative) {
        // 走 Threatening 的正式生成命令，按档位生出有身体、按学卷表学过卷的唤魔者；关掉 AI 免得召唤尖牙和恼鬼。
        ServerLevel level = context.getLevel();
        Vec3 position = context.absoluteVec(Vec3.atBottomCenterOf(relative));
        CommandSourceStack source = level.getServer().createCommandSourceStack().withLevel(level).withSuppressedOutput();
        String command = String.format(Locale.ROOT, "meridian humanoid spawn minecraft:evoker %s %.2f %.2f %.2f",
                tier, position.x, position.y, position.z);
        level.getServer().getCommands().performPrefixedCommand(source, command);
        List<Evoker> evokers = level.getEntitiesOfClass(Evoker.class, new AABB(BlockPos.containing(position)));
        if (evokers.size() != 1) {
            throw new IllegalStateException("生成命令没有在原地生出一只唤魔者：" + command + " → " + evokers);
        }
        Evoker evoker = evokers.getFirst();
        evoker.setNoAi(true);
        return evoker;
    }

    private static boolean knows(Evoker evoker) {
        // 和掉落条件同一判定：功法服务里学会了本卷全部式。
        return new TechniqueScrollDrops.KnowsVolume(EvokerTestPlayer.VOLUME).knows(evoker);
    }

    private static int roll(ServerLevel level, Evoker evoker, DamageSource source, ServerPlayer killer) {
        // 用原版掉落入口抽 LOOT_ROLLS 次，数掉出来的《养》卷轴；killer 为空算非玩家击杀。
        boolean playerKilled = killer != null;
        if (playerKilled) {
            evoker.setLastHurtByPlayer(killer, LAST_HURT_TICKS);
        }
        ResourceKey<LootTable> table = EntityTypes.EVOKER.getDefaultLootTable().orElseThrow();
        List<ItemStack> drops = new ArrayList<>();
        for (int roll = 0; roll < LOOT_ROLLS; roll++) {
            evoker.dropFromLootTable(level, source, playerKilled, table, drops::add);
        }
        Item scroll = scrollItem();
        return drops.stream().filter(stack -> stack.is(scroll)).mapToInt(ItemStack::getCount).sum();
    }

    private static boolean within(int drops) {
        // 200 次 25%：均值 50、标准差约 6.1，取正负四个标准差。
        return drops >= LEAST_DROPS && drops <= MOST_DROPS;
    }

    private static Item scrollItem() {
        // 功法库按卷生成的卷轴物品。
        Item item = BuiltInRegistries.ITEM.getValue(SCROLL);
        if (!(item instanceof TechniqueScrollItem)) {
            throw new IllegalStateException("没有《养》卷轴物品：" + SCROLL);
        }
        return item;
    }

    private static void assertLine(GameTestHelper context, List<String> lines, String fragment) {
        // 至少一行写着这段境界与费用。
        context.assertTrue(lines.stream().anyMatch(line -> line.contains("：" + fragment)),
                "卷轴提示缺「" + fragment + "」：" + lines);
    }

    private static synchronized void listenForDeaths() {
        // 掉落表算完后在同一份掉落上下文里再判一次「会这一卷」，记下这只唤魔者死时的情况。
        if (listening) {
            return;
        }
        LootTableEvents.MODIFY_DROPS.register((table, lootContext, drops) -> {
            boolean evokerTable = table.unwrapKey().equals(EntityTypes.EVOKER.getDefaultLootTable());
            if (evokerTable && lootContext.getOptionalParameter(LootContextParams.THIS_ENTITY) instanceof Evoker evoker) {
                KNOWN_AT_DEATH.put(evoker.getUUID(), new TechniqueScrollDrops.KnowsVolume(EvokerTestPlayer.VOLUME)
                        .test(lootContext));
            }
        });
        listening = true;
    }
}
