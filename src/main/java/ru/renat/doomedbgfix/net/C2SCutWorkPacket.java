package ru.renat.doomedbgfix.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import ru.renat.doomedbgfix.server.CutSessions;

import java.util.function.Supplier;

public record C2SCutWorkPacket(long token, int part, float work, int reversals, float contact) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeLong(token);
        buf.writeVarInt(part);
        buf.writeFloat(work);
        buf.writeVarInt(reversals);
        buf.writeFloat(contact);
    }

    public static C2SCutWorkPacket decode(FriendlyByteBuf buf) {
        return new C2SCutWorkPacket(buf.readLong(), buf.readVarInt(), buf.readFloat(), buf.readVarInt(), buf.readFloat());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) CutSessions.work(sender, token, part, work, reversals, contact);
        });
        ctx.get().setPacketHandled(true);
    }
}
