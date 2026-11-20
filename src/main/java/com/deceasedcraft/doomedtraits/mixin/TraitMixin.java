package com.deceasedcraft.doomedtraits.mixin;

import com.deceasedcraft.doomedtraits.AddTraits;
import net.mattlives.doomedmatu.body.Trait;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Биты цены наших черт: известны Doomed и входят в баланс. */
@Mixin(value = Trait.class, remap = false)
public abstract class TraitMixin {

    @Inject(method = "knownMask", at = @At("RETURN"), cancellable = true, remap = false)
    private static void doomedtraitsadd$knownMask(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(cir.getReturnValueI() | AddTraits.OUR_BITS);
    }

    @Inject(method = "balance", at = @At("RETURN"), cancellable = true, remap = false)
    private static void doomedtraitsadd$balance(int mask, CallbackInfoReturnable<Integer> cir) {
        // У Doomed: баланс = бюджет - сумма цен. Цена наших черт закодирована в маске.
        cir.setReturnValue(cir.getReturnValueI() - AddTraits.decodeCost(mask));
    }
}
