package ru.renat.doomedbgfix.server;

import net.mattlives.doomedmatu.body.BodyPart;
import net.mattlives.doomedmatu.body.ForeignBodyKind;
import net.mattlives.doomedmatu.body.LimbData;
import net.mattlives.doomedmatu.body.VesselInvolvement;
import net.mattlives.doomedmatu.body.WoundClosure;
import net.mattlives.doomedmatu.body.WoundLedgerService;
import net.mattlives.doomedmatu.body.WoundRecord;
import net.mattlives.doomedmatu.body.WoundSurface;
import net.mattlives.doomedmatu.body.WoundType;
import net.mattlives.doomedmatu.capability.DoomedData;
import net.minecraft.util.Mth;
import ru.renat.doomedbgfix.config.BGConfig;
import ru.renat.doomedbgfix.cut.CutLayer;
import ru.renat.doomedbgfix.cut.CutMath;
import ru.renat.doomedbgfix.cut.LimbCut;

/**
 * Рез живёт как настоящая рана в журнале Doomed.
 * Тип SURGICAL_INCISION выбран намеренно: по WoundCarePolicy он ВСЕГДА глубокий,
 * значит рану можно зашить сразу, на любом этапе пиления (и без жгута тоже).
 */
public final class CutWounds {
    private CutWounds() {}

    public static WoundRecord find(DoomedData data, BodyPart part, LimbCut cut) {
        if (cut.woundId() == 0L) return null;
        return WoundLedgerService.findExact(data, part, cut.woundId());
    }

    public static WoundRecord ensure(DoomedData data, BodyPart part, LimbCut cut, boolean saw, long seed) {
        WoundRecord existing = find(data, part, cut);
        if (existing != null && existing.type() == WoundType.SURGICAL_INCISION) return existing;
        float depth = Math.max(cut.depth(), 0.02f);
        WoundRecord created = WoundLedgerService.create(
                data, part, WoundType.SURGICAL_INCISION, seed,
                CutMath.skinLoss(depth), CutMath.muscleLoss(depth), depth,
                CutMath.bleedTarget(depth, saw ? 1.0f : 1.35f) * BGConfig.bleedScale(),
                0.0f, saw ? 4.0f : 32.0f, ForeignBodyKind.NONE, 0,
                depth >= CutLayer.MUSCLE.start ? VesselInvolvement.MAJOR : VesselInvolvement.MINOR);
        if (created != null) {
            created.setSurface(WoundSurface.FRONT);
            created.setAnchor(0.5f, 0.5f);
            created.setClosure(WoundClosure.OPEN);
            cut.setWoundId(created.id());
            WoundLedgerService.applyProjection(data.getLimb(part));
        }
        return created;
    }

    public static void apply(DoomedData data, BodyPart part, LimbCut cut, float toolBleed, float contaminationAdd) {
        LimbData limb = data.getLimb(part);
        if (limb.isDismembered()) return;
        float depth = cut.depth();
        WoundRecord wound = ensure(data, part, cut, contaminationAdd <= 0.0f, part.ordinal() * 31L + 0x5C07L);
        float target = CutMath.bleedTarget(depth, toolBleed) * BGConfig.bleedScale();
        float skin = CutMath.skinLoss(depth);
        float muscle = CutMath.muscleLoss(depth);
        float tissueDepth = BGConfig.forceDeepWound() ? Math.max(0.75f, depth) : depth;

        if (wound != null) {
            wound.setSkinLoss(Math.max(wound.skinLoss(), skin));
            wound.setMuscleLoss(Math.max(wound.muscleLoss(), muscle));
            wound.setTissueLoss(Math.max(wound.skinLoss(), wound.muscleLoss()));
            wound.setTissueDepth(Math.max(wound.tissueDepth(), tissueDepth));
            if (!wound.closure().definitivelyClosed()) {
                wound.setBleeding(Math.max(wound.bleeding(), Math.min(target, limb.maxBleedForSkin())));
                wound.setClosure(WoundClosure.OPEN);
            }
            if (contaminationAdd > 0.0f) {
                wound.setContamination(Mth.clamp(wound.contamination() + contaminationAdd * 0.02f, 0.0f, 100.0f));
            }
            if (depth >= CutLayer.MUSCLE.start) wound.setVessel(VesselInvolvement.MAJOR);
            WoundLedgerService.applyProjection(limb);
        } else {
            limb.setSkinHealth(Math.min(limb.getSkinHealth(), 100.0f - skin));
            limb.setMuscleHealth(Math.min(limb.getMuscleHealth(), 100.0f - muscle));
            limb.setBleed(Math.max(limb.getBleed(), Math.min(target, limb.maxBleedForSkin())));
            limb.setDeepWound(true);
            limb.setStitched(false);
        }
    }

    public static void forget(DoomedData data, BodyPart part, LimbCut cut) {
        if (cut.woundId() != 0L) {
            WoundLedgerService.removeWoundById(data, part, cut.woundId());
            cut.setWoundId(0L);
        }
    }
}
