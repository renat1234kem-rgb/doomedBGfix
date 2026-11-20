package com.deceasedcraft.doomedtraits.mixin;

import com.deceasedcraft.doomedtraits.AddTrait;
import com.deceasedcraft.doomedtraits.AddTraits;
import com.deceasedcraft.doomedtraits.config.CommonConfig;
import net.mattlives.doomedmatu.body.ImaginaryFriend;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import net.mattlives.doomedmatu.capability.DoomedData;
import net.mattlives.doomedmatu.config.DoomedConfig;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Хардкорная шизофрения: «пушистый друг» Doomed работает и без обычной черты «Шизофрения». */
@Mixin(value = ImaginaryFriend.class, remap = false)
public abstract class ImaginaryFriendMixin {

    /** Озвучка реплик друга: по умолчанию выключена (config: friend_voice). */
    @ModifyArg(
        method = "tick",
        at = @At(
            value = "INVOKE",
            target = "Lnet/mattlives/doomedmatu/network/S2CImaginaryFriendLinePacket;<init>(Lnet/minecraft/resources/ResourceLocation;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lnet/minecraft/resources/ResourceLocation;I)V",
            remap = false
        ),
        index = 4,
        remap = false,
        require = 0
    )
    private static ResourceLocation doomedtraitsadd$noVoice(ResourceLocation sound) {
        return CommonConfig.friendVoice() ? sound : null;
    }

    @Inject(method = "eligible", at = @At("RETURN"), cancellable = true, remap = false)
    private static void doomedtraitsadd$hardcore(ServerPlayer player, DoomedData data, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()
            && data != null
            && data.isActive()
            && !data.isDeathPending()
            && !data.isRefusalPending()
            && player.isAlive()
            && !player.isSpectator()
            && DoomedConfig.traitsEnabled()
            && AddTraits.active(data, AddTrait.HARDCORE_SCHIZOPHRENIA)) {
            cir.setReturnValue(true);
        }
    }
}
