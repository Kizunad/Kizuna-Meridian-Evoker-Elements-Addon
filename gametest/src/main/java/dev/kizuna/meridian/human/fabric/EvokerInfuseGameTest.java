package dev.kizuna.meridian.human.fabric;

import dev.kizuna.meridian.core.Qi;
import dev.kizuna.meridian.protocol.BodyProtobuf;
import dev.kizuna.meridian.protocol.pb.BodyMessages.Realm;
import dev.kizuna.meridian.protocol.pb.HealthMessages.DamageKind;
import dev.kizuna.meridian.technique.fabric.TechniqueFabric;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.CastOutcome;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.CastPhase;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.CastRequest;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueFailure;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueReference;
import dev.kizuna.meridian.technique.server.TechniqueService;
import dev.kizuna.meridian.vanilla.fabric.VanillaDamageFabric;
import dev.kizuna.meridian.vanilla.fabric.VanillaDamageTypeTags;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** 中卷五式端到端：境界与费用、附行起止、附伤落点与原版附带、相克、来源和声音，照 C6b PLAN 第 3 段。 */
public final class EvokerInfuseGameTest {
    private static final List<VanillaDamageFabric.DamageEvent> EVENTS = new ArrayList<>();
    private static final int TIMEOUT = 200;
    private static final int EXPIRY_TIMEOUT = 480;
    private static final int BEFORE_INFUSION_TICKS = 38;
    private static final int RAMP_TICKS = 42;
    private static final int PLATEAU_TICKS = 55;
    private static final int STAGGER_TICKS = 20;
    private static final int RECOVERED_TICKS = 75;
    private static final int EXPIRED_TICKS = 404;
    private static final int CUES_TICKS = 60;
    private static final int KNOCK_TICKS = 30;
    private static final int SOUND_TICKS = 2;
    // 原版尖牙生成后第 8 tick 咬下，多等几 tick。
    private static final int FANG_BITE_TICKS = 12;
    private static final long STAGGER_MILLIS = 500;
    private static final double STAGGER_SPEED = 0.5;
    private static final double QI = 2;
    private static final double RATIO = 0.25;
    private static final double RAMP_VALUE = 0.2;
    private static final double WIN = 1.5;
    private static final double LOSE = 0.5;
    private static final double LOSE_VOLUME = 0.6;
    private static final double LOSE_PITCH = 0.2;
    private static final double HIT_VOLUME = 0.5;
    private static final double HIT_PITCH = 1.0;
    private static final float STRIKE = 4;
    private static final float FANG_DAMAGE = 6;
    private static final int IGNITE_TICKS = 20;
    private static final int FREEZE_TICKS = 50;
    private static final double MAIN_KNOCKBACK = 0.4;
    private static final double EXTRA_KNOCKBACK = 0.3;
    private static final int COW_DISTANCE = 3;
    private static final double KNOCK_DISTANCE = 2;
    private static final float FACING_SOUTH = 0;
    private static final float FACING_NORTH = 180;
    private static final double TOLERANCE = 1e-5;
    private static final double POSITION_TOLERANCE = 0.13;
    private static final String NAMESPACE = "meridian_evoker";
    private static final String KNOCK = "meridian_door_breaker:knock";
    private static final String DOOR_BREAKER_VOLUME = "meridian_door_breaker:upper";
    private static final String DOOR_BREAKER = "meridian_door_breaker";
    private static final String PEDDLER_KICK = "meridian_peddler:axe_kick";
    private static final List<String> HANDS = EvokerTestPlayer.meridians(List.of("lung", "large_intestine",
            "heart", "small_intestine", "pericardium", "triple_burner"));

    /** 一行中卷：行名、式名、最低境界、本行属性、表里两经、Damage 的伤害种类标签名。 */
    private record Element(String name, String technique, Realm realm, Qi.QiAttribute attribute,
                           List<String> kinds, String kind) {
        String damageType() {
            // 伤害类型按行名命名。
            return NAMESPACE + ":" + name;
        }

        String hitSound() {
            // 命中声事件也按行名命名。
            return NAMESPACE + ":hit." + name;
        }
    }

    private static final Element METAL = new Element("metal", "metal_edge", Realm.TRUE_QI, Qi.QiAttribute.METAL,
            List.of("lung", "large_intestine"), "cutting");
    private static final Element EARTH = new Element("earth", "earth_quake", Realm.TRUE_QI, Qi.QiAttribute.EARTH,
            List.of("spleen", "stomach"), "blunt");
    private static final Element FIRE = new Element("fire", "fire_burn", Realm.TRUE_QI, Qi.QiAttribute.FIRE,
            List.of("heart", "small_intestine"), "heat");
    private static final Element WATER = new Element("water", "water_frost", Realm.TRUE_QI, Qi.QiAttribute.WATER,
            List.of("kidney", "bladder"), "cold");
    private static final Element WOOD = new Element("wood", "wood_thunder", Realm.ESSENCE_TRANSFORMATION,
            Qi.QiAttribute.WOOD, List.of("liver", "gallbladder"), "electric");
    private static final Map<DamageKind, Element> KINDS = Map.of(DamageKind.CUTTING, METAL, DamageKind.BLUNT, EARTH,
            DamageKind.HEAT, FIRE, DamageKind.COLD, WATER, DamageKind.ELECTRIC, WOOD);

    static {
        // 观察方只读身体提交后的事实；主击与附伤各是一条，按目标筛出本用例的记录。
        VanillaDamageFabric.registerDamageObserver(EVENTS::add);
    }

    @GameTest(maxTicks = TIMEOUT)
    public void realmAndGasPhaseGateTheMiddleVolume(GameTestHelper context) {
        // 游炁不能起灼火、真炁不能起惊雷；本行真元只有游离态时付不了要气态的费用。
        EvokerTestPlayer.readyInfuse(context, 1, players -> {
            EvokerTestPlayer fixture = players.getFirst();
            charge(fixture, FIRE, EvokerTestPlayer.GAS_DENSITY);
            charge(fixture, WOOD, EvokerTestPlayer.GAS_DENSITY);
            assertRejected(context, fixture.cast(FIRE.technique()), TechniqueFailure.REALM_TOO_LOW,
                    "境界不够：这一式要真炁，你现在是游炁");
            fixture.enterRealm(Realm.TRUE_QI);
            assertRejected(context, fixture.cast(WOOD.technique()), TechniqueFailure.REALM_TOO_LOW,
                    "境界不够：这一式要化元，你现在是真炁");
            charge(fixture, FIRE, EvokerTestPlayer.FREE_DENSITY);
            assertRejected(context, fixture.cast(FIRE.technique()), TechniqueFailure.INSUFFICIENT_ATTRIBUTE_QI,
                    "左手少阴心经 等 4 条经脉的火行真元不够：要气态以上 0.6，可付 0；另有游离态 8，不算");
            EvokerTestPlayer.finish(context, players);
        });
    }

    @GameTest(maxTicks = TIMEOUT)
    public void middleVolumeNeedsTheUpperVolumeFirst(GameTestHelper context) {
        // 没学会《养》时学《附》被拒并点名前置卷；学会《养》后再学就成。
        EvokerTestPlayer.unlearned(context, fixture -> {
            UUID id = fixture.player.getUUID();
            String refused = "";
            try {
                TechniqueFabric.service().learn(id, EvokerTestPlayer.INFUSE_VOLUME);
            } catch (TechniqueService.PrerequisiteVolumeException expected) {
                refused = expected.getMessage();
            }
            context.assertTrue(refused.equals("请先学会前置卷：" + EvokerTestPlayer.VOLUME),
                    "没学上卷就学中卷没有被拒：" + refused);
            TechniqueFabric.service().learn(id, EvokerTestPlayer.VOLUME);
            var known = TechniqueFabric.service().learn(id, EvokerTestPlayer.INFUSE_VOLUME);
            context.assertTrue(!known.isEmpty(), "学会上卷后仍学不了中卷");
            EvokerTestPlayer.finish(context, List.of(fixture));
        });
    }

    @GameTest(maxTicks = TIMEOUT)
    public void infusionStartsWhenTheWarmupEnds(GameTestHelper context) {
        // 起手 1.9 s 时还没有附行，尖牙打出去不带附伤；2.1 s 时附行在爬坡，附伤按曲线值 0.2 折算。
        EvokerTestPlayer.readyInfuse(context, 3, players -> {
            EvokerTestPlayer caster = castOn(context, players.getFirst(), FIRE);
            EvokerTestPlayer.later(context, BEFORE_INFUSION_TICKS, players, () -> {
                context.assertTrue(remainingMillis(caster) == 0, "起手还没结束就有附行");
                LivingEntity early = players.get(1).player;
                fang(context, caster.player, early);
                context.assertTrue(events(early).size() == 1, "起手结束前的尖牙带了附伤");
                // 同一用例只留一条回调链：并排的两条会让后一条晚一两 tick，爬坡上的剂量就对不上。
                EvokerTestPlayer.later(context, RAMP_TICKS - BEFORE_INFUSION_TICKS, players, () -> {
                    context.assertTrue(remainingMillis(caster) > 0, "起手结束后没有附行");
                    LivingEntity late = players.get(2).player;
                    fang(context, caster.player, late);
                    assertInfusion(context, late, FIRE, FANG_DAMAGE * RATIO * RAMP_VALUE);
                    EvokerTestPlayer.finish(context, players);
                });
            });
        });
    }

    @GameTest(maxTicks = TIMEOUT)
    public void spawnedFangOwnedByThePlayerCarriesTheInfusion(GameTestHelper context) {
        // 照原版唤魔者的做法生成一只尖牙、构造时传入玩家当主人，让它自己咬下：主击记在玩家名下，带火行附伤。
        EvokerTestPlayer.readyInfuse(context, 2, players -> {
            EvokerTestPlayer caster = castOn(context, players.getFirst(), FIRE);
            LivingEntity target = players.get(1).player;
            EvokerTestPlayer.later(context, PLATEAU_TICKS, players, () -> {
                var fangs = new EvokerFangs(context.getLevel(), target.getX(), target.getY(), target.getZ(), 0, 0,
                        caster.player);
                context.getLevel().addFreshEntity(fangs);
                EvokerTestPlayer.later(context, FANG_BITE_TICKS, players, () -> {
                    var infusion = assertInfusion(context, target, FIRE, FANG_DAMAGE * RATIO);
                    context.assertTrue(infusion.attacker() == caster.player, "尖牙的附伤没有记在玩家名下");
                    context.assertTrue(infusion.source().getDirectEntity() == fangs, "附伤的直接实体不是这只尖牙");
                    EvokerTestPlayer.finish(context, players);
                });
            });
        });
    }

    @GameTest(maxTicks = TIMEOUT)
    public void interruptedWarmupNeverInfuses(GameTestHelper context) {
        // 起手中受击被打断：效果时刻没到，附行不写，之后的尖牙不带附伤。
        EvokerTestPlayer.readyInfuse(context, 2, players -> {
            EvokerTestPlayer caster = castOn(context, players.getFirst(), WATER);
            EvokerTestPlayer.later(context, STAGGER_TICKS, players, () -> {
                var stagger = new TechniqueService.Stagger(STAGGER_MILLIS, STAGGER_SPEED);
                var interrupted = TechniqueFabric.service().stagger(caster.player.getUUID(), stagger);
                context.assertTrue(interrupted.isPresent() && interrupted.get().getPhase() == CastPhase.INTERRUPTED,
                        "受击没有打断寒水起手：" + interrupted);
                EvokerTestPlayer.later(context, PLATEAU_TICKS - STAGGER_TICKS, players, () -> {
                    context.assertTrue(remainingMillis(caster) == 0, "被打断的起手仍写了附行");
                    LivingEntity target = players.get(1).player;
                    fang(context, caster.player, target);
                    context.assertTrue(events(target).size() == 1, "被打断后尖牙仍带附伤");
                    EvokerTestPlayer.finish(context, players);
                });
            });
        });
    }

    @GameTest(maxTicks = TIMEOUT)
    public void laterInfusionReplacesTheEarlierOne(GameTestHelper context) {
        // 灼火放完再放寒水：下一击只带一记寒冷，冻结不点火。
        EvokerTestPlayer.readyInfuse(context, 2, players -> {
            EvokerTestPlayer caster = players.getFirst();
            charge(caster, WATER, EvokerTestPlayer.GAS_DENSITY);
            castOn(context, caster, FIRE);
            EvokerTestPlayer.later(context, RECOVERED_TICKS, players, () -> {
                CastOutcome outcome = caster.cast(WATER.technique());
                context.assertTrue(EvokerPaymentGameTest.succeeded(outcome), "灼火之后寒水起手失败：" + outcome);
                EvokerTestPlayer.later(context, PLATEAU_TICKS, players, () -> {
                    LivingEntity target = players.get(1).player;
                    fang(context, caster.player, target);
                    assertInfusion(context, target, WATER, FANG_DAMAGE * RATIO);
                    context.assertTrue(target.getTicksFrozen() == FREEZE_TICKS && target.getRemainingFireTicks() <= 0,
                            "后放的寒水没有覆盖灼火：冻结 " + target.getTicksFrozen() + " 着火 "
                                    + target.getRemainingFireTicks());
                    EvokerTestPlayer.finish(context, players);
                });
            });
        });
    }

    @GameTest(maxTicks = EXPIRY_TIMEOUT)
    public void infusionEndsEighteenSecondsAfterTheWarmup(GameTestHelper context) {
        // 附行曲线 18 s 走完即到期：剩余为零，尖牙不再带附伤。
        EvokerTestPlayer.readyInfuse(context, 2, players -> {
            EvokerTestPlayer caster = castOn(context, players.getFirst(), METAL);
            EvokerTestPlayer.later(context, EXPIRED_TICKS, players, () -> {
                context.assertTrue(remainingMillis(caster) == 0, "附行 18 s 后没有到期：" + remainingMillis(caster));
                LivingEntity target = players.get(1).player;
                fang(context, caster.player, target);
                context.assertTrue(events(target).size() == 1, "附行到期后尖牙仍带附伤");
                EvokerTestPlayer.finish(context, players);
            });
        });
    }

    @GameTest(maxTicks = TIMEOUT)
    public void deathClearsTheInfusionAndRespawnStartsWithout(GameTestHelper context) {
        // 修炼身体死亡时死前清理清掉附行；重生后的新实体用尖牙打牛，牛只吃原版那一下。
        EvokerTestPlayer.readyInfuse(context, 1, players -> {
            EvokerTestPlayer caster = castOn(context, players.getFirst(), FIRE);
            EvokerTestPlayer.later(context, PLATEAU_TICKS, players, () -> {
                context.assertTrue(remainingMillis(caster) > 0, "死前没有附行");
                caster.kill();
                context.assertTrue(caster.body().snapshot().vitality().dead(), "致死伤害没有记到身体");
                context.assertTrue(remainingMillis(caster) == 0, "身体死亡后附行仍在");
                ServerPlayer revived = caster.respawn();
                context.assertTrue(remainingMillis(caster) == 0, "重生后又有附行");
                Mob cow = cow(context, revived);
                float health = cow.getHealth();
                fang(context, revived, cow);
                assertClose(context, health - cow.getHealth(), FANG_DAMAGE, "重生后的尖牙仍带附伤");
                EvokerTestPlayer.finish(context, players);
            });
        });
    }

    @GameTest(maxTicks = TIMEOUT)
    public void playerMeleeInfusesTheSamePartOfAnotherPlayer(GameTestHelper context) {
        // 玩家近战打第二个测试玩家：附伤落在主击同一部位，记在施放者名下，并点着目标。
        EvokerTestPlayer.readyInfuse(context, 2, players -> {
            EvokerTestPlayer caster = castOn(context, players.getFirst(), FIRE);
            EvokerTestPlayer.later(context, PLATEAU_TICKS, players, () -> {
                ServerPlayer target = players.get(1).player;
                target.hurtServer(context.getLevel(), caster.player.damageSources().playerAttack(caster.player),
                        STRIKE);
                var infusion = assertInfusion(context, target, FIRE, STRIKE * RATIO);
                context.assertTrue(infusion.source().getDirectEntity() == caster.player, "近战附伤的直接实体不是施放者");
                context.assertTrue(target.getRemainingFireTicks() == IGNITE_TICKS,
                        "灼火附伤没有点着目标：" + target.getRemainingFireTicks());
                EvokerTestPlayer.finish(context, players);
            });
        });
    }

    @GameTest(maxTicks = TIMEOUT)
    public void metalEdgeAddsNoVanillaExtras(GameTestHelper context) {
        // 锐金只有切割附伤，不点火、不冻结、不追加击退。
        extras(context, METAL, cow -> assertExtras(context, cow, 0, 0, MAIN_KNOCKBACK));
    }

    @GameTest(maxTicks = TIMEOUT)
    public void earthQuakePushesOnTopOfTheMainKnockback(GameTestHelper context) {
        // 震土在主击的原版击退上再推 0.3。
        extras(context, EARTH, cow -> assertExtras(context, cow, 0, 0, MAIN_KNOCKBACK + EXTRA_KNOCKBACK));
    }

    @GameTest(maxTicks = TIMEOUT)
    public void fireBurnIgnitesTheTarget(GameTestHelper context) {
        // 灼火点火 20 刻。
        extras(context, FIRE, cow -> assertExtras(context, cow, IGNITE_TICKS, 0, MAIN_KNOCKBACK));
    }

    @GameTest(maxTicks = TIMEOUT)
    public void waterFrostFreezesTheTarget(GameTestHelper context) {
        // 寒水冻结 +50 刻。
        extras(context, WATER, cow -> assertExtras(context, cow, 0, FREEZE_TICKS, MAIN_KNOCKBACK));
    }

    @GameTest(maxTicks = TIMEOUT)
    public void woodThunderAddsNoVanillaExtras(GameTestHelper context) {
        // 惊雷只有雷电附伤，没有原版附带。
        extras(context, WOOD, cow -> assertExtras(context, cow, 0, 0, MAIN_KNOCKBACK));
    }

    @GameTest(maxTicks = TIMEOUT)
    public void countersScaleDamageAndHitSound(GameTestHelper context) {
        // 玩家拥有的尖牙打三个目标：主属性金行 ×1.5、水行 ×0.5、没身体的牛 ×1；命中声在目标处按相克调音量音高。
        EvokerTestPlayer.readyInfuse(context, 3, players -> {
            EvokerTestPlayer caster = castOn(context, players.getFirst(), FIRE);
            EvokerTestPlayer metal = players.get(1);
            EvokerTestPlayer water = players.get(2);
            charge(metal, METAL, EvokerTestPlayer.GAS_DENSITY);
            charge(water, WATER, EvokerTestPlayer.GAS_DENSITY);
            Mob cow = cow(context, caster.player);
            EvokerTestPlayer.later(context, PLATEAU_TICKS, players, () -> {
                fang(context, caster.player, metal.player);
                fang(context, caster.player, water.player);
                float health = cow.getHealth();
                fang(context, caster.player, cow);
                assertInfusion(context, metal.player, FIRE, FANG_DAMAGE * RATIO * WIN);
                assertInfusion(context, water.player, FIRE, FANG_DAMAGE * RATIO * LOSE);
                assertClose(context, health - cow.getHealth(), FANG_DAMAGE * (1 + RATIO), "无身体目标的附伤不是 ×1");
                // 声音包在下一 tick 开头才记下；接在同一条回调链上，不另起一条并排的链。
                EvokerTestPlayer.later(context, SOUND_TICKS, players, () -> {
                    assertHitSound(context, caster, metal.player, HIT_VOLUME * WIN, HIT_PITCH);
                    assertHitSound(context, caster, water.player, HIT_VOLUME * LOSE_VOLUME, HIT_PITCH + LOSE_PITCH);
                    assertHitSound(context, caster, cow, HIT_VOLUME, HIT_PITCH);
                    EvokerTestPlayer.finish(context, players);
                });
            });
        });
    }

    @GameTest(maxTicks = TIMEOUT)
    public void doorBreakerKnockCarriesTheInfusion(GameTestHelper context) {
        // 锐金附行之后放破门斩叩门：功法库排出的每一记叩击后面都跟一记切割附伤，落在同一部位。
        EvokerTestPlayer.readyInfuse(context, 2, players -> {
            EvokerTestPlayer caster = players.getFirst();
            caster.setQi(HANDS, Qi.QiAttribute.NONE, QI, EvokerTestPlayer.FREE_DENSITY);
            castOn(context, caster, METAL);
            ServerPlayer target = players.get(1).player;
            EvokerTestPlayer.later(context, RECOVERED_TICKS, players, () -> {
                Vec3 front = caster.player.position().add(0, 0, KNOCK_DISTANCE);
                target.snapTo(front.x, front.y, front.z, FACING_NORTH, 0);
                caster.player.snapTo(caster.player.getX(), caster.player.getY(), caster.player.getZ(), FACING_SOUTH, 0);
                caster.player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_AXE));
                TechniqueFabric.service().learn(caster.player.getUUID(), DOOR_BREAKER_VOLUME);
                CastOutcome outcome = knock(caster, target);
                context.assertTrue(EvokerPaymentGameTest.succeeded(outcome), "叩门起手失败：" + outcome);
                EvokerTestPlayer.later(context, KNOCK_TICKS, players, () -> {
                    assertKnocksInfused(context, target, caster.player);
                    EvokerTestPlayer.finish(context, players);
                });
            });
        });
    }

    @GameTest(maxTicks = TIMEOUT)
    public void infusionDamageTypesCarryTheirTags(GameTestHelper context) {
        // 运行期数据包：五个附伤类型进本行种类和原版两条标签，不进着火、冻伤和可附伤标签；千里腿的踢可附伤。
        var types = context.getLevel().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE);
        for (Element element : KINDS.values()) {
            var type = types.getOrThrow(damageKey(element.damageType()));
            var kind = TagKey.create(Registries.DAMAGE_TYPE, Identifier.parse("meridian:kind/" + element.kind()));
            context.assertTrue(type.is(kind) && type.is(DamageTypeTags.NO_KNOCKBACK)
                    && type.is(DamageTypeTags.BYPASSES_COOLDOWN), "附伤类型缺标签：" + element.damageType());
            context.assertTrue(!type.is(DamageTypeTags.IS_FIRE) && !type.is(DamageTypeTags.IS_FREEZING)
                    && !type.is(VanillaDamageTypeTags.INFUSABLE), "附伤类型多了标签：" + element.damageType());
        }
        context.assertTrue(types.getOrThrow(damageKey(PEDDLER_KICK)).is(VanillaDamageTypeTags.INFUSABLE),
                "千里腿的踢不在可附伤标签里");
        context.succeed();
    }

    @GameTest(maxTicks = TIMEOUT)
    public void metalEdgeCastSoundsFollowTheTimeline(GameTestHelper context) {
        // 锐金：抬手、抹锋、起势。
        castSounds(context, METAL);
    }

    @GameTest(maxTicks = TIMEOUT)
    public void earthQuakeCastSoundsFollowTheTimeline(GameTestHelper context) {
        // 震土：抬手、顿地、起势。
        castSounds(context, EARTH);
    }

    @GameTest(maxTicks = TIMEOUT)
    public void fireBurnCastSoundsFollowTheTimeline(GameTestHelper context) {
        // 灼火：抬手、搓掌、吹气、火星、起势。
        castSounds(context, FIRE);
    }

    @GameTest(maxTicks = TIMEOUT)
    public void waterFrostCastSoundsFollowTheTimeline(GameTestHelper context) {
        // 寒水：抬手、三次下按、起势。
        castSounds(context, WATER);
    }

    @GameTest(maxTicks = TIMEOUT)
    public void woodThunderCastSoundsFollowTheTimeline(GameTestHelper context) {
        // 惊雷：抬手、击掌两声同刻、起势。
        castSounds(context, WOOD);
    }

    private static EvokerTestPlayer castOn(GameTestHelper context, EvokerTestPlayer caster, Element element) {
        // 到达本式境界、本行表里经放足气态本行真元后起手，起手必须成功。
        caster.enterRealm(element.realm());
        charge(caster, element, EvokerTestPlayer.GAS_DENSITY);
        CastOutcome outcome = caster.cast(element.technique());
        context.assertTrue(EvokerPaymentGameTest.succeeded(outcome), "中卷起手失败：" + element + " " + outcome);
        return caster;
    }

    private static void charge(EvokerTestPlayer fixture, Element element, double density) {
        // 本行表里经左右四条按给定密度放本行真元。
        fixture.setQi(EvokerTestPlayer.meridians(element.kinds()), element.attribute(), QI, density);
    }

    private static void extras(GameTestHelper context, Element element, Consumer<Mob> check) {
        // 平台期近战打一头静止、不受重力的牛（没有身体），打完立刻看原版附带。
        EvokerTestPlayer.readyInfuse(context, 1, players -> {
            EvokerTestPlayer caster = castOn(context, players.getFirst(), element);
            Mob cow = cow(context, caster.player);
            EvokerTestPlayer.later(context, PLATEAU_TICKS, players, () -> {
                cow.hurtServer(context.getLevel(), caster.player.damageSources().playerAttack(caster.player), STRIKE);
                check.accept(cow);
                EvokerTestPlayer.finish(context, players);
            });
        });
    }

    private static void assertExtras(GameTestHelper context, Mob cow, int fireTicks, int frozenTicks, double speed) {
        // 着火刻、冻结刻和水平速度（主击原版击退加追加击退）。
        context.assertTrue(Math.max(cow.getRemainingFireTicks(), 0) == fireTicks,
                "着火刻不符：" + cow.getRemainingFireTicks());
        context.assertTrue(cow.getTicksFrozen() == frozenTicks, "冻结刻不符：" + cow.getTicksFrozen());
        assertClose(context, cow.getDeltaMovement().horizontalDistance(), speed, "击退速度不符");
    }

    private static void castSounds(GameTestHelper context, Element element) {
        // 起手到起势的本仓动作声按 EvokerCues 手写的表逐条核对。
        EvokerTestPlayer.readyInfuse(context, 1, players -> {
            EvokerTestPlayer caster = players.getFirst();
            long started = caster.tick();
            castOn(context, caster, element);
            EvokerTestPlayer.later(context, CUES_TICKS, players, () -> {
                EvokerCues.assertTimeline(context, caster, element.technique(), started);
                EvokerTestPlayer.finish(context, players);
            });
        });
    }

    private static VanillaDamageFabric.DamageEvent assertInfusion(GameTestHelper context, LivingEntity target,
                                                                  Element element, double damage) {
        // 主击之后恰好一记附伤：本行伤害类型与致伤机制、同一部位、记在主击的攻击者名下、剂量按期望。
        List<VanillaDamageFabric.DamageEvent> events = events(target);
        context.assertTrue(events.size() == 2, "主击之后应该恰好一记附伤：" + events.size());
        var main = events.getFirst();
        var infusion = events.get(1);
        context.assertTrue(infusion.source().is(damageKey(element.damageType())), "附伤类型不是" + element);
        context.assertTrue(KINDS.get(infusion.input().kind()) == element, "附伤种类不符：" + infusion.input().kind());
        context.assertTrue(infusion.input().hit().equals(main.input().hit()), "附伤没有落在主击同一部位");
        context.assertTrue(infusion.attacker() == main.attacker(), "附伤没有记在主击的攻击者名下");
        assertClose(context, infusion.input().rawDamage(), damage, "附伤剂量不符");
        return infusion;
    }

    private static void assertKnocksInfused(GameTestHelper context, LivingEntity target, LivingEntity caster) {
        // 叩门的每记主击都是破门斩伤害类型，后面紧跟一记施放者名下的切割附伤，落在同一部位。
        // 打中哪个部位有随机：叩击把那个部位打到 0 HP 时，这一记附伤照 SPEC 5.5 丢弃。
        List<VanillaDamageFabric.DamageEvent> events = events(target);
        int infused = 0;
        for (int index = 0; index < events.size(); index++) {
            var event = events.get(index);
            if (!isKnock(event)) {
                continue;
            }
            boolean last = index + 1 == events.size();
            if (partHp(event) <= 0) {
                context.assertTrue(last || isKnock(events.get(index + 1)), "打断部位的那记叩击后面仍有附伤");
                continue;
            }
            context.assertTrue(!last, "最后一记叩击后面没有附伤");
            var infusion = events.get(index + 1);
            context.assertTrue(infusion.source().is(damageKey(METAL.damageType()))
                    && infusion.attacker() == caster && infusion.input().hit().equals(event.input().hit()),
                    "叩击后面的附伤不符：" + infusion.source() + " " + infusion.input());
            infused++;
        }
        context.assertTrue(infused > 0, "叩门没有一记带上附伤：" + events.size());
    }

    private static boolean isKnock(VanillaDamageFabric.DamageEvent event) {
        // 破门斩自己的伤害类型都在它的命名空间下。
        return event.source().typeHolder().unwrapKey().orElseThrow().identifier().getNamespace().equals(DOOR_BREAKER);
    }

    private static double partHp(VanillaDamageFabric.DamageEvent event) {
        // 主击提交以后，打中的那个部位还剩多少 HP。
        String partId = event.input().hit().orElseThrow().getPartId();
        return BodyProtobuf.fromMessage(event.result().body()).partStates().stream()
                .filter(part -> part.id().value().equals(partId))
                .mapToDouble(part -> part.hp())
                .sum();
    }

    private static void assertHitSound(GameTestHelper context, EvokerTestPlayer listener, LivingEntity target,
                                       double volume, double pitch) {
        // 施放者听到目标脚下恰好一次本行命中声：玩家主人归玩家类别，音量音高按相克调整。
        String hit = FIRE.hitSound();
        var heard = listener.sounds().stream()
                .filter(sound -> sound.id().toString().equals(hit))
                .map(EvokerTestPlayer.HeardSound::packet)
                .filter(packet -> near(packet.getX(), target.getX()) && near(packet.getY(), target.getY())
                        && near(packet.getZ(), target.getZ()))
                .toList();
        context.assertTrue(heard.size() == 1, "目标 " + target.position() + " 处应该恰好一次命中声：" + heard.size());
        var packet = heard.getFirst();
        context.assertTrue(packet.getSource() == SoundSource.PLAYERS, "玩家主人的命中声类别不对：" + packet.getSource());
        assertClose(context, packet.getVolume(), volume, "命中声音量不符");
        assertClose(context, packet.getPitch(), pitch, "命中声音高不符");
    }

    private static boolean near(double heard, double actual) {
        // 声音包的坐标读出来是 float：整套 GameTest 把用例摆在离原点上千万格处，那里 float 只精确到 1 格，容差要加上它。
        return Math.abs(heard - actual) < POSITION_TOLERANCE + Math.ulp((float) actual);
    }

    private static CastOutcome knock(EvokerTestPlayer caster, LivingEntity target) {
        // 叩门要指定目标，精确引用取生产注册表里的最新版本。
        var definition = TechniqueFabric.service().registry().findLatest(KNOCK).orElseThrow();
        var reference = TechniqueReference.newBuilder()
                .setId(definition.getId())
                .setVersion(definition.getVersion())
                .build();
        var request = CastRequest.newBuilder()
                .setBodyId(caster.player.getStringUUID())
                .setCastId(UUID.randomUUID().toString())
                .setTechnique(reference)
                .setTargetId(target.getStringUUID())
                .build();
        return TechniqueFabric.service().cast(caster.player.getUUID(), request);
    }

    private static Mob cow(GameTestHelper context, LivingEntity caster) {
        // 施放者南边三格一头牛：没身体，关掉 AI 和重力，速度清零，打完的速度只来自这一击。
        BlockPos position = BlockPos.containing(caster.position().add(0, 0, COW_DISTANCE));
        Mob cow = context.spawn(EntityTypes.COW, context.relativePos(position));
        cow.setNoAi(true /* aiDisabled */);
        cow.setNoGravity(true /* noGravity */);
        cow.setDeltaMovement(Vec3.ZERO);
        return cow;
    }

    private static void fang(GameTestHelper context, LivingEntity owner, LivingEntity target) {
        // 玩家拥有的尖牙放在目标脚下当直接实体，伤害源照原版有主尖牙写 indirectMagic。
        var fangs = new EvokerFangs(target.level(), target.getX(), target.getY(), target.getZ(), 0, 0, owner);
        target.hurtServer(context.getLevel(), owner.damageSources().indirectMagic(fangs, owner), FANG_DAMAGE);
    }

    private static long remainingMillis(EvokerTestPlayer fixture) {
        // 功法库公开的附行剩余查询，没有附行时为零。
        return TechniqueFabric.service().infusionRemainingMillis(fixture.player.getUUID());
    }

    private static List<VanillaDamageFabric.DamageEvent> events(LivingEntity target) {
        // 观察记录跨用例累积，只取打在这个目标身上的。
        return EVENTS.stream().filter(event -> event.target() == target).toList();
    }

    private static ResourceKey<DamageType> damageKey(String id) {
        // 伤害类型按完整 ID 查注册表。
        return ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.parse(id));
    }

    private static void assertRejected(GameTestHelper context, CastOutcome outcome, TechniqueFailure failure,
                                       String reason) {
        // 拒绝码与说明都要对上，且没有绑定。
        context.assertTrue(outcome.getFailure() == failure && outcome.getReason().equals(reason)
                && !outcome.hasBinding(), "拒绝不符：期望 " + failure + " " + reason + " 实际 " + outcome);
    }

    private static void assertClose(GameTestHelper context, double actual, double expected, String message) {
        // 浮点比较，失败信息带上两边的值。
        context.assertTrue(Math.abs(actual - expected) < TOLERANCE, message + "：期望 " + expected + " 实际 " + actual);
    }
}
