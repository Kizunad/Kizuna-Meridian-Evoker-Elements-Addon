package dev.kizuna.meridian.human.fabric;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ServerboundPlayerLoadedPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;

/** 「修行」页功法线的唤魔者分支：打掠夺者不算，跟唤魔者交过手（打它或被它打）才完成。 */
public final class EvokerAdvancementGameTest {
    private static final int TIMEOUT = 100;
    private static final float HIT = 1.0F;
    private static final BlockPos MOB_POS = new BlockPos(1, 1, 1);
    private static final Identifier EVOKER = Identifier.parse("meridian_evoker:cultivators/evoker");

    @GameTest(maxTicks = TIMEOUT)
    public void hittingEvokerCompletesItsLeaf(GameTestHelper context) {
        // 先打一只掠夺者，唤魔者那条不动；再打唤魔者才完成。
        ServerPlayer player = player(context);
        try {
            hit(context, player, context.spawn(EntityTypes.PILLAGER, MOB_POS));
            context.assertFalse(done(player), "打掠夺者完成了五脏诀");
            hit(context, player, context.spawn(EntityTypes.EVOKER, MOB_POS));
            context.assertTrue(done(player), "打唤魔者没完成五脏诀");
        } finally {
            release(player);
        }
        context.succeed();
    }

    @GameTest(maxTicks = TIMEOUT)
    public void beingHitByEvokerCompletesItsLeaf(GameTestHelper context) {
        // 被唤魔者打一下也算交过手。
        ServerPlayer player = player(context);
        try {
            LivingEntity evoker = context.spawn(EntityTypes.EVOKER, MOB_POS);
            context.assertFalse(done(player), "没交手就完成了五脏诀");
            player.hurtServer(context.getLevel(), player.damageSources().mobAttack(evoker), HIT);
            evoker.discard();
            context.assertTrue(done(player), "挨了唤魔者一下没完成五脏诀");
        } finally {
            release(player);
        }
        context.succeed();
    }

    private static ServerPlayer player(GameTestHelper context) {
        // 生存模式的测试玩家，先报一次客户端已载入：没载入的玩家服务器不让受伤。
        ServerPlayer player = context.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.connection.handleAcceptPlayerLoad(new ServerboundPlayerLoadedPacket());
        return player;
    }

    private static void hit(GameTestHelper context, ServerPlayer player, LivingEntity target) {
        // 走原版受伤入口，伤害来源是这名玩家的近战；打完就清掉。
        target.hurtServer(context.getLevel(), player.damageSources().playerAttack(player), HIT);
        target.discard();
    }

    private static void release(ServerPlayer player) {
        // 测试玩家下线，免得留在同一服务器的后续测试里。
        player.level().getServer().getPlayerList().remove(player);
    }

    private static boolean done(ServerPlayer player) {
        // 进度文件必须已加载，缺了直接报错而不是当作未完成。
        AdvancementHolder advancement = player.level().getServer().getAdvancements().get(EVOKER);
        if (advancement == null) {
            throw new AssertionError("进度没有加载：" + EVOKER);
        }
        return player.getAdvancements().getOrStartProgress(advancement).isDone();
    }
}
