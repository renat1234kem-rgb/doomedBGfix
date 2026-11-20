package com.deceasedcraft.doomedtraits;

import net.mattlives.doomedmatu.body.Skills;

/**
 * Сдвиг уровня навыка на N уровней вниз (отрицательное N = вверх).
 * Опыт сдвигается с сохранением доли прогресса внутри уровня.
 */
public final class SkillShift {
    private SkillShift() {
    }

    public static float level(float stored, int down) {
        return Math.max(0.0F, Math.min(100.0F, stored - down));
    }

    public static float exp(float storedExp, int down) {
        int lvl = Skills.levelForExp(storedExp);
        int eff = Math.max(0, Math.min(100, lvl - down));
        if (eff == lvl) {
            return storedExp;
        }
        float lo = Skills.xpForLevel(lvl);
        float hi = Skills.xpForLevel(Math.min(100, lvl + 1));
        float frac = hi > lo ? (storedExp - lo) / (hi - lo) : 0.0F;
        frac = Math.max(0.0F, Math.min(1.0F, frac));
        float elo = Skills.xpForLevel(eff);
        float ehi = Skills.xpForLevel(Math.min(100, eff + 1));
        return elo + frac * (ehi - elo);
    }
}
