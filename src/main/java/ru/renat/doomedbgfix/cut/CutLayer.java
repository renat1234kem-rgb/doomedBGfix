package ru.renat.doomedbgfix.cut;

import java.util.Locale;

/** Границы кости и её сопротивление совпадают с оригиналом (0.34 / 0.66 / 4.5). */
public enum CutLayer {
    SKIN  (0.00f, 0.10f, 1.00f),
    FAT   (0.10f, 0.20f, 1.25f),
    MUSCLE(0.20f, 0.34f, 1.80f),
    BONE  (0.34f, 0.66f, 4.50f),
    EXIT  (0.66f, 1.00f, 1.00f);

    public final float start;
    public final float end;
    public final float resistance;

    CutLayer(float start, float end, float resistance) {
        this.start = start;
        this.end = end;
        this.resistance = resistance;
    }

    public float span() { return end - start; }
    public float frac(float depth) { return clamp01((depth - start) / span()); }
    public String key() { return "doomedbgfix.cut.layer." + name().toLowerCase(Locale.ROOT); }

    public static CutLayer at(float depth) {
        for (CutLayer l : values()) {
            if (depth < l.end) return l;
        }
        return EXIT;
    }

    public static float clamp01(float v) {
        if (Float.isNaN(v)) return 0f;
        return v < 0f ? 0f : Math.min(v, 1f);
    }
}
