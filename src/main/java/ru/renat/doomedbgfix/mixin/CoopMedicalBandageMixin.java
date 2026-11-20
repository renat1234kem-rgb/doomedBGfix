package ru.renat.doomedbgfix.mixin;

import net.mattlives.doomedmatu.body.BodyPart;
import net.mattlives.doomedmatu.body.CoopMedical;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.renat.doomedbgfix.bandage.BandageRework;

/**
 * The single entry point where wrap progress arrives from the bandage minigame
 * (CoopMedical.handleBandageProgress). Vanilla only reduces bleeding when the
 * wrap completes; we add a fixed bleed stop per wrap step on top.
 */
@Mixin(value = CoopMedical.class, remap = false)
public abstract class CoopMedicalBandageMixin {

    @Inject(method = "handleBandageProgress", at = @At("HEAD"), remap = false)
    private static void bg$progressiveBandage(ServerPlayer healer, ServerPlayer patient, BodyPart part,
                                              float targetProgress, boolean finished, long seed, int itemSlot,
                                              CallbackInfo ci) {
        BandageRework.onBandageProgress(healer, patient, part, targetProgress, finished, itemSlot);
    }
}