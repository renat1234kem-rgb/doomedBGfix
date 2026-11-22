package ru.renat.doomedbgfix.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import ru.renat.doomedbgfix.server.CutSessions;
import ru.renat.doomedbgfix.server.EyeCutSessions;

import java.util.function.Supplier;

public record C2SCutStopPacket(long token) {

    public void encode(FriendlyByteBuf buf) { buf.writeLong(token); }

    public static C2SCutStopPacket decode(FriendlyByteBuf buf) { return new C2SCutStopPacket(buf.readLong()); }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) {
                CutSessions.stop(sender, token);
                EyeCutSessions.stop(sender, token);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
