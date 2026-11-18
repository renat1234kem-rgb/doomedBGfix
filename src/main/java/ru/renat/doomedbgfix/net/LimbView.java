package ru.renat.doomedbgfix.net;

import net.mattlives.doomedmatu.body.BodyPart;
import net.mattlives.doomedmatu.body.LimbData;
import net.mattlives.doomedmatu.capability.DoomedData;
import net.minecraft.network.FriendlyByteBuf;

/** Компактный срез тканей для отрисовки мини-игры. */
public record LimbView(float skin, float muscle, int bone, float bleed, float pain,
                       float infection, float frostbite, float burn,
                       boolean tourniquet, boolean blocked, boolean splinted,
                       float bleedRate, float blood, float consciousness) {

    public static LimbView of(DoomedData data, BodyPart part) {
        LimbData l = data.getLimb(part);
        return new LimbView(l.getSkinHealth(), l.getMuscleHealth(), l.getBone().ordinal(), l.getBleed(),
                l.getPain(), l.getInfection(), l.getFrostbite(), l.getBurn(),
                l.isTourniquet(), data.isBleedBlocked(part), l.isSplinted(),
                data.getBleedRate(), data.getBloodVolume(), data.getConsciousness());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeFloat(skin);
        buf.writeFloat(muscle);
        buf.writeByte(bone);
        buf.writeFloat(bleed);
        buf.writeFloat(pain);
        buf.writeFloat(infection);
        buf.writeFloat(frostbite);
        buf.writeFloat(burn);
        buf.writeBoolean(tourniquet);
        buf.writeBoolean(blocked);
        buf.writeBoolean(splinted);
        buf.writeFloat(bleedRate);
        buf.writeFloat(blood);
        buf.writeFloat(consciousness);
    }

    public static LimbView decode(FriendlyByteBuf buf) {
        return new LimbView(buf.readFloat(), buf.readFloat(), buf.readByte(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                buf.readFloat(), buf.readFloat(), buf.readFloat());
    }
}
