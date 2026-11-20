package com.deceasedcraft.doomedtraits.net;

import com.deceasedcraft.doomedtraits.AddTraits;
import com.deceasedcraft.doomedtraits.server.Draft;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Клиент сообщает, какие наши черты выбраны на экране (до подтверждения). */
public final class C2SDraftPacket {
    private final int mask2;

    public C2SDraftPacket(int mask2) {
        this.mask2 = mask2;
    }

    public static void encode(C2SDraftPacket p, FriendlyByteBuf buf) {
        buf.writeInt(p.mask2);
    }

    public static C2SDraftPacket decode(FriendlyByteBuf buf) {
        return new C2SDraftPacket(buf.readInt());
    }

    public static void handle(C2SDraftPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (ctx.get().getSender() != null) {
                Draft.set(ctx.get().getSender().getUUID(), p.mask2 & AddTraits.ALL_MASK);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
