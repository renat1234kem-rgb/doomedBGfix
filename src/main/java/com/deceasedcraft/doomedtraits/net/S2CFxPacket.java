package com.deceasedcraft.doomedtraits.net;

import com.deceasedcraft.doomedtraits.client.HallucinationClient;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Сервер -> клиент: визуальный эффект галлюцинации. */
public final class S2CFxPacket {
    public static final int INVERT = 0;
    public static final int IMAGE = 1;
    public static final int RED_FLASH = 2;
    public static final int VIGNETTE = 3;
    public static final int SHAKE = 4;
    public static final int HEAD_SNAP = 5;
    public static final int FAKE_VITALS = 6;
    public static final int GUN = 7;
    public static final int BLACKOUT = 8;
    /** Локальный (не позиционный) звук: str = id звука, arg = громкость в %, duration = через сколько тиков оборвать (0 = целиком). */
    public static final int SOUND = 9;
    /** Жажда убийства: arg = 1 включить, 0 выключить; duration = максимум тиков. */
    public static final int BLOODLUST = 10;
    /** Депрессивный провал: тёмно-синее затемнение на duration тиков. */
    public static final int DEPRESSION = 11;
    public static final int FORGET = 12;
    public static final int FLICKER = 13;
    /** arg = id моба, которого «подменяем». */
    public static final int WRONG_MOB = 14;
    public static final int DOPPELGANGER = 15;
    public static final int DISTORT = 16;
    public static final int MUFFLE = 17;
    /** Закрыть поддельный экран смерти через duration тиков. */
    public static final int DEATH_CLOSE = 18;

    private final int type;
    private final int duration;
    private final int arg;
    private final String str;

    public S2CFxPacket(int type, int duration, int arg) {
        this(type, duration, arg, "");
    }

    public S2CFxPacket(int type, int duration, int arg, String str) {
        this.type = type;
        this.duration = duration;
        this.arg = arg;
        this.str = str == null ? "" : str;
    }

    public static void encode(S2CFxPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.type);
        buf.writeVarInt(p.duration);
        buf.writeInt(p.arg);
        buf.writeUtf(p.str);
    }

    public static S2CFxPacket decode(FriendlyByteBuf buf) {
        return new S2CFxPacket(buf.readVarInt(), buf.readVarInt(), buf.readInt(), buf.readUtf());
    }

    public static void handle(S2CFxPacket p, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> HallucinationClient.handle(p.type, p.duration, p.arg, p.str)));
        ctx.get().setPacketHandled(true);
    }
}
