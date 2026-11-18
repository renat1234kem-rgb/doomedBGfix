package ru.renat.doomedbgfix.mixin;

import net.mattlives.doomedmatu.body.BodyPart;
import net.mattlives.doomedmatu.body.DismemberService;
import net.mattlives.doomedmatu.capability.DoomedData;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.renat.doomedbgfix.config.BGConfig;
import ru.renat.doomedbgfix.server.CutSessions;
import ru.renat.doomedbgfix.server.TourniquetFix;

/**
 * Единственный миксин аддона. Цели - классы Doomed (не обфусцированы), remap = false.
 * 1) перехват открытия мини-игры ампутации (self + coop);
 * 2) возврат жгута перед тем, как makeInert() его обнулит.
 */
@Mixin(value = DismemberService.class, remap = false)
public abstract class DismemberServiceMixin {

    @Inject(method = "openSelfMinigame", at = @At("HEAD"), cancellable = true, remap = false)
    private static void bg$openSelf(ServerPlayer player, BodyPart part, boolean forceInteractive,
                                    CallbackInfoReturnable<Boolean> cir) {
        if (!BGConfig.enableRework()) return;
        cir.setReturnValue(CutSessions.openSelf(player, part, forceInteractive));
    }

    @Inject(method = "openAuthorizedMinigame", at = @At("HEAD"), cancellable = true, remap = false)
    private static void bg$openCoop(ServerPlayer healer, ServerPlayer patient, BodyPart part,
                                    DismemberService.CutTool authorizedTool, boolean implied,
                                    CallbackInfoReturnable<Boolean> cir) {
        if (!BGConfig.enableRework()) return;
        cir.setReturnValue(CutSessions.openCoop(healer, patient, part, authorizedTool, false));
    }

    @Inject(method = "performSelfAmputation", at = @At("HEAD"), remap = false)
    private static void bg$selfStrap(ServerPlayer sp, DoomedData d, BodyPart part, DismemberService.CutTool tool,
                                     CallbackInfoReturnable<Boolean> cir) {
        TourniquetFix.releaseChain(sp, d, part);
    }

    @Inject(method = "performOn", at = @At("HEAD"), remap = false)
    private static void bg$coopStrap(ServerPlayer healer, ServerPlayer patient, DoomedData pd, BodyPart part,
                                     DismemberService.CutTool tool, CallbackInfoReturnable<Boolean> cir) {
        TourniquetFix.releaseChain(patient, pd, part);
    }

    @Inject(method = "dismember", at = @At("HEAD"), remap = false)
    private static void bg$dismemberStrap(ServerPlayer sp, DoomedData d, BodyPart part,
                                          CallbackInfoReturnable<Boolean> cir) {
        TourniquetFix.releaseChain(sp, d, part);
    }

    @Inject(method = "necroticLoss", at = @At("HEAD"), remap = false)
    private static void bg$necroticStrap(ServerPlayer sp, DoomedData d, BodyPart part,
                                         CallbackInfoReturnable<Boolean> cir) {
        TourniquetFix.releaseChain(sp, d, part);
    }

    @Inject(method = "gibHead", at = @At("HEAD"), remap = false)
    private static void bg$headStrap(ServerPlayer sp, DoomedData d, CallbackInfo ci) {
        TourniquetFix.releaseHead(sp, d);
    }
}
