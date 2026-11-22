package ru.renat.doomedbgfix.eye;

import net.mattlives.doomedmatu.body.BodyPart;
import net.mattlives.doomedmatu.body.DoomedData;
import net.mattlives.doomedmatu.body.LimbData;
import net.mattlives.doomedmatu.body.WoundLedgerService;
import net.mattlives.doomedmatu.body.WoundType;
import net.mattlives.doomedmatu.body.ForeignBodyKind;
import net.mattlives.doomedmatu.body.VesselInvolvement;
import net.mattlives.doomedmatu.body.WoundClosure;
import net.mattlives.doomedmatu.body.WoundSurface;
import net.mattlives.doomedmatu.capability.DoomedDataUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public class EyeAmputationHandler {

    /**
     * Выполняет ампутацию глаза.
     * Вызывается при завершении мини-игры.
     */
    public static boolean perform(ServerPlayer player, BodyPart eyePart) {
        DoomedData data = DoomedDataUtil.get((Player) player).orElse(null);
        if (data == null || !data.isActive()) return false;
        
        LimbData limb = data.getLimb(eyePart);
        if (limb == null || limb.isDismembered()) return false;

        // Создаём рану на месте глаза
        WoundLedgerService.create(data, eyePart, WoundType.AMPUTATION_STUMP,
            player.getUUID().getLeastSignificantBits(),
            95f,   // skinLoss (почти вся кожа глаза)
            85f,   // muscleLoss (мышцы глаза)
            1.0f,  // depth (полная ампутация)
            15f,   // bleeding (кровотечение из глаза)
            0f,    // internalBleeding
            10f,   // contamination
            ForeignBodyKind.NONE, 0, VesselInvolvement.MINOR);

        // Помечаем глаз как ампутированный
        limb.setDismembered(true);
        limb.setSkinHealth(0f);
        limb.setMuscleHealth(0f);
        limb.setBleed(15f);
        limb.setPain(100f);

        DoomedDataUtil.sync(player);
        return true;
    }
}