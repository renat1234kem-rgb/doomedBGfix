package ru.renat.doomedbgfix.eye;

/**
 * Общая для клиента и сервера модель мини-игры удаления глаза. Без классов Minecraft.
 *
 * 13 сегментов, у каждого прогресс 0..1:
 *   0..7   - конъюнктива: 8 дуг по ободку глаза (идти по кругу);
 *   8..11  - четыре глазодвигательные мышцы (пилить поперёк);
 *   12     - зрительный нерв (последний рез).
 * Этапы идут строго по порядку: пока не закончен текущий, следующий недоступен.
 */
public final class EyeCutModel {
    private EyeCutModel() {}

    public static final int STAGE_RIM = 0;
    public static final int STAGE_MUSCLE = 1;
    public static final int STAGE_NERVE = 2;
    public static final int STAGES = 3;

    public static final int RIM = 8;
    public static final int MUSCLES = 4;
    public static final int SEGMENTS = RIM + MUSCLES + 1;

    private static final int[] FIRST = {0, RIM, RIM + MUSCLES};
    private static final int[] COUNT = {RIM, MUSCLES, 1};

    /** Доля каждого этапа в общем проценте. Сумма = 1. */
    private static final float[] WEIGHT = {0.30f, 0.40f, 0.30f};

    public static int stageOf(int index) {
        if (index < 0 || index >= SEGMENTS) return -1;
        if (index < RIM) return STAGE_RIM;
        if (index < RIM + MUSCLES) return STAGE_MUSCLE;
        return STAGE_NERVE;
    }

    public static int first(int stage) { return FIRST[stage]; }
    public static int count(int stage) { return COUNT[stage]; }

    public static boolean stageDone(float[] seg, int stage) {
        for (int i = FIRST[stage]; i < FIRST[stage] + COUNT[stage]; i++) {
            if (seg[i] < 0.999f) return false;
        }
        return true;
    }

    /** Первый незавершённый этап, STAGES - если всё готово. */
    public static int currentStage(float[] seg) {
        for (int s = 0; s < STAGES; s++) {
            if (!stageDone(seg, s)) return s;
        }
        return STAGES;
    }

    public static float stageProgress(float[] seg, int stage) {
        float sum = 0f;
        for (int i = FIRST[stage]; i < FIRST[stage] + COUNT[stage]; i++) sum += Math.min(1f, Math.max(0f, seg[i]));
        return sum / COUNT[stage];
    }

    public static float total(float[] seg) {
        float t = 0f;
        for (int s = 0; s < STAGES; s++) t += WEIGHT[s] * stageProgress(seg, s);
        return Math.min(1f, t);
    }
}
