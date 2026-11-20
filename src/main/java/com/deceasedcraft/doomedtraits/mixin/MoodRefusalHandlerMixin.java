package com.deceasedcraft.doomedtraits.mixin;

import com.deceasedcraft.doomedtraits.AddTrait;
import com.deceasedcraft.doomedtraits.AddTraits;
import net.mattlives.doomedmatu.body.MoodRefusalHandler;
import net.mattlives.doomedmatu.capability.DoomedDataUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Сосредоточенный: не отказывается подбирать вещи из-за плохого настроения. */
@Mixin(value = MoodRefusalHandler.class, remap = false)
public abstract class MoodRefusalHandlerMixin {

    @Inject(method = "onItemPickup", at = @At("HEAD"), cancellable = true, remap = false)
    private static void doomedtraitsadd$focused(EntityItemPickupEvent event, CallbackInfo ci) {
        if (event.getEntity() instanceof ServerPlayer sp
            && DoomedDataUtil.get(sp).map(d -> AddTraits.active(d, AddTrait.FOCUSED)).orElse(false)) {
            ci.cancel();
        }
    }
}
