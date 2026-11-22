package ru.renat.doomedbgfix.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import ru.renat.doomedbgfix.eye.EyeCutModel;

import java.util.function.Supplier;

public record S2COpenEyeCutPacket(int part, long token, float[] progress, LimbView view,
                                  boolean forceInteractive) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(part);
        buf.writeLong(token);
        for (int i = 0; i < EyeCutModel.SEGMENTS; i++) buf.writeFloat(progress[i]);
        view.encode(buf);
        buf.writeBoolean(forceInteractive);
    }

    public static S2COpenEyeCutPacket decode(FriendlyByteBuf buf) {
        int part = buf.readVarInt();
        long token = buf.readLong();
        float[] progress = new float[EyeCutModel.SEGMENTS];
        for (int i = 0; i < progress.length; i++) progress[i] = buf.readFloat();
        LimbView view = LimbView.decode(buf);
        boolean force = buf.readBoolean();
        return new S2COpenEyeCutPacket(part, token, progress, view, force);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ru.renat.doomedbgfix.client.EyeCutClient.open(this)));
        ctx.get().setPacketHandled(true);
    }
}
