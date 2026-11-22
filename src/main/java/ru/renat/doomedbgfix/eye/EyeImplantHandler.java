package ru.renat.doomedbgfix.eye;

import net.mattlives.doomedmatu.body.BodyPart;
import net.mattlives.doomedmatu.body.DoomedData;
import net.mattlives.doomedmatu.body.LimbData;
import net.mattlives.doomedmatu.capability.DoomedDataUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * Устанавливает глазной имплант. Вызывается из EyeImplantItem.use().
 */
public final class EyeImplantHandler {

    private EyeImplantHandler() {}

    public static boolean install(ServerPlayer player, BodyPart eyePart) {
        DoomedData data = DoomedDataUtil.get((Player) player).orElse(null);
        if (data == null || !data.isActive()) return false;

        LimbData limb = data.getLimb(eyePart);
        if (limb == null || !limb.isDismembered()) return false;

        limb.setDismembered(false);
        limb.setSkinHealth(60f);
        limb.setMuscleHealth(60f);
        limb.setBleed(0f);
        limb.setPain(15f);
        limb.getOrCreateTag().putBoolean("EyeImplant", true);

        DoomedDataUtil.sync(player);
        return true;
    }
}