package ru.renat.doomedbgfix.bag;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import ru.renat.doomedbgfix.config.BGConfig;

public final class BagTooltip {

    @SubscribeEvent
    public void onTooltip(ItemTooltipEvent event) {
        if (!BGConfig.bagPartialUse()) return;
        if (!BagVolume.isBag(event.getItemStack())) return;
        int ml = BagVolume.realMl(event.getItemStack());
        int cap = BagVolume.capacityMl(event.getItemStack());
        event.getToolTip().add(Component.literal(ml + " / " + cap + " ml")
                .withStyle(ml > 0 ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY));
    }
}
