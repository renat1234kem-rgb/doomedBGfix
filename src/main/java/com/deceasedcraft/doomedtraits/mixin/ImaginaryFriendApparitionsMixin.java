package com.deceasedcraft.doomedtraits.mixin;

import com.deceasedcraft.doomedtraits.AddTrait;
import com.deceasedcraft.doomedtraits.AddTraits;
import net.mattlives.doomedmatu.body.ImaginaryFriendApparitions;
import net.mattlives.doomedmatu.capability.DoomedDataUtil;
import net.mattlives.doomedmatu.config.DoomedConfig;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Сам «пушистый друг» (фигура в мире) показывается только игрокам с обычной «Шизофренией».
 * Без этого патча у Хардкорной шизофрении друг говорит, но его тело сразу прячется.
 */
@Mixin(value = ImaginaryFriendApparitions.class, remap = false)
public abstract class ImaginaryFriendApparitionsMixin {

    @Inject(method = "eligible", at = @At("RETURN"), cancellable = true, remap = false)
    private static void doomedtraitsadd$hardcore(ServerPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() || !player.isAlive() || player.isSpectator() || !DoomedConfig.traitsEnabled()) {
            return;
        }
        boolean ok = DoomedDataUtil.get(player)
            .map(d -> d.isActive() && !d.isDeathPending() && !d.isRefusalPending()
                && AddTraits.active(d, AddTrait.HARDCORE_SCHIZOPHRENIA))
            .orElse(false);
        if (ok) {
            cir.setReturnValue(true);
        }
    }
}
