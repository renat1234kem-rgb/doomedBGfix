package ru.renat.doomedbgfix.mixin;

import net.mattlives.doomedmatu.body.BodyPart;
import net.mattlives.doomedmatu.body.CoopMedical;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.renat.doomedbgfix.bandage.BandageRework;

/**
 * Единственная точка, куда приходит прогресс намотки бинта.
 * Ваниль применяет кровь только при завершении; мы добавляем снятие крови за каждый шаг.
 * ВАЖНО: метод возвращает BandageProgressResult, поэтому обработчик обязан принимать
 * CallbackInfoReturnable, а не CallbackInfo (иначе Mixin падает на старте игры).
 */
@Mixin(value = CoopMedical.class, remap = false)
public abstract class CoopMedicalBandageMixin {

    @Inject(method = "handleBandageProgress", at = @At("HEAD"), remap = false)
    private static void bg$progressiveBandage(ServerPlayer healer, ServerPlayer patient, BodyPart part,
                                              float targetProgress, boolean finished, long seed, int itemSlot,
                                              CallbackInfoReturnable<CoopMedical.BandageProgressResult> cir) {
        BandageRework.onBandageProgress(healer, patient, part, targetProgress, finished, itemSlot);
    }
}