package ru.renat.doomedbgfix.registry;

import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import ru.renat.doomedbgfix.DoomedBGFix;
import ru.renat.doomedbgfix.eye.EyeImplantItem;
import ru.renat.doomedbgfix.eye.EmptyEyeImplantItem;

public class EyeItems {
    public static final DeferredRegister<Item> ITEMS = 
        DeferredRegister.create(ForgeRegistries.ITEMS, DoomedBGFix.MODID);

    public static final RegistryObject<Item> EYE_IMPLANT = ITEMS.register("eye_implant",
        () -> new EyeImplantItem(new Item.Properties().stacksTo(1)));

    public static final RegistryObject<Item> EMPTY_EYE_IMPLANT = ITEMS.register("empty_eye_implant",
        () -> new EmptyEyeImplantItem(new Item.Properties().stacksTo(16)));

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}