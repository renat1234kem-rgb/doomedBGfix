package ru.renat.doomedbgfix;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.renat.doomedbgfix.bag.BagSweeper;
import ru.renat.doomedbgfix.bag.BagTooltip;
import ru.renat.doomedbgfix.bag.BarrelFill;
import ru.renat.doomedbgfix.bag.EmptyBagRefill;
import ru.renat.doomedbgfix.config.BGConfig;
import ru.renat.doomedbgfix.cut.CutCapability;
import ru.renat.doomedbgfix.net.BGNetwork;
import ru.renat.doomedbgfix.registry.EyeItems;
import ru.renat.doomedbgfix.server.CutTicker;

@Mod(DoomedBGFix.MODID)
public final class DoomedBGFix {
    public static final String MODID = "doomedbgfix";
    public static final String VERSION = "0.3.3";
    public static final Logger LOGGER = LogManager.getLogger("doomedbgfix");

    public DoomedBGFix() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, BGConfig.SPEC, "doomedbgfix-common.toml");
        CutCapability.register(modBus);
        EyeItems.register(modBus);
        BGNetwork.register();
        MinecraftForge.EVENT_BUS.register(new CutTicker());
        MinecraftForge.EVENT_BUS.register(new BarrelFill());
        MinecraftForge.EVENT_BUS.register(new BagTooltip());
        MinecraftForge.EVENT_BUS.register(new EmptyBagRefill());
        LOGGER.info("[doomedbgfix] v{} armed: cut rework, bags, stump aftercare, bandages", VERSION);
    }
}
