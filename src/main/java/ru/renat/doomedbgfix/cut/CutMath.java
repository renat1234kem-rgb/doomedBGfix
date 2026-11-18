package ru.renat.doomedbgfix.cut;

import static ru.renat.doomedbgfix.cut.CutLayer.clamp01;

/** Чистая математика реза, без классов Minecraft. Ориентиры - ванильное AmputationCut / BleedHandler. */
public final class CutMath {
    private CutMath() {}

    public static final float WORK_PER_SECOND = 0.075f;
    /** Vanilla: один полный проход пилы = 0.08 работы. */
    public static final float WORK_PER_STROKE = 0.08f;

    // Калибровка крови по ванильным ориентирам:
    //   SMALL_BLEED_UNITS = 2, SERIOUS_BLEED_UNITS = 8, DEEP_WOUND_THRESHOLD = 20,
    //   SurgeryManager.OPEN_BLEED_MAX = 1.1, культя после отделения = 100.
    public static final float BLEED_SKIN_END   = 1.0f;
    public static final float BLEED_FAT_END    = 2.0f;
    public static final float BLEED_MUSCLE_END = 6.0f;
    public static final float BLEED_BONE_END   = 8.0f;
    public static final float BLEED_EXIT_END   = 14.0f;

    public record Tissue(float skin, float muscle, boolean fractured, boolean saw, float boneScale) {}

    public static Tissue tissue(float skin, float muscle, boolean fractured, boolean saw) {
        return new Tissue(skin, muscle, fractured, saw, 1f);
    }

    public static float freeDepth(float skin, float muscle) {
        float s = clamp01(1f - skin / 100f);
        float m = clamp01(1f - muscle / 100f);
        float d = CutLayer.SKIN.span() * s
                + CutLayer.FAT.span() * Math.max(s, m * 0.8f)
                + CutLayer.MUSCLE.span() * m;
        return Math.min(d, CutLayer.BONE.start);
    }

    public static float resistance(float depth, Tissue t) {
        CutLayer l = CutLayer.at(depth);
        float k = switch (l) {
            case SKIN   -> 0.35f + 0.65f * clamp01(t.skin() / 100f);
            case FAT    -> 1.0f;
            case MUSCLE -> 0.40f + 0.60f * clamp01(t.muscle() / 100f);
            case EXIT   -> 0.40f + 0.60f * clamp01(t.muscle() / 100f);
            case BONE   -> (t.fractured() ? 0.45f : 1.0f) * (t.saw() ? 1.0f : 2.2f) * Math.max(0.2f, t.boneScale());
        };
        return Math.max(0.05f, l.resistance * k);
    }

    public static float advance(float depth, float work, Tissue t) {
        float d = clamp01(depth);
        if (!Float.isFinite(work) || work <= 0f) return d;
        int guard = 0;
        while (work > 1e-6f && d < 1f && guard++ < 32) {
            CutLayer l = CutLayer.at(d);
            float r = resistance(d, t);
            float need = (l.end - d) * r;
            float used = Math.min(work, need);
            d = (used >= need) ? l.end : d + used / r;
            work -= used;
        }
        return clamp01(d);
    }

    public static float remainingWork(float depth, Tissue t) {
        float d = clamp01(depth);
        float sum = 0f;
        int guard = 0;
        while (d < 1f && guard++ < 32) {
            CutLayer l = CutLayer.at(d);
            sum += (l.end - d) * resistance(d, t);
            d = l.end;
        }
        return sum;
    }

    /**
     * Кровь ДО полного отделения. Пик 14 единиц (~0.7 L/min по твоим замерам),
     * а не 65 как было. Полное отделение по-прежнему даёт ванильные 100 на культе.
     */
    public static float bleedTarget(float depth, float toolMult) {
        float b;
        if (depth < CutLayer.SKIN.end)        b = lerp(0.2f, BLEED_SKIN_END,   CutLayer.SKIN.frac(depth));
        else if (depth < CutLayer.FAT.end)    b = lerp(BLEED_SKIN_END,   BLEED_FAT_END,    CutLayer.FAT.frac(depth));
        else if (depth < CutLayer.MUSCLE.end) b = lerp(BLEED_FAT_END,    BLEED_MUSCLE_END, CutLayer.MUSCLE.frac(depth));
        else if (depth < CutLayer.BONE.end)   b = lerp(BLEED_MUSCLE_END, BLEED_BONE_END,   CutLayer.BONE.frac(depth));
        else                                  b = lerp(BLEED_BONE_END,   BLEED_EXIT_END,   CutLayer.EXIT.frac(depth));
        return b * toolMult;
    }

    public static float skinLoss(float depth) { return clamp01(depth / CutLayer.EXIT.start) * 95f; }

    public static float muscleLoss(float depth) {
        float t = clamp01((depth - CutLayer.FAT.end) / (1f - CutLayer.FAT.end));
        return t * 85f;
    }

    public static float boneProgress(float depth) {
        return clamp01((depth - CutLayer.BONE.start) / CutLayer.BONE.span());
    }

    public static int boneCredit(float boneProgress, int requiredTicks) {
        return Math.round(requiredTicks * (1f - clamp01(boneProgress)));
    }

    /**
     * Боль ровно по ванильной формуле AmputationCut.stroke():
     *   painAdded = 2 + 4*depth + 3*(boneWork / 0.08)
     * только пересчитанной на фактически приложенную работу. Итог за всю процедуру
     * совпадает с оригиналом (~140), независимо от того, как быстро ты пилишь.
     */
    public static float pain(float depth, float work, float toolMult) {
        float strokes = Math.max(0f, work) / WORK_PER_STROKE;
        float base = (2f + 4f * clamp01(depth)) * strokes;
        float bone = CutLayer.at(depth) == CutLayer.BONE ? 3f * strokes : 0f;
        return (base + bone) * toolMult;
    }

    public static float effort(float consciousness, float feltPain) {
        if (!Float.isFinite(consciousness) || !Float.isFinite(feltPain)) return 0.45f;
        float awake = clamp01(consciousness / 100f);
        float pain = clamp01(feltPain / 100f);
        return 0.45f + 0.55f * awake * (1f - 0.55f * pain);
    }

    public static float technique(int reversals, float contact) {
        float rev = clamp01(reversals / 2f);
        return (0.55f + 0.45f * rev) * (0.35f + 0.65f * clamp01(contact));
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * clamp01(t); }
}