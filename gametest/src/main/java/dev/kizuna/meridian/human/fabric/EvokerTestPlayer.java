package dev.kizuna.meridian.human.fabric;

import com.mojang.authlib.GameProfile;
import dev.kizuna.meridian.core.AnatomyState;
import dev.kizuna.meridian.core.BodySnapshot;
import dev.kizuna.meridian.core.Qi;
import dev.kizuna.meridian.core.QiInventory;
import dev.kizuna.meridian.protocol.BodyProtobuf;
import dev.kizuna.meridian.protocol.HealthHostProtobuf;
import dev.kizuna.meridian.protocol.pb.ActivityMessages.ActivityEffort;
import dev.kizuna.meridian.protocol.pb.ActivityMessages.BodyActivity;
import dev.kizuna.meridian.protocol.pb.ActivityMessages.QiAccountChange;
import dev.kizuna.meridian.protocol.pb.BodyMessages.HealthTarget;
import dev.kizuna.meridian.protocol.pb.BodyMessages.Realm;
import dev.kizuna.meridian.protocol.pb.HealthHostMessages.HealthHostRequest;
import dev.kizuna.meridian.protocol.pb.HealthHostMessages.HealthHostResponse;
import dev.kizuna.meridian.protocol.pb.HealthMessages.DamageBody;
import dev.kizuna.meridian.protocol.pb.HealthMessages.DamageKind;
import dev.kizuna.meridian.protocol.pb.MeridianMessages.BodyCommand;
import dev.kizuna.meridian.protocol.pb.MeridianMessages.CommandReceipt;
import dev.kizuna.meridian.protocol.pb.MeridianMessages.DebugQi;
import dev.kizuna.meridian.protocol.pb.MeridianMessages.DebugQiMode;
import dev.kizuna.meridian.protocol.pb.MeridianMessages.FailureCode;
import dev.kizuna.meridian.protocol.pb.MeridianMessages.RequestEnvelope;
import dev.kizuna.meridian.technique.fabric.TechniqueFabric;
import dev.kizuna.meridian.technique.fabric.testing.RecordingConnection;
import dev.kizuna.meridian.technique.fabric.testing.TestPlayers;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.CastOutcome;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.CastRequest;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueHostRequest;
import dev.kizuna.meridian.technique.protocol.pb.TechniqueMessages.TechniqueReference;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** 经过真实登录的五脏诀测试玩家：内存连接记下发的声音包，身体准备只走 Human 的管理与活动入口。 */
final class EvokerTestPlayer implements AutoCloseable {
    static final String PREFIX = "meridian_evoker:";
    static final String VOLUME = PREFIX + "nourish";
    static final double FREE_DENSITY = 0.01;
    static final double GAS_DENSITY = 0.1;
    static final double LIQUID_DENSITY = 1;
    private static final int NAME_LENGTH = 8;
    private static final int HEIGHT = 2;
    private static final int LANE_WIDTH = 2;
    private static final int LOGIN_TICKS = 3;
    private static final int PROTOCOL_VERSION = 1;
    private static final String DAMAGE_SOURCE = "meridian_evoker_test:organ";
    private static final List<String> SIDES = List.of("left", "right");
    private static final List<Identifier> CHANNELS = List.of(
            Identifier.parse("meridian:body_view"),
            Identifier.parse("meridian:gathering_response"),
            Identifier.parse("meridian:opening_response"),
            Identifier.parse("meridian_human:control"),
            Identifier.parse("meridian_progression:event"),
            Identifier.parse("kizuna_players_animation:play"),
            Identifier.parse("kizuna_players_animation:stop"));
    private static final Map<UUID, EvokerTestPlayer> ONLINE = new LinkedHashMap<>();
    private static boolean ticking;

    final ServerPlayer player;
    private final RecordingConnection connection;
    private final List<HeardSound> sounds = new ArrayList<>();
    private final List<Runnable> observers = new ArrayList<>();

    /** 收到声音包时的世界 tick 与原始包。 */
    record HeardSound(long tick, ClientboundSoundPacket packet) {
        Identifier id() {
            // 本仓事件不进服务端注册表，包里内联的 ID 就是目录写的事件。
            return packet.getSound().value().location();
        }
    }

    private EvokerTestPlayer(GameTestHelper context, int lane) {
        // 只替换传输层；玩家列表、Human 登录与 Fabric 网络事件都用正式实现。
        UUID id = UUID.randomUUID();
        var level = context.getLevel();
        var profile = new GameProfile(id, "evoker_" + id.toString().substring(0, NAME_LENGTH));
        player = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
        connection = new RecordingConnection(CHANNELS);
        ONLINE.put(id, this);
        TestPlayers.logIn(player, connection);
        BlockPos position = context.absolutePos(new BlockPos(1 + lane * LANE_WIDTH, HEIGHT, 1));
        player.setPos(position.getX(), position.getY(), position.getZ());
        player.setNoGravity(true /* noGravity */);
        player.setGameMode(GameType.SURVIVAL);
    }

    static void ready(GameTestHelper context, Consumer<EvokerTestPlayer> action) {
        // 单人用例：登录几 tick 后开通全身经脉、清空真元并学会上卷。
        ready(context, 1, players -> action.accept(players.getFirst()));
    }

    static void ready(GameTestHelper context, int count, Consumer<List<EvokerTestPlayer>> action) {
        // 多人用例按列排开，声音包可以按位置区分施放者；用例结束一律真实断线。
        initializeTicks();
        var players = new ArrayList<EvokerTestPlayer>();
        for (int lane = 0; lane < count; lane++) {
            var fixture = new EvokerTestPlayer(context, lane);
            players.add(fixture);
        }
        context.runAfterDelay(LOGIN_TICKS, () -> guarded(players, () -> {
            for (EvokerTestPlayer fixture : players) {
                fixture.openAllMeridians();
                fixture.fillEvenly(0, FREE_DENSITY);
                TechniqueFabric.service().learn(fixture.player.getUUID(), VOLUME);
            }
            action.accept(players);
        }));
    }

    static void later(GameTestHelper context, int ticks, List<EvokerTestPlayer> players, Runnable assertion) {
        // 延迟断言失败时先断开玩家，防止残留施放污染并行用例。
        context.runAfterDelay(ticks, () -> guarded(players, assertion));
    }

    static void finish(GameTestHelper context, List<EvokerTestPlayer> players) {
        // 退出正式连接使尚在收势或炼化中的施放清理，再完成本用例。
        players.forEach(EvokerTestPlayer::close);
        context.succeed();
    }

    private static void guarded(List<EvokerTestPlayer> players, Runnable assertion) {
        // 失败照常抛给 GameTest，断线只是清理。
        try {
            assertion.run();
        } catch (RuntimeException | Error failure) {
            players.forEach(EvokerTestPlayer::close);
            throw failure;
        }
    }

    private static void initializeTicks() {
        // 内存连接不在服务器的套接字列表里：补投连接 tick 才会推进玩家药效，并按心跳回包，长施放期间不掉线。
        if (ticking) {
            return;
        }
        ServerTickEvents.START_SERVER_TICK.register(server -> {
            for (EvokerTestPlayer fixture : List.copyOf(ONLINE.values())) {
                if (fixture.connection.isConnected()) {
                    fixture.connection.tick();
                    fixture.drainPackets();
                    List.copyOf(fixture.observers).forEach(Runnable::run);
                }
            }
        });
        ticking = true;
    }

    private void drainPackets() {
        // 声音包按收到时的世界 tick 记录；心跳立即应答。
        var listener = (ServerGamePacketListenerImpl) connection.getPacketListener();
        for (var packet : List.copyOf(connection.packets())) {
            if (packet instanceof ClientboundSoundPacket sound) {
                sounds.add(new HeardSound(player.level().getGameTime(), sound));
            }
            if (packet instanceof ClientboundKeepAlivePacket keepAlive) {
                listener.handleKeepAlive(new ServerboundKeepAlivePacket(keepAlive.getId()));
            }
        }
        connection.packets().clear();
    }

    void everyTick(Runnable observer) {
        // 用例的逐 tick 观察挂在连接 tick 上：GameTest 的逐 tick 表不能在回调里添项；断线后不再调用。
        observers.add(observer);
    }

    long tick() {
        // 用例里的时刻都以主世界 tick 计，与身体时钟同源。
        return player.level().getServer().overworld().getGameTime();
    }

    HumanPlayerBody body() {
        // Human 世界状态里的唯一身体，不另建测试宿主。
        return HumanTechniqueHost.state().find(player.getUUID());
    }

    BodySnapshot settled() {
        // 读取前先把身体结算到当前主世界时刻，曲线与加工都按同一时钟。
        HumanPlayerBody body = body();
        body.settle(HumanTechniqueHost.nowMillis());
        return body.snapshot();
    }

    List<HeardSound> sounds() {
        // 只读副本，断言不改记录。
        return List.copyOf(sounds);
    }

    CastOutcome cast(String technique) {
        // 精确引用取生产注册表，测试不覆盖目录里的费用、槽位和时刻。
        var definition = TechniqueFabric.service().registry().findLatest(PREFIX + technique).orElseThrow();
        var reference = TechniqueReference.newBuilder()
                .setId(definition.getId())
                .setVersion(definition.getVersion())
                .build();
        var request = CastRequest.newBuilder()
                .setBodyId(player.getStringUUID())
                .setCastId(UUID.randomUUID().toString())
                .setTechnique(reference)
                .build();
        return TechniqueFabric.service().cast(player.getUUID(), request);
    }

    void enterRealm(Realm target) {
        // 境界只能逐级上升，每级都经 Human 功法宿主提交一次真实身体活动。
        Realm current = Realm.valueOf(body().snapshot().realm().name());
        for (int number = current.getNumber() + 1; number <= target.getNumber(); number++) {
            var activity = BodyActivity.newBuilder()
                    .setRealm(Realm.forNumber(number))
                    .build();
            submit(activity);
        }
    }

    void fillEvenly(double occupancy, double density) {
        // 全身经脉按可用体积同比例写入无属性真元，各脉占比相同，被动流动不会先把某一脉抽空。
        double amount = body().snapshot().structure().lumenVolume() * occupancy * density;
        var request = DebugQi.newBuilder()
                .setMode(DebugQiMode.SET_INVENTORY)
                .setAmount(amount)
                .setDensity(density)
                .build();
        debug(request, "全身");
    }

    void setQi(List<String> meridianIds, Qi.QiAttribute attribute, double amount, double density) {
        // 管理员入口只能写无属性真元：先逐脉精确设数量与密度，有属性时再原样换成该属性，总量不变。
        for (String meridianId : meridianIds) {
            var request = DebugQi.newBuilder()
                    .setMode(DebugQiMode.SET_INVENTORY)
                    .setMeridianId(meridianId)
                    .setAmount(amount)
                    .setDensity(density)
                    .build();
            debug(request, meridianId);
        }
        if (!attribute.equals(Qi.QiAttribute.NONE)) {
            attune(meridianIds, attribute);
        }
    }

    void fillLumens() {
        // 全身内腔注满无属性游离真元，供中焦储备达到入库条件。
        var request = DebugQi.newBuilder()
                .setMode(DebugQiMode.FILL_LUMENS)
                .build();
        debug(request, "全身内腔");
    }

    void exertOrgan(String organId, double watts, double seconds) {
        // 器官做功走 Framework 的耐力算子，疲劳与施放校验读同一份账。
        var effort = ActivityEffort.newBuilder()
                .setOrganId(organId)
                .setWatts(watts)
                .setSeconds(seconds)
                .build();
        var activity = BodyActivity.newBuilder()
                .addEfforts(effort)
                .build();
        submit(activity);
    }

    void destroyOrgan(String organId) {
        // 可信健康协议精确打到一个器官；CUSTOM 不另分配组织伤害，器官归零不致死。
        double maximumHp = settled().structure().organs().stream()
                .filter(organ -> organ.id().value().equals(organId))
                .findFirst()
                .orElseThrow()
                .maxHp();
        var inspected = exchange(health(UUID.randomUUID().toString()).setInspect(true /* inspect */).build());
        var hit = HealthTarget.newBuilder()
                .setOrganId(organId)
                .build();
        var damage = DamageBody.newBuilder()
                .setSourceId(DAMAGE_SOURCE)
                .setDamageType(DAMAGE_SOURCE)
                .setKind(DamageKind.CUSTOM)
                .setHit(hit)
                .setDamageHp(maximumHp)
                .build();
        var command = BodyCommand.newBuilder()
                .setBodyId(inspected.getBody().getId())
                .setExpectedRevision(inspected.getBody().getRevision())
                .setDamageBody(damage)
                .build();
        String requestId = UUID.randomUUID().toString();
        var effect = RequestEnvelope.newBuilder()
                .setProtocolVersion(PROTOCOL_VERSION)
                .setRequestId(requestId)
                .setBodyCommand(command)
                .build();
        var response = exchange(health(requestId).setEffect(effect).build());
        if (!response.hasBody()) {
            throw new IllegalStateException("测试打坏器官失败：" + organId + " " + response);
        }
    }

    Set<String> receiptKeys() {
        // 回执以调用方与请求 ID 为键永久保存，记下键集合即可找出之后新增的回执。
        return body().receipts().stream()
                .map(EvokerTestPlayer::key)
                .collect(Collectors.toSet());
    }

    List<CommandReceipt> receiptsSince(Set<String> before) {
        // 只取给定键集合之后新提交的回执。
        return body().receipts().stream()
                .filter(receipt -> !before.contains(key(receipt)))
                .toList();
    }

    static double released(List<CommandReceipt> receipts) {
        // 加工损耗、开脉、活动与健康释放都随回执原子入环境账，逐项求和就是身体流出的总量。
        double total = 0;
        for (CommandReceipt receipt : receipts) {
            var inventories = Stream.of(
                    receipt.getTimeAdvance().getReleasedQi(),
                    receipt.getOpening().getReleasedQi(),
                    receipt.getCommand().getExecuteActivity().getReleasedQi(),
                    receipt.getHealth().getReleasedQi());
            total += inventories.mapToDouble(inventory -> BodyProtobuf.fromInventory(inventory).totalQi()).sum();
        }
        return total;
    }

    static double attributeQi(BodySnapshot body, Qi.QiAttribute attribute) {
        // 内腔、外壁、器官缓存、加工投料与在途货物都算身体所有，按属性合计。
        return inventories(body)
                .flatMap(inventory -> inventory.entries().stream())
                .filter(qi -> qi.attribute().equals(attribute))
                .mapToDouble(Qi::amount)
                .sum();
    }

    static double meridianQi(BodySnapshot body, List<String> meridianIds, Qi.QiAttribute attribute) {
        // 指定经脉内腔与外壁里某一属性的合计。
        return body.meridianStates().stream()
                .filter(meridian -> meridianIds.contains(meridian.id().value()))
                .flatMap(meridian -> Stream.of(meridian.lumenQi(), meridian.wallQi()))
                .flatMap(inventory -> inventory.entries().stream())
                .filter(qi -> qi.attribute().equals(attribute))
                .mapToDouble(Qi::amount)
                .sum();
    }

    static AnatomyState.Organ organ(BodySnapshot body, String organId) {
        // 器官状态与构型一一对应，缺失是装配错误。
        return body.organStates().stream()
                .filter(organ -> organ.id().value().equals(organId))
                .findFirst()
                .orElseThrow();
    }

    static List<String> meridians(List<String> kinds) {
        // 十二正经左右各一条，ID 为 <侧>_<经>_meridian。
        return SIDES.stream()
                .flatMap(side -> kinds.stream().map(kind -> side + "_" + kind + "_meridian"))
                .toList();
    }

    private static Stream<QiInventory> inventories(BodySnapshot body) {
        // 身体的全部真元账户，不含已经释放到环境的部分。
        var meridians = body.meridianStates().stream()
                .flatMap(meridian -> Stream.of(meridian.lumenQi(), meridian.wallQi()));
        var buffers = body.organStates().stream().map(AnatomyState.Organ::buffer);
        var processes = body.organStates().stream()
                .filter(organ -> organ.process() != null)
                .map(organ -> organ.process().input());
        var transits = body.transits().stream().map(transit -> transit.cargo());
        return Stream.of(meridians, buffers, processes, transits).flatMap(stream -> stream);
    }

    private static String key(CommandReceipt receipt) {
        // 与 Framework 回执表相同的唯一键。
        return receipt.getCallerId() + "/" + receipt.getRequestId();
    }

    private void openAllMeridians() {
        // 与管理命令同一开脉入口，扩容仍由 Framework 完成算子校验。
        FailureCode result = body().completeOpening(HumanTechniqueHost.nowMillis(), "all");
        if (result != FailureCode.FAILURE_CODE_UNSPECIFIED) {
            throw new IllegalStateException("测试开脉失败：" + result);
        }
    }

    private void debug(DebugQi request, String target) {
        // 管理员调试入口按当前时刻结算后提交，失败直接报出目标。
        FailureCode result = body().debugQi(HumanTechniqueHost.nowMillis(), request);
        if (result != FailureCode.FAILURE_CODE_UNSPECIFIED) {
            throw new IllegalStateException("测试设真元失败：" + target + " " + result);
        }
    }

    private void attune(List<String> meridianIds, Qi.QiAttribute attribute) {
        // 每条内腔按原数量、原密度换属性；Framework 只核对总量守恒，属性变化等同于已经炼好。
        BodySnapshot body = settled();
        var activity = BodyActivity.newBuilder();
        for (var meridian : body.meridianStates()) {
            if (!meridianIds.contains(meridian.id().value())) {
                continue;
            }
            QiInventory lumen = meridian.lumenQi();
            List<Qi> entries = lumen.entries().stream()
                    .map(qi -> new Qi(attribute, qi.amount(), qi.density()))
                    .toList();
            var change = QiAccountChange.newBuilder()
                    .setLumenId(meridian.id().value())
                    .setExpected(BodyProtobuf.toInventory(lumen))
                    .setReplacement(BodyProtobuf.toInventory(new QiInventory(entries)))
                    .build();
            activity.addAccounts(change);
        }
        submit(activity.build());
    }

    private void submit(BodyActivity activity) {
        // 按当前版本提交给 Human 功法宿主；失败直接报出 Framework 回执。
        long now = HumanTechniqueHost.nowMillis();
        HumanPlayerBody body = body();
        body.settle(now);
        var request = TechniqueHostRequest.newBuilder()
                .setRequestId(UUID.randomUUID().toString())
                .setExpectedRevision(body.snapshot().revision())
                .setActivity(activity)
                .build();
        var response = body.applyTechnique(now, request);
        if (response.hasFailure()) {
            throw new IllegalStateException("测试身体活动被拒绝：" + response.getFailure());
        }
    }

    private HealthHostRequest.Builder health(String requestId) {
        // 每次健康请求绑定当前在线玩家与独立请求 ID。
        return HealthHostRequest.newBuilder()
                .setProtocolVersion(PROTOCOL_VERSION)
                .setRequestId(requestId)
                .setSubjectId(player.getStringUUID());
    }

    private static HealthHostResponse exchange(HealthHostRequest request) {
        // 正式 Protobuf 编解码和健康宿主共同验证请求契约。
        return HealthHostProtobuf.decodeResponse(new HumanHealthHost().exchange(
                HealthHostProtobuf.encodeRequest(request)));
    }

    @Override
    public void close() {
        // 真实断线同时清除施放、身体认领和服务端玩家列表；重复调用不再处理。
        if (ONLINE.remove(player.getUUID()) != null) {
            connection.disconnectFromClient();
        }
    }
}
