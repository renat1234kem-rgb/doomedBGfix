package com.deceasedcraft.doomedtraits.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Общие настройки (файл config/doomed_traits_additions-common.toml, читается на сервере). */
public final class CommonConfig {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue FRIEND_VOICE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        FRIEND_VOICE = b.comment("Озвучка реплик воображаемого друга Doomed (голосовые звуки). false = только текст.")
            .define("friend_voice", false);
        SPEC = b.build();
    }

    private CommonConfig() {
    }

    public static boolean friendVoice() {
        try {
            return FRIEND_VOICE.get();
        } catch (Throwable t) {
            return false;
        }
    }
}
