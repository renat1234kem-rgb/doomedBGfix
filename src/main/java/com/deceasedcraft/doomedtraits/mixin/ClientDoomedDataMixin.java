package com.deceasedcraft.doomedtraits.mixin;

import com.deceasedcraft.doomedtraits.client.HallucinationClient;
import net.mattlives.doomedmatu.client.ClientDoomedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Лже-показатели в панели здоровья во время галлюцинации (только клиент). */
@Mixin(value = ClientDoomedData.class, remap = false)
public abstract class ClientDoomedDataMixin {

    @Inject(method = "getHeartRate", at = @At("RETURN"), cancellable = true, remap = false)
    private static void doomedtraitsadd$heart(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(HallucinationClient.fake("heart", cir.getReturnValueF()));
    }

    @Inject(method = "getSystolicBP", at = @At("RETURN"), cancellable = true, remap = false)
    private static void doomedtraitsadd$sys(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(HallucinationClient.fake("sys", cir.getReturnValueF()));
    }

    @Inject(method = "getDiastolicBP", at = @At("RETURN"), cancellable = true, remap = false)
    private static void doomedtraitsadd$dia(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(HallucinationClient.fake("dia", cir.getReturnValueF()));
    }

    @Inject(method = "getSpO2", at = @At("RETURN"), cancellable = true, remap = false)
    private static void doomedtraitsadd$spo2(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(HallucinationClient.fake("spo2", cir.getReturnValueF()));
    }

    @Inject(method = "getRespiration", at = @At("RETURN"), cancellable = true, remap = false)
    private static void doomedtraitsadd$resp(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(HallucinationClient.fake("resp", cir.getReturnValueF()));
    }

    @Inject(method = "getBodyTemp", at = @At("RETURN"), cancellable = true, remap = false)
    private static void doomedtraitsadd$temp(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(HallucinationClient.fake("temp", cir.getReturnValueF()));
    }

    @Inject(method = "getBloodVolume", at = @At("RETURN"), cancellable = true, remap = false)
    private static void doomedtraitsadd$blood(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(HallucinationClient.fake("blood", cir.getReturnValueF()));
    }

    @Inject(method = "getMood", at = @At("RETURN"), cancellable = true, remap = false)
    private static void doomedtraitsadd$mood(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(HallucinationClient.fake("mood", cir.getReturnValueF()));
    }

    @Inject(method = "getRedCellFraction", at = @At("RETURN"), cancellable = true, remap = false)
    private static void doomedtraitsadd$rbc(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(HallucinationClient.fake("rbc", cir.getReturnValueF()));
    }

    @Inject(method = "getPain", at = @At("RETURN"), cancellable = true, remap = false)
    private static void doomedtraitsadd$pain(CallbackInfoReturnable<Float> cir) {
        cir.setReturnValue(HallucinationClient.fake("pain", cir.getReturnValueF()));
    }
}
