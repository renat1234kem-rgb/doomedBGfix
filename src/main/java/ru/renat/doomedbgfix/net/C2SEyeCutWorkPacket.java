package ru.renat.doomedbgfix.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import ru.renat.doomedbgfix.server.EyeCutSessions;

import java.util.function.Supplier;

public record C2SEyeCutWorkPacket(long token, int part, int index, float delta) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeLong(token);
        buf.writeVarInt(part);
        buf.writeVarInt(index);
        buf.writeFloat(delta);
    }

    public static C2SEyeCutWorkPacket decode(FriendlyByteBuf buf) {
        return new C2SEyeCutWorkPacket(buf.readLong(), buf.readVarInt(), buf.readVarInt(), buf.readFloat());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) EyeCutSessions.work(sender, token, part, index, delta);
        });
        ctx.get().setPacketHandled(true);
    }
}
