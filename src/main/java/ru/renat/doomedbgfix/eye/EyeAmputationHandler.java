package ru.renat.doomedbgfix.eye;

import net.mattlives.doomedmatu.body.BodyPart;
import net.mattlives.doomedmatu.body.EyeFunctionService;
import net.mattlives.doomedmatu.body.LimbData;
import net.mattlives.doomedmatu.capability.DoomedData;
import net.mattlives.doomedmatu.capability.DoomedDataUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import ru.renat.doomedbgfix.registry.EyeItems;

/**
 * Финальный шаг удаления глаза (мини-игра дошла до конца).
 *
 * Почему кровь переносится на голову вручную. makeAbsent() делает limb.reset() - накопленное
 * кровотечение самого глаза пропадает. BleedHandler при этом каждый тик обрезает кровь лимба до
 * maxBleedForSkin() = 100 * (1 - skin/100), поэтому на целой коже головы любое кровотечение мгновенно
 * "схлопывается" в 0. Так же поступает Doomed при потере глаза от отчаяния: сначала портит кожу головы,
 * потом добавляет кровь. Делаем то же самое и переносим кровь глаза.
 */
public final class EyeAmputationHandler {

    private static final float SOCKET_BLEED = 25f;
    private static final float HEAD_MUSCLE_LOSS = 12f;
    private static final float EXTRA_TRAUMA = 15f;
    private static final float MIN_SHOCK = 45f;
    private static final float MIN_ADRENALINE = 60f;

    private EyeAmputationHandler() {}

    /**
     * @param patient   у кого вырезают глаз
     * @param recipient кому отдать вырезанный кибер-глаз (оператор), null - самому пациенту
     */
    public static boolean perform(ServerPlayer patient, BodyPart eyePart, ServerPlayer recipient) {
        if (!EyeFunctionService.isEye(eyePart)) return false;

        DoomedData data = DoomedDataUtil.get(patient).orElse(null);
        if (data == null || !data.isActive()) return false;
        if (EyeFunctionService.isAbsent(data, eyePart)) return false;

        boolean hadImplant = EyeImplantHandler.isImplanted(data, eyePart);
        float transferred = Math.min(LimbData.MAX_BLEED, data.getLimb(eyePart).getBleed() + SOCKET_BLEED);

        LimbData head = data.getLimb(BodyPart.HEAD);
        head.setSkinHealth(Math.min(head.getSkinHealth(), 100f - transferred));
        head.setMuscleHealth(head.getMuscleHealth() - HEAD_MUSCLE_LOSS);

        // removeEye: limb.reset() + dismembered, боль головы 100, кровь головы +20, травма 35.
        if (!data.removeEye(eyePart)) return false;

        head.setBleed(head.getBleed() + transferred);
        data.addTrauma(EXTRA_TRAUMA);
        data.setShock(Math.max(data.getShock(), MIN_SHOCK));
        data.setAdrenaline(Math.max(data.getAdrenaline(), MIN_ADRENALINE));

        // Вырезали кибер-глаз: имплант возвращается (removeEye метку в данных обнулил).
        if (hadImplant) {
            ItemStack implant = new ItemStack(EyeItems.EYE_IMPLANT.get());
            ServerPlayer to = recipient != null ? recipient : patient;
            if (!to.getInventory().add(implant)) to.drop(implant, false);
        }

        DoomedDataUtil.sync(patient);
        return true;
    }
}
