package com.deceasedcraft.doomedtraits.mixin;

import com.deceasedcraft.doomedtraits.AddTrait;
import com.deceasedcraft.doomedtraits.AddTraits;
import net.mattlives.doomedmatu.body.EnergyHandler;
import net.mattlives.doomedmatu.capability.DoomedData;
import net.mattlives.doomedmatu.capability.DoomedDataUtil;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Железный сон: дрёма везде как на шерсти; после сна в кровати бодрость на 5 минут дольше. */
@Mixin(value = EnergyHandler.class, remap = false)
public abstract class EnergyHandlerMixin {

    @Inject(method = "napOnComfort", at = @At("RETURN"), cancellable = true, remap = false)
    private static void doomedtraitsadd$comfortAnywhere(ServerPlayer sp, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()
            && DoomedDataUtil.get(sp).map(d -> AddTraits.active(d, AddTrait.IRON_SLEEP)).orElse(false)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/mattlives/doomedmatu/capability/DoomedData;refreshCaffeinated(F)V",
            shift = At.Shift.AFTER,
            remap = false
        ),
        remap = false
    )
    private static void doomedtraitsadd$longerVigor(ServerPlayer sp, DoomedData d, CallbackInfo ci) {
        if (AddTraits.active(d, AddTrait.IRON_SLEEP)) {
            d.setCaffeinated(d.getCaffeinated() + 300.0F);
        }
    }
}
