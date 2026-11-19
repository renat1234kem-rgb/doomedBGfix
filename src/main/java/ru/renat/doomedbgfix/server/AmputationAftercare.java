package ru.renat.doomedbgfix.server;

import net.mattlives.doomedmatu.body.AmputationState;
import net.mattlives.doomedmatu.body.BodyPart;
import net.mattlives.doomedmatu.body.DismemberService;
import net.mattlives.doomedmatu.body.LimbData;
import net.mattlives.doomedmatu.body.WoundLedgerService;
import net.mattlives.doomedmatu.body.WoundRecord;
import net.mattlives.doomedmatu.body.WoundType;
import net.mattlives.doomedmatu.capability.DoomedData;
import net.mattlives.doomedmatu.capability.DoomedDataUtil;
import net.minecraft.server.level.ServerPlayer;
import ru.renat.doomedbgfix.config.BGConfig;
import ru.renat.doomedbgfix.cut.CutMath;

/**
 * Kultя: ваниль ставит bleeding=100 (2.4 L/min) на ПРОКСИМАЛЬНЫЙ сегмент и держит
 * residualPain ~90, пока рана не свернётся до 8 (~90 минут).
 *
 * Здесь всё сделано идемпотентными потолками в тике, а не разовой правкой:
 * что бы ни надувало значения обратно (enforceInvariants / reconcileRegion),
 * мы срезаем их каждый тик. Только вниз, никогда вверх.
 */
public final class AmputationAftercare {

    private AmputationAftercare() {}

    /** Разово после отделения. Оставлено как есть, но основную работу делает tick(). */
    public static void scaleStumpBleed(DoomedData data, BodyPart boundary) {
        if (data == null || boundary == null) return;
        clampStump(data, boundary, 100f * BGConfig.stumpBleedScale());
    }

    public static void tick(ServerPlayer player, DoomedData data) {
        if (data == null || !data.isActive()) return;

        // множитель ранней боли живёт в CutMath, чтобы не менять签名 pain()
        CutMath.softPainMult = BGConfig.softTissuePainScale();

        float bleedCap = 100f * BGConfig.stumpBleedScale();
        boolean slow = player.tickCount % 20 == 0;
        float painCap = BGConfig.residualPainCap();
        float boost = BGConfig.stumpHealBoost();
        boolean changed = false;

        for (BodyPart part : BodyPart.VALUES) {
            AmputationState st = data.getAmputation(part);
            if (st == null || !st.active()) continue;

            // кровь культи держим под потолком КАЖДЫЙ тик
            if (bleedCap < 100f && clampStump(data, part, bleedCap)) changed = true;

            if (!slow) continue;

            if (painCap < 100f && st.residualPain() > painCap) {
                st.setResidualPain(painCap);
                changed = true;
            }

            if (boost > 0f && st.stumpHealing() < 100f) {
                BodyPart stump = DismemberService.stumpFor(part);
                float bleed = 0f;
                if (stump != null && st.stumpWoundId() != 0L) {
                    WoundRecord w = WoundLedgerService.findExact(data, stump, st.stumpWoundId());
                    if (w != null) bleed = w.bleeding();
                }
                st.setStumpHealing(st.stumpHealing() + boost * 0.13f * (bleed > 8f ? 0.35f : 1f));
                changed = true;
            }
        }

        if (changed) DoomedDataUtil.sync(player);
    }

    /** Срезает кровь раны культи до cap. Возвращает true, если что-то изменилось. */
    private static boolean clampStump(DoomedData data, BodyPart boundary, float cap) {
        BodyPart stump = DismemberService.stumpFor(boundary);
        if (stump == null) return false;
        LimbData limb = data.getLimb(stump);
        if (limb.isDismembered()) return false;

        boolean hit = false;
        boolean found = false;
        for (WoundRecord w : limb.getWounds()) {
            if (w.type() != WoundType.AMPUTATION_STUMP) continue;
            found = true;
            if (w.closure().definitivelyClosed()) continue;   // зашито — не лезем
            if (w.bleeding() > cap) {
                w.setBleeding(cap);
                hit = true;
            }
        }
        if (!found) return false;

        if (hit) WoundLedgerService.applyProjection(limb);
        // проекция может быть перезатёрта проекцией другой раны — держим и агрегат
        if (limb.getBleed() > cap) {
            limb.setBleed(cap);
            hit = true;
        }
        return hit;
    }
}