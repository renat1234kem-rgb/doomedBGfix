package com.deceasedcraft.doomedtraits.mixin;

import com.deceasedcraft.doomedtraits.AddTraits;
import net.mattlives.doomedmatu.body.FumeExposureHandler;
import net.mattlives.doomedmatu.capability.DoomedData;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Помечаем время обработки испарений, чтобы Железный желудок ослаблял их сильнее обычной тошноты. */
@Mixin(value = FumeExposureHandler.class, remap = false)
public abstract class FumeExposureHandlerMixin {

    @Inject(method = "tick", at = @At("HEAD"), remap = false)
    private static void doomedtraitsadd$fumesBegin(ServerPlayer sp, DoomedData d, CallbackInfo ci) {
        AddTraits.IN_FUMES.set(Boolean.TRUE);
    }

    @Inject(method = "tick", at = @At("RETURN"), remap = false)
    private static void doomedtraitsadd$fumesEnd(ServerPlayer sp, DoomedData d, CallbackInfo ci) {
        AddTraits.IN_FUMES.set(Boolean.FALSE);
    }
}
