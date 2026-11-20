package com.deceasedcraft.doomedtraits.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Клиентские настройки (файл config/doomed_traits_additions-client.toml). */
public final class ClientConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue FOXY_JUMPSCARE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        FOXY_JUMPSCARE = b.comment("Шуточный режим: каждый «25-й кадр» (и страшный, и тёплый) заменяется скримером Фокси со звуком.")
            .define("foxy_jumpscare", false);
        SPEC = b.build();
    }

    private ClientConfig() {
    }

    public static boolean foxy() {
        try {
            return FOXY_JUMPSCARE.get();
        } catch (Throwable t) {
            return false;
        }
    }
}
