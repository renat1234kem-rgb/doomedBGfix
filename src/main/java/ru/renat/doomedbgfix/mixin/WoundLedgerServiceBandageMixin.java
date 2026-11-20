package ru.renat.doomedbgfix.mixin;

import net.mattlives.doomedmatu.body.WoundLedgerService;
import net.mattlives.doomedmatu.body.WoundRecord;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.renat.doomedbgfix.config.BGConfig;

/**
 * Ваниль считает одетую рану небинтуемой, из-за чего второй бинт на ещё
 * кровящую повязку мгновенно закрывает мини-игру. Разрешаем повторное
 * бинтование, пока одетая рана кровит выше порога dressedResidualBleedUnits.
 */
@Mixin(value = WoundLedgerService.class, remap = false)
public abstract class WoundLedgerServiceBandageMixin {

    @Inject(method = "isDressable", at = @At("RETURN"), cancellable = true, remap = false)
    private static void bg$allowRebandage(WoundRecord wound, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;
        if (wound == null) return;
        float threshold = BGConfig.dressedResidualBleedUnits();
        if (threshold <= 0f) return;
        if (wound.bleeding() > threshold) {
            cir.setReturnValue(true);
        }
    }
}