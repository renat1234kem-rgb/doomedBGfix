package ru.renat.doomedbgfix.net;

import net.mattlives.doomedmatu.body.PatientSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record S2COpenCutPacket(int part, int patientId, long token, float depth, int tool,
                               LimbView view, PatientSnapshot snapshot, boolean forceInteractive) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(part);
        buf.writeInt(patientId);
        buf.writeLong(token);
        buf.writeFloat(depth);
        buf.writeVarInt(tool);
        view.encode(buf);
        buf.writeBoolean(snapshot != null);
        if (snapshot != null) snapshot.encode(buf);
        buf.writeBoolean(forceInteractive);
    }

    public static S2COpenCutPacket decode(FriendlyByteBuf buf) {
        int part = buf.readVarInt();
        int patientId = buf.readInt();
        long token = buf.readLong();
        float depth = buf.readFloat();
        int tool = buf.readVarInt();
        LimbView view = LimbView.decode(buf);
        PatientSnapshot snapshot = buf.readBoolean() ? PatientSnapshot.decode(buf) : null;
        boolean force = buf.readBoolean();
        return new S2COpenCutPacket(part, patientId, token, depth, tool, view, snapshot, force);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ru.renat.doomedbgfix.client.CutClient.open(this)));
        ctx.get().setPacketHandled(true);
    }
}
