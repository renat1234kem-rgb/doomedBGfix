package ru.renat.doomedbgfix.net;

import net.mattlives.doomedmatu.body.PatientSnapshot;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import ru.renat.doomedbgfix.eye.EyeCutModel;

import java.util.function.Supplier;

public record S2COpenEyeCutPacket(int part, int patientId, long token, int mode, float[] progress,
                                  LimbView view, PatientSnapshot snapshot, boolean forceInteractive) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(part);
        buf.writeInt(patientId);
        buf.writeLong(token);
        buf.writeVarInt(mode);
        for (int i = 0; i < EyeCutModel.SEGMENTS; i++) buf.writeFloat(progress[i]);
        view.encode(buf);
        buf.writeBoolean(snapshot != null);
        if (snapshot != null) snapshot.encode(buf);
        buf.writeBoolean(forceInteractive);
    }

    public static S2COpenEyeCutPacket decode(FriendlyByteBuf buf) {
        int part = buf.readVarInt();
        int patientId = buf.readInt();
        long token = buf.readLong();
        int mode = buf.readVarInt();
        float[] progress = new float[EyeCutModel.SEGMENTS];
        for (int i = 0; i < progress.length; i++) progress[i] = buf.readFloat();
        LimbView view = LimbView.decode(buf);
        PatientSnapshot snapshot = buf.readBoolean() ? PatientSnapshot.decode(buf) : null;
        boolean force = buf.readBoolean();
        return new S2COpenEyeCutPacket(part, patientId, token, mode, progress, view, snapshot, force);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ru.renat.doomedbgfix.client.EyeCutClient.open(this)));
        ctx.get().setPacketHandled(true);
    }
}
