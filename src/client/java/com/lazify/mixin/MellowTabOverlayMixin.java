package com.lazify.mixin;

import com.lazify.LazifyHud;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerTabOverlay.class)
abstract class MellowTabOverlayMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void lazify$replaceMellowPlayerList(GuiGraphicsExtractor graphics, int scaledWidth,
                                                Scoreboard scoreboard, Objective objective,
                                                CallbackInfo callback) {
        if (LazifyHud.shouldReplaceVanillaPlayerList()) callback.cancel();
    }
}

@Mixin(MouseHandler.class)
abstract class MellowMouseHandlerMixin {
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void lazify$scrollMellowList(long window, double horizontal, double vertical, CallbackInfo callback) {
        if (!LazifyHud.shouldReplaceVanillaPlayerList() || vertical == 0.0) return;
        LazifyHud.scrollMellow(vertical > 0.0 ? -1 : 1);
        callback.cancel();
    }
}
