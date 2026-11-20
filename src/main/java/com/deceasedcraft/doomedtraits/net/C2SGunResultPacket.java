package com.deceasedcraft.doomedtraits.net;

import com.deceasedcraft.doomedtraits.server.SchizoManager;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Клиент: игрок успел убрать пистолет от головы. */
public final class C2SGunResultPacket {
    private final boolean success;

    public C2SGunResultPacket(boolean success) {
        this.success = success;
    }

    public static void encode(C2SGunResultPacket p, FriendlyByteBuf buf) {
        buf.writeBoolean(p.success);
    }

    public static C2SGunResultPacket decode(FriendlyByteBuf buf) {
        return new C2SGunResultPacket(buf.readBoolean());
    }

    public static void handle(C2SGunResultPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getSender() != null) {
                SchizoManager.gunResolved(ctx.get().getSender(), p.success);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
