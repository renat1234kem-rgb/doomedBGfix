package ru.renat.doomedbgfix.mixin;

import net.mattlives.doomedmatu.body.BodyPart;
import net.mattlives.doomedmatu.client.puppet.PuppetSprites;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.renat.doomedbgfix.client.CyberEyeSprites;

/**
 * Клиентский миксин (см. секцию "client" в doomedbgfix.mixins.json).
 * Панель здоровья Doomed берёт спрайт глаза через PuppetSprites.eyeOpen(part),
 * для глаза с имплантом подставляем свой спрайт.
 */
@Mixin(value = PuppetSprites.class, remap = false)
public abstract class PuppetSpritesMixin {

    @Shadow(remap = false)
    public abstract String styleDir();

    @Inject(method = "eyeOpen", at = @At("HEAD"), cancellable = true, remap = false)
    private void bg$cyberEye(BodyPart part, CallbackInfoReturnable<PuppetSprites.BodySpr> cir) {
        PuppetSprites.BodySpr cyber = CyberEyeSprites.forEye(part, this.styleDir());
        if (cyber != null) cir.setReturnValue(cyber);
    }
}
