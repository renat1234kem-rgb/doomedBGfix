package com.deceasedcraft.doomedtraits.server;

import com.deceasedcraft.doomedtraits.AddTrait;
import com.deceasedcraft.doomedtraits.AddTraits;
import net.mattlives.doomedmatu.capability.DoomedData;

/**
 * Стартовый недовес анорексии.
 * weightOffset в Doomed - отклонение веса в кг. Штрафы заживления и иммунитета начинаются
 * ниже -10, движение страдает за -40, а аритмия сердца начинается за -60.
 * -30 даёт заметный, но безопасный недовес.
 */
public final class AnorexiaStart {
    public static final float START_WEIGHT_OFFSET = -30.0F;

    private AnorexiaStart() {
    }

    public static void apply(DoomedData d) {
        if (AddTraits.active(d, AddTrait.ANOREXIA)) {
            d.setWeightOffset(Math.min(d.getWeightOffset(), START_WEIGHT_OFFSET));
        }
    }
}
