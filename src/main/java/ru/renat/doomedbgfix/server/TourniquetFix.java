package ru.renat.doomedbgfix.server;

import net.mattlives.doomedmatu.body.BodyPart;
import net.mattlives.doomedmatu.body.LimbData;
import net.mattlives.doomedmatu.capability.DoomedData;
import net.mattlives.doomedmatu.registry.DoomedItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import ru.renat.doomedbgfix.config.BGConfig;

import java.util.Arrays;
import java.util.List;

/**
 * Фикс: в оригинале makeInert() обнуляет tourniquet, и жгут исчезает навсегда.
 * Возвращаем его с тем же износом, что и при ручном снятии (TreatmentService.removeTourniquet).
 */
public final class TourniquetFix {
    private static final BodyPart[] HEAD_CHAIN = {
            BodyPart.NECK, BodyPart.HEAD, BodyPart.EYE_LEFT, BodyPart.EYE_RIGHT
    };

    private TourniquetFix() {}

    public static void releaseChain(ServerPlayer patient, DoomedData data, BodyPart boundary) {
        if (!BGConfig.dropTourniquet() || patient == null || data == null || boundary == null) return;
        release(patient, data, boundary.distalIncludingSelf());
    }

    public static void releaseHead(ServerPlayer patient, DoomedData data) {
        if (!BGConfig.dropTourniquet() || patient == null || data == null) return;
        release(patient, data, Arrays.asList(HEAD_CHAIN));
    }

    private static void release(ServerPlayer patient, DoomedData data, List<BodyPart> parts) {
        for (BodyPart part : parts) {
            LimbData limb = data.getLimb(part);
            if (limb == null || limb.isDismembered() || !limb.isTourniquet()) continue;
            float condition = limb.getTourniquetCondition();
            limb.setTourniquet(false);
            limb.setTourniquetTime(0.0f);
            limb.setTourniquetCondition(0.0f);
            if (condition <= 0.0f) continue;
            ItemStack strap = new ItemStack(DoomedItems.TOURNIQUET.get());
            int max = strap.getMaxDamage();
            int damage = Math.round((1.0f - condition) * (float) max);
            if (max > 0 && damage >= max) damage = max - 1;
            strap.setDamageValue(Math.max(0, damage));
            if (!patient.getInventory().add(strap)) {
                patient.drop(strap, false);
            }
        }
    }
}
