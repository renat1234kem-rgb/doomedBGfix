package ru.renat.doomedbgfix.server;

import net.mattlives.doomedmatu.body.BodyPart;
import net.mattlives.doomedmatu.body.BoneState;
import net.mattlives.doomedmatu.body.LimbData;
import net.mattlives.doomedmatu.capability.DoomedData;
import ru.renat.doomedbgfix.cut.CutLayer;
import ru.renat.doomedbgfix.cut.CutMath;
import ru.renat.doomedbgfix.cut.LimbCut;

/** Дошёл до кости - перелом; глубина пропила задаёт срок срастания. */
public final class CutBone {
    private CutBone() {}

    public static void onCut(DoomedData data, BodyPart part, LimbCut cut) {
        LimbData limb = data.getLimb(part);
        if (limb.isDismembered() || cut.depth() < CutLayer.BONE.start) return;
        if (limb.getBone() != BoneState.FRACTURED) {
            limb.setBone(BoneState.FRACTURED);
            limb.setBoneHealTicks(0);
        }
        int required = CutBridge.requiredBoneTicks(data, limb);
        int credit = CutMath.boneCredit(cut.boneProgress(), required);
        if (limb.getBoneHealTicks() > credit) limb.setBoneHealTicks(credit);
    }

    public static void tick(DoomedData data, BodyPart part, LimbCut cut) {
        LimbData limb = data.getLimb(part);
        if (limb.isDismembered() || cut.depth() <= CutLayer.BONE.start) return;
        if (limb.getBone() != BoneState.FRACTURED) {
            cut.clampToBoneStart();
            return;
        }
        int required = CutBridge.requiredBoneTicks(data, limb);
        float healed = required <= 0 ? 1.0f : (float) limb.getBoneHealTicks() / (float) required;
        cut.syncBone(1.0f - CutLayer.clamp01(healed));
    }
}
