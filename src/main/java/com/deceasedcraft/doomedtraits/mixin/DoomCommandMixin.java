package com.deceasedcraft.doomedtraits.mixin;

import com.deceasedcraft.doomedtraits.AddTraits;
import com.deceasedcraft.doomedtraits.DoomedDataExt;
import com.deceasedcraft.doomedtraits.server.AnorexiaStart;
import com.deceasedcraft.doomedtraits.server.Draft;
import com.mojang.brigadier.context.CommandContext;
import net.mattlives.doomedmatu.capability.DoomedDataUtil;
import net.mattlives.doomedmatu.command.DoomCommand;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Подтверждение выбора черт: проверяем наш набор до Doomed и записываем после. */
@Mixin(value = DoomCommand.class, remap = false)
public abstract class DoomCommandMixin {

    @Inject(method = "applyTraits", at = @At("HEAD"), cancellable = true, remap = false)
    private static void doomedtraitsadd$validate(
        CommandContext<CommandSourceStack> ctx, ServerPlayer sp, int mask, CallbackInfoReturnable<Integer> cir
    ) {
        int draft = Draft.get(sp.getUUID());
        boolean markerOk = ((mask & AddTraits.COST_MARKER) != 0) == (draft != 0);
        boolean costOk = AddTraits.decodeCost(mask) == AddTraits.cost(draft);
        boolean flagOk = ((mask & AddTraits.FOCUSED_FLAG) != 0) == AddTraits.has(draft, com.deceasedcraft.doomedtraits.AddTrait.FOCUSED);
        if (!markerOk || !costOk || !flagOk || AddTraits.conflicts(draft, mask)) {
            sp.displayClientMessage(Component.literal("Doomed Traits Additions: выбор не совпал, открой меню черт заново."), false);
            cir.setReturnValue(0);
        }
    }

    @Inject(
        method = "applyTraits",
        at = @At(
            value = "INVOKE",
            target = "Lnet/mattlives/doomedmatu/capability/DoomedData;reconcileTraitHeadStarts(I)Z",
            shift = At.Shift.AFTER,
            remap = false
        ),
        remap = false
    )
    private static void doomedtraitsadd$commit(
        CommandContext<CommandSourceStack> ctx, ServerPlayer sp, int mask, CallbackInfoReturnable<Integer> cir
    ) {
        // Следом Doomed сам вызывает DoomedDataUtil.sync(sp).
        DoomedDataUtil.get(sp).ifPresent(d -> {
            ((DoomedDataExt) (Object) d).doomedtraitsadd$setMask2(Draft.get(sp.getUUID()));
            AnorexiaStart.apply(d);
        });
    }
}
