package dev.kizuna.meridian.evoker.clienttest;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import dev.kizuna.meridian.evoker.clienttest.mixin.ContainerScreenAccess;
import java.util.function.Function;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.phys.Vec2;
import org.lwjgl.glfw.GLFW;

/**
 * 照 Wiki「五脏诀上手」在一个新世界里走一遍，每步截图：卷轴提示、右键学卷、经脉未通、真元不够、肺炼、收势后两肺没有余力、
 * 歇过放金臂、背包效果栏的金臂说明、游炁放心火被拒、改境界到真炁并补气态真元、心炼、心火说明。
 * 命令都走服务端，和玩家在聊天栏输入的相同；进架势和放招按真实按键（R 与数字键）。截图写到运行目录 {@code screenshots/}。
 */
public final class EvokerWalkthroughClientTest implements FabricClientGameTest {
    private static final int WINDOW_WIDTH = 1280;
    private static final int WINDOW_HEIGHT = 960;
    private static final int GUI_SCALE = 3;
    private static final int SETTLE_TICKS = 10;
    private static final int MESSAGE_TICKS = 6;
    private static final int REFINE_TICKS = 32 * 20;
    private static final int REFINE_HALF_TICKS = REFINE_TICKS / 2;
    private static final int TOO_SOON_TICKS = 20;
    private static final int REST_TICKS = 9 * 20;
    private static final int STATUS_TICKS = 80;
    private static final int SLOT_CENTER = 8;
    private static final int EFFECT_GAP = 2;
    private static final int EFFECT_HOVER = 16;
    private static final String SCROLL = "meridian_evoker:nourish_scroll";
    private static final String BOOK = "meridian_evoker:";

    @Override
    public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(WINDOW_WIDTH, WINDOW_HEIGHT);
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            command(world, "time set noon");
            command(world, "weather clear");
            command(world, "gamemode survival @a");
            command(world, "difficulty peaceful");
            context.runOnClient(client -> {
                client.options.guiScale().set(GUI_SCALE);
                client.resizeGui();
            });
            settle(context);
            learn(context, world);
            metalArms(context, world);
            fireLine(context, world);
        }
    }

    private static void learn(ClientGameTestContext context, TestSingleplayerContext world) {
        // 卷轴拿在第一格：背包里指着它看提示；关掉背包后右键学会，阅读页自动打开。
        command(world, "item replace entity @a hotbar.0 with " + SCROLL);
        context.getInput().pressKey(options -> options.keyHotbarSlots[0]);
        settle(context);
        context.getInput().pressKey(options -> options.keyInventory);
        context.waitForScreen(InventoryScreen.class);
        hoverSlotHolding(context, SCROLL);
        settle(context);
        shoot(context, "01_scroll_tooltip");
        context.setScreen(() -> null);
        settle(context);
        context.getInput().pressKey(options -> options.keyUse);
        settle(context);
        shoot(context, "02_learned_reading_screen");
        context.setScreen(() -> null);
        settle(context);
    }

    private static void metalArms(ClientGameTestContext context, TestSingleplayerContext world) {
        // 设槽、进架势后先被拒两次（经脉未通、金行真元不够），再开脉补元、肺炼、金臂，最后在背包效果栏看金臂说明。
        command(world, "execute as @a run meridian combat slot set 1 " + BOOK + "refine_lung");
        command(world, "execute as @a run meridian combat slot set 2 " + BOOK + "metal_arms");
        command(world, "execute as @a run meridian combat slot set 3 " + BOOK + "fire_heart");
        command(world, "execute as @a run meridian combat slot set 4 " + BOOK + "refine_heart");
        settle(context);
        context.getInput().pressKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_R));
        settle(context);
        cast(context, 1);
        shoot(context, "03_reject_meridians_not_opened");
        command(world, "meridian opening complete @a all");
        settle(context);
        cast(context, 1);
        shoot(context, "04_reject_no_metal_qi");
        command(world, "meridian qi fill @a");
        settle(context);
        refine(context, 0, "05_refine_lung_halfway");
        cast(context, 1);
        shoot(context, "06_reject_lungs_tired");
        context.waitTicks(REST_TICKS - TOO_SOON_TICKS - MESSAGE_TICKS);
        cast(context, 1);
        context.waitTicks(STATUS_TICKS);
        shoot(context, "07_metal_arms_cast");
        effectTooltip(context, "08_metal_arms_effect_tooltip");
    }

    private static void fireLine(ClientGameTestContext context, TestSingleplayerContext world) {
        // 游炁放心火被拒；改境界到真炁，心只炼气态真元，所以用 qi set 把内腔覆盖成气态，再心炼、歇过放心火，看心火说明。
        cast(context, 2);
        shoot(context, "09_reject_realm_fire_heart");
        command(world, "meridian body realm @a true_qi");
        command(world, "meridian qi set @a 3000 all lumen 0.1");
        settle(context);
        shoot(context, "10_realm_true_qi_gas_qi");
        refine(context, 3, "11_refine_heart_halfway");
        context.waitTicks(REST_TICKS - TOO_SOON_TICKS);
        cast(context, 2);
        context.waitTicks(STATUS_TICKS);
        shoot(context, "12_fire_heart_cast");
        effectTooltip(context, "13_fire_heart_effect_tooltip");
    }

    private static void refine(ClientGameTestContext context, int slot, String halfway) {
        // 炼化 32 s：半程拍一张，等到收势后 1 s 返回，正好赶上本脏余力还没回来。
        cast(context, slot);
        context.waitTicks(REFINE_HALF_TICKS - MESSAGE_TICKS);
        shoot(context, halfway);
        context.waitTicks(REFINE_TICKS - REFINE_HALF_TICKS + TOO_SOON_TICKS);
    }

    private static void cast(ClientGameTestContext context, int slot) {
        // 架势里按数字键放对应槽位的式；等几 tick 让动作栏提示到客户端。
        context.getInput().pressKey(options -> options.keyHotbarSlots[slot]);
        context.waitTicks(MESSAGE_TICKS);
    }

    private static void effectTooltip(ClientGameTestContext context, String name) {
        // 打开背包，光标指向右侧效果栏第一条（只有这一条功法状态）。效果栏贴着界面右边、与界面顶端对齐。
        context.getInput().pressKey(options -> options.keyInventory);
        context.waitForScreen(InventoryScreen.class);
        hover(context, screen -> new Vec2(screen.evokerLeft() + screen.evokerImageWidth() + EFFECT_GAP + EFFECT_HOVER,
                screen.evokerTop() + EFFECT_HOVER));
        settle(context);
        shoot(context, name);
        context.setScreen(() -> null);
        settle(context);
    }

    private static void hoverSlotHolding(ClientGameTestContext context, String itemId) {
        // 光标移到界面里装着这件物品的格子中间；格子坐标相对界面左上角。
        hover(context, screen -> {
            Slot slot = ((AbstractContainerScreen<?>) screen).getMenu().slots.stream()
                    .filter(candidate -> BuiltInRegistries.ITEM.getKey(candidate.getItem().getItem()).toString().equals(itemId))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("背包里没有 " + itemId));
            return new Vec2(screen.evokerLeft() + slot.x + SLOT_CENTER, screen.evokerTop() + slot.y + SLOT_CENTER);
        });
    }

    private static void hover(ClientGameTestContext context, Function<ContainerScreenAccess, Vec2> guiPoint) {
        // 背包布局由界面框架决定，位置从当前界面读；界面坐标乘缩放换算成窗口像素。
        Vec2 cursor = context.computeOnClient(client -> {
            Window window = client.getWindow();
            float scale = (float) window.getScreenWidth() / window.getGuiScaledWidth();
            Vec2 point = guiPoint.apply((ContainerScreenAccess) client.gui.screen());
            return new Vec2(point.x * scale, point.y * scale);
        });
        context.getInput().setCursorPos(cursor.x, cursor.y);
    }

    private static void command(TestSingleplayerContext world, String command) {
        world.getServer().runCommand(command);
    }

    private static void settle(ClientGameTestContext context) {
        context.waitTicks(SETTLE_TICKS);
    }

    private static void shoot(ClientGameTestContext context, String name) {
        // 命令回显留在聊天栏里，截图照拍；只清掉右上角的提示条。
        context.runOnClient(client -> client.gui.toastManager().clear());
        context.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix());
    }
}
