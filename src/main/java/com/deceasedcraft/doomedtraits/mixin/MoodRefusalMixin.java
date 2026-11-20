package com.deceasedcraft.doomedtraits.mixin;

import com.deceasedcraft.doomedtraits.AddTraits;
import com.deceasedcraft.doomedtraits.client.ClientFocus;
import net.mattlives.doomedmatu.body.MoodRefusal;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Сосредоточенный: шанс «отказаться что-то делать» из-за плохого настроения равен нулю.
 * Экраны Doomed (планшет, капсула и т. д.) вычисляют его на клиенте, поэтому смотрим на флаг
 * в маске черт, которую Doomed синхронизирует на клиент.
 */
@Mixin(value = MoodRefusal.class, remap = false)
public abstract class MoodRefusalMixin {

    @Inject(method = "refusalChance", at = @At("RETURN"), cancellable = true, remap = false)
    private static void doomedtraitsadd$focused(float mood, CallbackInfoReturnable<Float> cir) {
        if (cir.getReturnValueF() > 0.0F && FMLEnvironment.dist == Dist.CLIENT && ClientFocus.focused()) {
            cir.setReturnValue(0.0F);
        }
    }
}
