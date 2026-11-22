package ru.renat.doomedbgfix.mixin;

import net.mattlives.doomedmatu.body.BodyPart;
import net.mattlives.doomedmatu.body.CoopTreatmentType;
import net.mattlives.doomedmatu.body.EyeFunctionService;
import net.mattlives.doomedmatu.client.ClientDoomedData;
import net.mattlives.doomedmatu.client.screen.StatusPanelScreen;
import net.mattlives.doomedmatu.network.DoomedNetwork;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.renat.doomedbgfix.eye.EyeTools;

/**
 * Клик холодным оружием / ножом для растений по глазу на панели здоровья отправляет
 * штатный пакет "начать ампутацию". Сервер (CoopMedicalEyeMixin) запускает мини-игру.
 * Клиентский миксин: см. секцию "client" в doomedbgfix.mixins.json.
 */
@Mixin(value = StatusPanelScreen.class, remap = false)
public abstract class StatusPanelScreenMixin {

    @Inject(method = "routeHeldTreatment", at = @At("HEAD"), cancellable = true, remap = false)
    private void bg$eyeCut(BodyPart part, ItemStack held, CallbackInfoReturnable<Boolean> cir) {
        if (!EyeFunctionService.isEye(part) || ClientDoomedData.isLimbDismembered(part)) return;
        if (!EyeTools.isEyeCutTool(held)) return;
        DoomedNetwork.sendBeginTreatment(CoopTreatmentType.AMPUTATE, part);
        cir.setReturnValue(true);
    }
}
