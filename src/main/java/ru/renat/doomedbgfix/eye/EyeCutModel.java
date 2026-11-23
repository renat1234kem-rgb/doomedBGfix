package ru.renat.doomedbgfix.eye;

import java.util.Arrays;

/**
 * Общая для клиента и сервера модель мини-игры глаза (удаление и установка). Без классов Minecraft.
 *
 * 13 сегментов, у каждого прогресс 0..1:
 *   0..7   - ободок глаза (конъюнктива): 8 дуг, их нужно пройти по кругу;
 *   8..11  - четыре глазодвигательные мышцы (водить поперёк);
 *   12     - зрительный нерв.
 *
 * Порядок этапов зависит от режима:
 *   MODE_CUT     - ободок, мышцы, нерв (вырезаем снаружи внутрь);
 *   MODE_INSTALL - нерв, мышцы, ободок (устанавливаем изнутри наружу).
 * Этап недоступен, пока не закончен предыдущий.
 */
public final class EyeCutModel {
    private EyeCutModel() {}

    public static final int MODE_CUT = 0;
    public static final int MODE_INSTALL = 1;

    public static final int GROUP_RIM = 0;
    public static final int GROUP_MUSCLE = 1;
    public static final int GROUP_NERVE = 2;

    public static final int STAGES = 3;
    public static final int RIM = 8;
    public static final int MUSCLES = 4;
    public static final int SEGMENTS = RIM + MUSCLES + 1;

    /** Сколько единиц здоровья мышц глаза съедает полностью выполненная операция (мышцы не падают ниже 10). */
    public static final float MUSCLE_DAMAGE_K = 90f;

    private static final int[] GROUP_FIRST = {0, RIM, RIM + MUSCLES};
    private static final int[] GROUP_COUNT = {RIM, MUSCLES, 1};
    private static final int[][] ORDER = {
            {GROUP_RIM, GROUP_MUSCLE, GROUP_NERVE},
            {GROUP_NERVE, GROUP_MUSCLE, GROUP_RIM}
    };
    /** Вес этапа по порядку прохождения (одинаков для обоих режимов). Сумма = 1. */
    private static final float[] WEIGHT = {0.30f, 0.40f, 0.30f};

    public static int normMode(int mode) {
        return mode == MODE_INSTALL ? MODE_INSTALL : MODE_CUT;
    }

    public static int groupOf(int index) {
        if (index < 0 || index >= SEGMENTS) return -1;
        if (index < RIM) return GROUP_RIM;
        if (index < RIM + MUSCLES) return GROUP_MUSCLE;
        return GROUP_NERVE;
    }

    /** Какая группа сегментов обрабатывается на этапе stage в режиме mode. */
    public static int groupAt(int mode, int stage) {
        return ORDER[normMode(mode)][stage];
    }

    /** Номер этапа, на котором работает сегмент index, или -1. */
    public static int stageOf(int mode, int index) {
        int group = groupOf(index);
        if (group < 0) return -1;
        for (int s = 0; s < STAGES; s++) {
            if (groupAt(mode, s) == group) return s;
        }
        return -1;
    }

    public static int firstOf(int mode, int stage) { return GROUP_FIRST[groupAt(mode, stage)]; }
    public static int countOf(int mode, int stage) { return GROUP_COUNT[groupAt(mode, stage)]; }

    public static boolean stageDone(float[] seg, int mode, int stage) {
        int first = firstOf(mode, stage);
        int count = countOf(mode, stage);
        for (int i = first; i < first + count; i++) {
            if (seg[i] < 0.999f) return false;
        }
        return true;
    }

    /** Первый незавершённый этап; STAGES, если всё готово. */
    public static int currentStage(float[] seg, int mode) {
        for (int s = 0; s < STAGES; s++) {
            if (!stageDone(seg, mode, s)) return s;
        }
        return STAGES;
    }

    public static float stageProgress(float[] seg, int mode, int stage) {
        int first = firstOf(mode, stage);
        int count = countOf(mode, stage);
        float sum = 0f;
        for (int i = first; i < first + count; i++) sum += clamp01(seg[i]);
        return sum / count;
    }

    public static float total(float[] seg, int mode) {
        float t = 0f;
        for (int s = 0; s < STAGES; s++) t += WEIGHT[s] * stageProgress(seg, mode, s);
        return clamp01(t);
    }

    /** Раскладывает общий прогресс total по этапам и сегментам по порядку (обратная операция к total). */
    public static void fromTotal(float total, int mode, float[] out) {
        Arrays.fill(out, 0f);
        float remaining = clamp01(total);
        for (int s = 0; s < STAGES; s++) {
            float frac = clamp01(remaining / WEIGHT[s]);
            remaining = Math.max(0f, remaining - WEIGHT[s]);
            int first = firstOf(mode, s);
            int count = countOf(mode, s);
            for (int k = 0; k < count; k++) {
                out[first + k] = clamp01(frac * count - k);
            }
        }
    }

    /** Общий прогресс, который соответствует текущему здоровью мышц глаза (источник истины для сохранения прогресса). */
    public static float impliedFromMuscle(float muscleHealth) {
        return clamp01((100f - muscleHealth) / MUSCLE_DAMAGE_K);
    }

    /** Здоровье мышц, которое соответствует общему прогрессу. */
    public static float muscleForTotal(float total) {
        return 100f - MUSCLE_DAMAGE_K * clamp01(total);
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }
}
