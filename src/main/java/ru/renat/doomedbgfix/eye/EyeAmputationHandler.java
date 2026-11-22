package ru.renat.doomedbgfix.eye;

import net.mattlives.doomedmatu.body.BodyPart;
import net.mattlives.doomedmatu.body.EyeFunctionService;
import net.mattlives.doomedmatu.capability.DoomedData;
import net.mattlives.doomedmatu.capability.DoomedDataUtil;
import net.minecraft.server.level.ServerPlayer;

/**
 * Ампутация глаза. Вызывается при завершении мини-игры (когда она появится).
 *
 * Doomed уже умеет удалять глаз: DoomedData.removeEye(BodyPart) делает limb.reset(), ставит
 * dismembered=true (так Doomed понимает, что глаза нет), добавляет боль/кровотечение в голову
 * и травму. Поэтому свою рану создавать не нужно, достаточно вызвать штатный метод.
 */
public final class EyeAmputationHandler {

    private EyeAmputationHandler() {}

    public static boolean perform(ServerPlayer player, BodyPart eyePart) {
        if (!EyeFunctionService.isEye(eyePart)) return false;

        DoomedData data = DoomedDataUtil.get(player).orElse(null);
        if (data == null || !data.isActive()) return false;

        // false, если глаза уже нет. Имплант тоже снимается: removeEye -> limb.reset() обнуляет протез.
        if (!data.removeEye(eyePart)) return false;

        DoomedDataUtil.sync(player);
        return true;
    }
}
