package ru.renat.doomedbgfix.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import ru.renat.doomedbgfix.eye.EyeCutModel;

import java.util.function.Supplier;

public record S2CEyeCutStatePacket(long token, int part, int stage, float[] progress,
                                   boolean valid, boolean severed, LimbView view) {

    public void encode(FriendlyByteBuf buf) {
        buf.writeLong(token);
        buf.writeVarInt(part);
        buf.writeVarInt(stage);
        for (int i = 0; i < EyeCutModel.SEGMENTS; i++) buf.writeFloat(progress[i]);
        buf.writeBoolean(valid);
        buf.writeBoolean(severed);
        buf.writeBoolean(view != null);
        if (view != null) view.encode(buf);
    }

    public static S2CEyeCutStatePacket decode(FriendlyByteBuf buf) {
        long token = buf.readLong();
        int part = buf.readVarInt();
        int stage = buf.readVarInt();
        float[] progress = new float[EyeCutModel.SEGMENTS];
        for (int i = 0; i < progress.length; i++) progress[i] = buf.readFloat();
        boolean valid = buf.readBoolean();
        boolean severed = buf.readBoolean();
        LimbView view = buf.readBoolean() ? LimbView.decode(buf) : null;
        return new S2CEyeCutStatePacket(token, part, stage, progress, valid, severed, view);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ru.renat.doomedbgfix.client.EyeCutClient.accept(this)));
        ctx.get().setPacketHandled(true);
    }
}
