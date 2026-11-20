package com.deceasedcraft.doomedtraits.net;

import com.deceasedcraft.doomedtraits.DoomedTraitsAdditions;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNet {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(DoomedTraitsAdditions.MOD_ID, "main"), () -> VERSION, VERSION::equals, VERSION::equals
    );
    private static boolean registered;

    private ModNet() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        int id = 0;
        CHANNEL.registerMessage(id++, C2SDraftPacket.class, C2SDraftPacket::encode, C2SDraftPacket::decode, C2SDraftPacket::handle);
        CHANNEL.registerMessage(id++, S2CFxPacket.class, S2CFxPacket::encode, S2CFxPacket::decode, S2CFxPacket::handle);
        CHANNEL.registerMessage(id++, C2SGunResultPacket.class, C2SGunResultPacket::encode, C2SGunResultPacket::decode, C2SGunResultPacket::handle);
    }
}
