package com.lazify.mixin;

import com.lazify.LazifyClient;
import net.minecraft.client.Camera;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
abstract class CameraFovMixin {
    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void lazify$removeSlownessFov(float partialTick, CallbackInfoReturnable<Float> callback) {
        if (!LazifyClient.antiDebuffEnabled()) return;
        Camera camera = (Camera) (Object) this;
        if (!(camera.entity() instanceof LivingEntity livingEntity)) return;
        MobEffectInstance slowness = livingEntity.getEffect(MobEffects.SLOWNESS);
        if (slowness == null) return;
        float multiplier = 1.0f - (slowness.getAmplifier() + 1) * 0.075f;
        if (multiplier > 0.0f) callback.setReturnValue(callback.getReturnValue() / multiplier);
    }
}
