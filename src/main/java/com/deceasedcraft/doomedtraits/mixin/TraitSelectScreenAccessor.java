package com.deceasedcraft.doomedtraits.mixin;

import net.mattlives.doomedmatu.body.Trait;
import net.mattlives.doomedmatu.client.screen.TraitSelectScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Доступ к приватным полям и методам экрана выбора черт (только клиент). */
@Mixin(value = TraitSelectScreen.class, remap = false)
public interface TraitSelectScreenAccessor {

    @Accessor(value = "mask", remap = false)
    int doomedtraitsadd$getMask();

    @Accessor(value = "mask", remap = false)
    void doomedtraitsadd$setMask(int mask);

    @Accessor(value = "hovered", remap = false)
    Trait doomedtraitsadd$getHovered();

    @Invoker(value = "refresh", remap = false)
    void doomedtraitsadd$refresh();

    @Invoker(value = "traitAt", remap = false)
    Trait doomedtraitsadd$traitAt(double mouseX, double mouseY);
}
