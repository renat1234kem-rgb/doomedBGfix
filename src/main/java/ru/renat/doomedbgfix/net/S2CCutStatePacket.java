package ru.renat.doomedbgfix.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record S2CCutStatePacket(int part, int patientId, long token, float depth,
                                boolean valid, boolean severed, LimbView view) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(part);
        buf.writeInt(patientId);
        buf.writeLong(token);
        buf.writeFloat(depth);
        buf.writeBoolean(valid);
        buf.writeBoolean(severed);
        buf.writeBoolean(view != null);
        if (view != null) view.encode(buf);
    }

    public static S2CCutStatePacket decode(FriendlyByteBuf buf) {
        int part = buf.readVarInt();
        int patientId = buf.readInt();
        long token = buf.readLong();
        float depth = buf.readFloat();
        boolean valid = buf.readBoolean();
        boolean severed = buf.readBoolean();
        LimbView view = buf.readBoolean() ? LimbView.decode(buf) : null;
        return new S2CCutStatePacket(part, patientId, token, depth, valid, severed, view);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ru.renat.doomedbgfix.client.CutClient.accept(this)));
        ctx.get().setPacketHandled(true);
    }
}
