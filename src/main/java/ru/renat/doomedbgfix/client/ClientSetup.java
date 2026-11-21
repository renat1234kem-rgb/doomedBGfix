package ru.renat.doomedbgfix.client;

import net.mattlives.doomedmatu.registry.DoomedItems;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import ru.renat.doomedbgfix.DoomedBGFix;
import ru.renat.doomedbgfix.bag.BagVolume;

@Mod.EventBusSubscriber(modid = DoomedBGFix.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    public static final ResourceLocation BAG_USED = new ResourceLocation(DoomedBGFix.MODID, "bag_used");

    private ClientSetup() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(DoomedItems.BLOOD_BAG_HUMAN.get(), BAG_USED,
                    (stack, level, entity, seed) -> 1f - BagVolume.get(stack) / 100f);
            ItemProperties.register(DoomedItems.SALINE.get(), BAG_USED,
                    (stack, level, entity, seed) -> 1f - BagVolume.get(stack) / 100f);
        });
    }
}