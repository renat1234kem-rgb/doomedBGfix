package ru.renat.doomedbgfix.mixin;

import net.mattlives.doomedmatu.body.CoopMedical;
import net.mattlives.doomedmatu.body.DrugType;
import net.mattlives.doomedmatu.item.BloodBagItem;
import net.mattlives.doomedmatu.registry.DoomedItems;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.renat.doomedbgfix.bag.BagVolume;
import ru.renat.doomedbgfix.config.BGConfig;

@Mixin(value = CoopMedical.class, remap = false)
public abstract class CoopMedicalMixin {

    @Inject(method = "injectionVolume", at = @At("HEAD"), cancellable = true, remap = false)
    private static void bg$bagVolume(ItemStack held, DrugType drug, CallbackInfoReturnable<Integer> cir) {
        if (!BGConfig.bagPartialUse()) return;
        boolean blood = drug == DrugType.BLOOD && held.getItem() instanceof BloodBagItem;
        boolean saline = drug == DrugType.SALINE && held.is(DoomedItems.SALINE.get());
        if (blood || saline) cir.setReturnValue(BagVolume.get(held));
    }
}
