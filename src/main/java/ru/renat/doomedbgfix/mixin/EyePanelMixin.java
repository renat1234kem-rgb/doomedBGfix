package ru.renat.doomedbgfix.mixin;

import net.mattlives.doomedmatu.body.BodyPart;
import net.mattlives.doomedmatu.body.LimbData;
import net.mattlives.doomedmatu.capability.DoomedData;
import net.mattlives.doomedmatu.client.render.HealthPanelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.renat.doomedbgfix.DoomedBGFix;

@Mixin(value = HealthPanelRenderer.class, remap = false)
public class EyePanelMixin {

    @Inject(method = "renderEye", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onRenderEye(DoomedData data, BodyPart eyePart, CallbackInfo ci) {
        LimbData limb = data.getLimb(eyePart);
        if (limb == null) return;

        // Проверяем, установлен ли имплант
        if (limb.getOrCreateTag().getBoolean("EyeImplant")) {
            // Рисуем спрайт импланта вместо обычного глаза
            // (здесь нужно вызвать твой рендер импланта)
            ci.cancel();
        }
    }
}