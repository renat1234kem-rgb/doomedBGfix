package ru.renat.doomedbgfix.eye;

import net.mattlives.doomedmatu.body.BodyPart;
import net.mattlives.doomedmatu.body.EyeFunctionService;
import net.mattlives.doomedmatu.body.LimbData;
import net.mattlives.doomedmatu.body.ProstheticTier;
import net.mattlives.doomedmatu.capability.DoomedData;
import net.mattlives.doomedmatu.capability.DoomedDataUtil;
import net.minecraft.server.level.ServerPlayer;

/**
 * Установка глазного импланта.
 *
 * У LimbData нет своего NBT-тега, поэтому признак "тут стоит кибер-глаз" хранится в штатном
 * поле prostheticTier того же LimbData. Оно сохраняется в NBT игрока и само синхронизируется
 * на клиент (ClientDoomedData.hasLimbProsthetic), отдельный пакет не нужен.
 * Глаз при этом НЕ dismembered, то есть Doomed считает его обычным рабочим глазом.
 * При повторном удалении глаза (removeEye -> limb.reset()) метка сбрасывается сама.
 */
public final class EyeImplantHandler {

    /** Какой tier считать "кибер-глазом". Клиентский миксин смотрит на него же. */
    public static final ProstheticTier IMPLANT_TIER = ProstheticTier.MECHANICAL;

    private EyeImplantHandler() {}

    /** Первый отсутствующий глаз (левый приоритетнее) или null. */
    public static BodyPart findAbsentEye(DoomedData data) {
        if (EyeFunctionService.isAbsent(data, BodyPart.EYE_LEFT)) return BodyPart.EYE_LEFT;
        if (EyeFunctionService.isAbsent(data, BodyPart.EYE_RIGHT)) return BodyPart.EYE_RIGHT;
        return null;
    }

    public static boolean isImplanted(DoomedData data, BodyPart eye) {
        if (data == null || !EyeFunctionService.isEye(eye)) return false;
        LimbData limb = data.getLimb(eye);
        return !limb.isDismembered() && limb.getProstheticTier() == IMPLANT_TIER;
    }

    /** Ставит имплант на место ампутированного глаза. false, если глаз на месте. */
    public static boolean install(ServerPlayer player, BodyPart eyePart) {
        DoomedData data = DoomedDataUtil.get(player).orElse(null);
        if (data == null || !data.isActive()) return false;
        if (!EyeFunctionService.isAbsent(data, eyePart)) return false;

        LimbData limb = data.getLimb(eyePart);
        limb.reset(); // dismembered=false, чистим раны, жгут, инфекцию и т.д.
        limb.setSkinHealth(60f);
        limb.setMuscleHealth(60f);
        limb.setPain(15f);
        limb.setProstheticTier(IMPLANT_TIER);
        limb.setProstheticCondition(100f);

        DoomedDataUtil.sync(player);
        return true;
    }
}
