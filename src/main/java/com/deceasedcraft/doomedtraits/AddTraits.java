package com.deceasedcraft.doomedtraits;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.mattlives.doomedmatu.body.DoomedMode;
import net.mattlives.doomedmatu.body.Trait;
import net.mattlives.doomedmatu.capability.DoomedData;
import net.mattlives.doomedmatu.config.DoomedConfig;

/**
 * Хранение наших черт.
 *
 * Свободных битов в маске Doomed (22 из 31 заняты) на 15 черт не хватает, поэтому:
 *  - сам набор черт лежит в собственной маске mask2 (хранится внутри DoomedData через миксин);
 *  - в маску Doomed кладётся только суммарная цена наших черт (биты 23..30):
 *    бит 30 - «есть наши черты», бит 29 - знак, биты 23..28 - модуль.
 *    Так Doomed сам считает баланс и валидность везде, где он это делает.
 */
public final class AddTraits {
    public static final int ALL_MASK = (1 << AddTrait.values().length) - 1;
    public static final int COST_MARKER = 1 << 30;
    public static final int COST_SIGN = 1 << 29;
    public static final int COST_SHIFT = 23;
    public static final int COST_BITS = 0x7F800000;
    /** Бит 22 маски Doomed: у игрока есть «Сосредоточенный». Doomed сам синхронизирует маску на клиент. */
    public static final int FOCUSED_FLAG = 1 << 22;
    public static final int OUR_BITS = COST_BITS | FOCUSED_FLAG;

    /** Игроки, рядом с которыми (6 блоков) стоит Оптимист: слабая версия бонуса. */
    public static final Set<DoomedData> OPTIMIST_AURA = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    /** Выставляется на время FumeExposureHandler.tick: испарения химии. */
    public static final ThreadLocal<Boolean> IN_FUMES = ThreadLocal.withInitial(() -> Boolean.FALSE);

    // Числа черт
    public static final int DEMENTIA_INT_LEVELS = 4;
    public static final float DEMENTIA_XP_MULT = 0.75F;
    public static final float GENIUS_XP_MULT = 2.0F;
    public static final int ANOREXIA_LEVELS = 2;
    public static final float ANOREXIA_XP_MULT = 0.75F;
    public static final int IN_SHAPE_LEVELS = 2;
    public static final float IN_SHAPE_XP_MULT = 1.25F;

    private AddTraits() {
    }

    public static boolean has(int mask2, AddTrait t) {
        return (mask2 & t.bit()) != 0;
    }

    /** Суммарная цена (перки +, недостатки -). */
    public static int cost(int mask2) {
        int sum = 0;
        for (AddTrait t : AddTrait.values()) {
            if (has(mask2, t)) {
                sum += t.cost();
            }
        }
        return sum;
    }

    public static int encodeCost(int mask2) {
        if (mask2 == 0) {
            return 0;
        }
        int c = cost(mask2);
        int mag = Math.min(63, Math.abs(c));
        return COST_MARKER | (c < 0 ? COST_SIGN : 0) | (mag << COST_SHIFT);
    }

    public static int decodeCost(int mask1) {
        if ((mask1 & COST_MARKER) == 0) {
            return 0;
        }
        int mag = (mask1 >> COST_SHIFT) & 0x3F;
        return (mask1 & COST_SIGN) != 0 ? -mag : mag;
    }

    /** Маска Doomed с подставленной ценой наших черт. */
    public static int withCost(int mask1, int mask2) {
        int flag = has(mask2, AddTrait.FOCUSED) ? FOCUSED_FLAG : 0;
        return (mask1 & ~OUR_BITS) | encodeCost(mask2) | flag;
    }

    /** Слабоумие нельзя брать вместе с «Сообразительностью». */
    public static boolean conflicts(int mask2, int mask1) {
        return has(mask2, AddTrait.DEMENTIA) && (mask1 & Trait.BRIGHT.bit()) != 0;
    }

    public static int mask2(DoomedData d) {
        return ((DoomedDataExt) (Object) d).doomedtraitsadd$getMask2();
    }

    /** Действует ли черта: те же условия, что Doomed требует для своих черт. */
    public static boolean active(DoomedData d, AddTrait t) {
        if (d == null || !has(mask2(d), t)) {
            return false;
        }
        if (!d.isTraitsChosen() || d.getMode() != DoomedMode.DEFAULT) {
            return false;
        }
        try {
            if (!DoomedConfig.traitsEnabled()) {
                return false;
            }
        } catch (Throwable ignored) {
            // конфиг ещё не загружен
        }
        return Trait.valid(d.getTraits());
    }

    /** На сколько уровней сила/стойкость ниже нормы (отрицательно = выше). */
    public static int bodyShift(DoomedData d) {
        int shift = 0;
        if (active(d, AddTrait.ANOREXIA)) {
            shift += ANOREXIA_LEVELS;
        }
        if (active(d, AddTrait.IN_SHAPE)) {
            shift -= IN_SHAPE_LEVELS;
        }
        return shift;
    }

    public static float bodyXpMult(DoomedData d) {
        float m = 1.0F;
        if (active(d, AddTrait.ANOREXIA)) {
            m *= ANOREXIA_XP_MULT;
        }
        if (active(d, AddTrait.IN_SHAPE)) {
            m *= IN_SHAPE_XP_MULT;
        }
        return m;
    }

    public static float intXpMult(DoomedData d) {
        float m = 1.0F;
        if (active(d, AddTrait.DEMENTIA)) {
            m *= DEMENTIA_XP_MULT;
        }
        if (active(d, AddTrait.GENIUS)) {
            m *= GENIUS_XP_MULT;
        }
        return m;
    }
}
