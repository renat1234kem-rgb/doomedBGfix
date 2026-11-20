package com.deceasedcraft.doomedtraits.mixin;

import com.deceasedcraft.doomedtraits.AddTrait;
import com.deceasedcraft.doomedtraits.AddTraits;
import com.deceasedcraft.doomedtraits.server.TraitEffects;
import net.mattlives.doomedmatu.body.CannibalismService;
import net.mattlives.doomedmatu.capability.DoomedData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Каннибал: человечина не бьёт по психике и здоровью, даже нравится. */
@Mixin(value = CannibalismService.class, remap = false)
public abstract class CannibalismServiceMixin {

    @Inject(method = "consume", at = @At("HEAD"), cancellable = true, remap = false)
    private static void doomedtraitsadd$enjoy(
        ServerPlayer sp, DoomedData d, ItemStack flesh, boolean cooked, float condition, CallbackInfo ci
    ) {
        if (AddTraits.active(d, AddTrait.CANNIBAL)) {
            TraitEffects.cannibalMeal(sp, d);
            ci.cancel();
        }
    }
}
