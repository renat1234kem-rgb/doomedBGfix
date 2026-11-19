package ru.renat.doomedbgfix.mixin;

import net.mattlives.doomedmatu.body.BodyPart;
import net.mattlives.doomedmatu.body.DrugService;
import net.mattlives.doomedmatu.body.DrugType;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.renat.doomedbgfix.bag.BagInfusion;
import ru.renat.doomedbgfix.config.BGConfig;

@Mixin(value = DrugService.class, remap = false)
public abstract class DrugServiceMixin {

    @Inject(method = "inject(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/level/ServerPlayer;"
            + "Lnet/mattlives/doomedmatu/body/BodyPart;Lnet/mattlives/doomedmatu/body/DrugType;F)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void bg$partialBags(ServerPlayer healer, ServerPlayer patient, BodyPart part,
                                       DrugType drug, float ml, CallbackInfo ci) {
        if (!BGConfig.bagPartialUse()) return;
        if (BagInfusion.handles(healer, drug)) {
            BagInfusion.inject(healer, patient, part, drug, ml);
            ci.cancel();
        }
    }
}
