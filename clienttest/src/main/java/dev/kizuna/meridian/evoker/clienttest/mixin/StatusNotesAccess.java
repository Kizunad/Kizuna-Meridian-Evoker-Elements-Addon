package dev.kizuna.meridian.evoker.clienttest.mixin;

import dev.kizuna.inventoryui.client.status.StatusNotes;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 走查读取图标真实汇总的说明，漏登记不能只凭截图通过。 */
@Mixin(value = StatusNotes.class, remap = false)
public interface StatusNotesAccess {
    @Invoker("of")
    static List<Component> notes(MobEffectInstance effect) {
        // 由 Mixin 接到界面框架方法，未装入时明确失败。
        throw new AssertionError("状态说明的测试入口没有接上");
    }
}
