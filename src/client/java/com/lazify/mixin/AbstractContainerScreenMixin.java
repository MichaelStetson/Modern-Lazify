package com.lazify.mixin;

import com.lazify.LazifyClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;
import java.util.Set;

@Mixin(AbstractContainerScreen.class)
abstract class AbstractContainerScreenMixin {
    private static final Set<String> SHOP_TITLES = Set.of(
            "quick buy", "blocks", "melee", "armor", "tools", "ranged", "potions", "utility", "shop"
    );


    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void lazify$middleClickShop(MouseButtonEvent event, boolean doubleClick,
                                        CallbackInfoReturnable<Boolean> callback) {
        int button = event.button();
        if ((button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT)
                || !LazifyClient.middleClickShopEnabled() || !LazifyClient.inBedwars()) return;
        Minecraft client = Minecraft.getInstance();
        if (client.options.keyShift.isDown()) return;
        AbstractContainerScreen<?> screen = (AbstractContainerScreen<?>) (Object) this;
        String title = screen.getTitle().getString().trim().toLowerCase(Locale.ROOT);
        if (SHOP_TITLES.stream().noneMatch(title::contains)) return;
        Slot slot = lazify$getHoveredSlot(event.x(), event.y());
        if (slot == null || !slot.hasItem() || client.player == null || client.gameMode == null) return;
        client.gameMode.handleContainerInput(screen.getMenu().containerId, slot.index,
                GLFW.GLFW_MOUSE_BUTTON_MIDDLE, ContainerInput.PICKUP, client.player);
        callback.setReturnValue(true);
    }

    @Invoker("getHoveredSlot")
    protected abstract Slot lazify$getHoveredSlot(double mouseX, double mouseY);
}
