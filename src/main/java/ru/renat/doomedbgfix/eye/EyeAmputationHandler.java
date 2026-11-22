package ru.renat.doomedbgfix.eye;

import net.mattlives.doomedmatu.body.BodyPart;
import net.mattlives.doomedmatu.body.EyeFunctionService;
import net.mattlives.doomedmatu.capability.DoomedData;
import net.mattlives.doomedmatu.capability.DoomedDataUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import ru.renat.doomedbgfix.registry.EyeItems;

/**
 * Финальный шаг удаления глаза (вызывается, когда мини-игра дошла до конца).
 *
 * DoomedData.removeEye(BodyPart) уже делает limb.reset(), dismembered=true,
 * боль головы 100, кровь головы +20 и травму 35. Сверху добавляем шок и травму,
 * чтобы это было заметно больнее обычной ампутации конечности.
 */
public final class EyeAmputationHandler {

    private static final float EXTRA_TRAUMA = 15f;
    private static final float MIN_SHOCK = 45f;
    private static final float MIN_ADRENALINE = 60f;

    private EyeAmputationHandler() {}

    public static boolean perform(ServerPlayer player, BodyPart eyePart) {
        if (!EyeFunctionService.isEye(eyePart)) return false;

        DoomedData data = DoomedDataUtil.get(player).orElse(null);
        if (data == null || !data.isActive()) return false;

        boolean hadImplant = EyeImplantHandler.isImplanted(data, eyePart);

        // false, если глаза уже нет.
        if (!data.removeEye(eyePart)) return false;

        data.addTrauma(EXTRA_TRAUMA);
        data.setShock(Math.max(data.getShock(), MIN_SHOCK));
        data.setAdrenaline(Math.max(data.getAdrenaline(), MIN_ADRENALINE));

        // Вырезали кибер-глаз: имплант возвращается игроку (removeEye его в данных обнулил).
        if (hadImplant) {
            ItemStack implant = new ItemStack(EyeItems.EYE_IMPLANT.get());
            if (!player.getInventory().add(implant)) player.drop(implant, false);
        }

        DoomedDataUtil.sync(player);
        return true;
    }
}
