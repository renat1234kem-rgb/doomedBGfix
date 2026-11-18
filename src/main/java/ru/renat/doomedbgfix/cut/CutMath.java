package ru.renat.doomedbgfix.cut;

import static ru.renat.doomedbgfix.cut.CutLayer.clamp01;

/** Чистая математика реза, без классов Minecraft. Ориентиры - ванильное AmputationCut. */
public final class CutMath {
    private CutMath() {}

    public static final float WORK_PER_SECOND = 0.075f;
    /** Vanilla: один полный проход пилы = 0.08 работы. */
    public static final float WORK_PER_STROKE = 0.08f;

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

    public static float bleedTarget(float depth, float toolMult) {
        float b;
        if (depth < CutLayer.SKIN.end)        b = lerp(0.5f,  4f,  CutLayer.SKIN.frac(depth));
        else if (depth < CutLayer.FAT.end)    b = lerp(4f,   10f,  CutLayer.FAT.frac(depth));
        else if (depth < CutLayer.MUSCLE.end) b = lerp(10f,  40f,  CutLayer.MUSCLE.frac(depth));
        else if (depth < CutLayer.BONE.end)   b = lerp(40f,  32f,  CutLayer.BONE.frac(depth));
        else                                  b = lerp(32f,  65f,  CutLayer.EXIT.frac(depth));
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

    public static float pain(float depth, float work, float toolMult) {
        float layer = CutLayer.at(depth) == CutLayer.BONE ? 1.8f : 1.0f;
        return work * (26f + 50f * clamp01(depth)) * layer * toolMult;
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
