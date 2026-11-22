package ru.renat.doomedbgfix.net;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import ru.renat.doomedbgfix.DoomedBGFix;

import java.util.Optional;

public final class BGNetwork {

    private static final String PROTOCOL = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(DoomedBGFix.MODID, "cut"))
            .networkProtocolVersion(() -> PROTOCOL)
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .simpleChannel();

    private BGNetwork() {}

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, C2SCutWorkPacket.class, C2SCutWorkPacket::encode, C2SCutWorkPacket::decode,
                C2SCutWorkPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, C2SCutStopPacket.class, C2SCutStopPacket::encode, C2SCutStopPacket::decode,
                C2SCutStopPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, S2COpenCutPacket.class, S2COpenCutPacket::encode, S2COpenCutPacket::decode,
                S2COpenCutPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, S2CCutStatePacket.class, S2CCutStatePacket::encode, S2CCutStatePacket::decode,
                S2CCutStatePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, C2SEyeCutWorkPacket.class, C2SEyeCutWorkPacket::encode, C2SEyeCutWorkPacket::decode,
                C2SEyeCutWorkPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, S2COpenEyeCutPacket.class, S2COpenEyeCutPacket::encode, S2COpenEyeCutPacket::decode,
                S2COpenEyeCutPacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, S2CEyeCutStatePacket.class, S2CEyeCutStatePacket::encode, S2CEyeCutStatePacket::decode,
                S2CEyeCutStatePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendOpen(ServerPlayer player, S2COpenCutPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendState(ServerPlayer player, S2CCutStatePacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendOpenEye(ServerPlayer player, S2COpenEyeCutPacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendEyeState(ServerPlayer player, S2CEyeCutStatePacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendEyeWork(C2SEyeCutWorkPacket packet) { CHANNEL.sendToServer(packet); }

    public static void sendWork(C2SCutWorkPacket packet) { CHANNEL.sendToServer(packet); }
    public static void sendStop(C2SCutStopPacket packet) { CHANNEL.sendToServer(packet); }
}
