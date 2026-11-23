package ru.renat.doomedbgfix.mixin;

import net.mattlives.doomedmatu.body.BodyPart;
import net.mattlives.doomedmatu.body.CoopMedical;
import net.mattlives.doomedmatu.body.CoopTreatmentType;
import net.mattlives.doomedmatu.body.EyeFunctionService;
import net.mattlives.doomedmatu.body.TreatmentKind;
import net.mattlives.doomedmatu.capability.DoomedData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.renat.doomedbgfix.eye.EyeCutModel;
import ru.renat.doomedbgfix.eye.EyeTools;
import ru.renat.doomedbgfix.server.EyeCutSessions;

/**
 * Doomed знает AMPUTATE только для конечностей (isAmputatable), глаз там нет. Подключаем свои мини-игры:
 *  1) самолечение: клик инструментом по глазу в панели здоровья -> удаление глаза;
 *  2) правый клик по другому игроку с выбранным глазом: удаление глаза / установка импланта;
 *  3) продолжение установки после того, как пациент дал согласие.
 * Согласие на удаление Doomed обрабатывает сам и затем вызывает DismemberService.openAuthorizedMinigame
 * (там стоит наша ветка в DismemberServiceMixin).
 */
@Mixin(value = CoopMedical.class, remap = false)
public abstract class CoopMedicalEyeMixin {

    @Inject(method = "beginSelfTreatment", at = @At("HEAD"), cancellable = true, remap = false)
    private static void bg$eyeAmputation(ServerPlayer player, CoopTreatmentType type, BodyPart part,
                                         boolean forceInteractive, CallbackInfoReturnable<Boolean> cir) {
        if (type != CoopTreatmentType.AMPUTATE || !EyeFunctionService.isEye(part)) return;
        cir.setReturnValue(EyeCutSessions.openSelf(player, part, EyeCutModel.MODE_CUT, forceInteractive));
    }

    @Inject(method = "startTreatment", at = @At("HEAD"), cancellable = true, remap = false)
    private static void bg$eyeCoop(ServerPlayer healer, ServerPlayer patient, DoomedData pd, ItemStack held,
                                   BodyPart aimedPart, CallbackInfoReturnable<Boolean> cir) {
        if (EyeCutSessions.startCoop(healer, patient, pd, held, aimedPart)) cir.setReturnValue(true);
    }

    @Inject(method = "openAuthorizedConsentedTreatment", at = @At("HEAD"), cancellable = true, remap = false)
    private static void bg$eyeInstallAfterConsent(ServerPlayer healer, ServerPlayer patient, BodyPart part,
                                                  TreatmentKind kind, CallbackInfoReturnable<Boolean> cir) {
        if (kind != TreatmentKind.INSTALL_PROSTHETIC || !EyeFunctionService.isEye(part)
                || !EyeTools.isImplant(healer.getMainHandItem())) return;
        cir.setReturnValue(EyeCutSessions.openCoop(healer, patient, part, EyeCutModel.MODE_INSTALL, false));
    }
}
