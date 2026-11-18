package dev.kizuna.meridian.evoker.clienttest.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 走查按界面自己的位置找物品格，背包布局由界面框架决定。 */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccess {
    @Accessor("leftPos")
    int evokerLeft();

    @Accessor("topPos")
    int evokerTop();

}
