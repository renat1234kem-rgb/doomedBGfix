package ru.renat.doomedbgfix.mixin;

import net.mattlives.doomedmatu.body.BodyPart;
import net.mattlives.doomedmatu.body.CoopMedical;
import net.mattlives.doomedmatu.body.CoopTreatmentType;
import net.mattlives.doomedmatu.body.EyeFunctionService;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.renat.doomedbgfix.server.EyeCutSessions;

/**
 * Doomed считает AMPUTATE допустимым только для конечностей (isAmputatable), глаза туда не входят.
 * Для глаз перехватываем начало самолечения и запускаем свою мини-игру.
 */
@Mixin(value = CoopMedical.class, remap = false)
public abstract class CoopMedicalEyeMixin {

    @Inject(method = "beginSelfTreatment", at = @At("HEAD"), cancellable = true, remap = false)
    private static void bg$eyeAmputation(ServerPlayer player, CoopTreatmentType type, BodyPart part,
                                         boolean forceInteractive, CallbackInfoReturnable<Boolean> cir) {
        if (type != CoopTreatmentType.AMPUTATE || !EyeFunctionService.isEye(part)) return;
        cir.setReturnValue(EyeCutSessions.openSelf(player, part, forceInteractive));
    }
}
