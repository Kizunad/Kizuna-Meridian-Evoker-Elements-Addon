package dev.kizuna.meridian.evoker.clienttest.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 走查要把光标放到真实界面的格子和效果栏上：背包布局由界面框架决定，不是原版 176×166，只能读界面自己的位置。 */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccess {
    @Accessor("leftPos")
    int evokerLeft();

    @Accessor("topPos")
    int evokerTop();

    @Accessor("imageWidth")
    int evokerImageWidth();
}
