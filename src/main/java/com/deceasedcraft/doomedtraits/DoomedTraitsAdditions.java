package com.deceasedcraft.doomedtraits;

import com.deceasedcraft.doomedtraits.config.ClientConfig;
import com.deceasedcraft.doomedtraits.config.CommonConfig;
import com.deceasedcraft.doomedtraits.net.ModNet;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

@Mod(DoomedTraitsAdditions.MOD_ID)
public class DoomedTraitsAdditions {
    public static final String MOD_ID = "doomed_traits_additions";
    public static final Logger LOGGER = LogUtils.getLogger();

    public DoomedTraitsAdditions() {
        ModNet.register();
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC);
        LOGGER.info("Doomed Traits Additions loaded");
    }
}
