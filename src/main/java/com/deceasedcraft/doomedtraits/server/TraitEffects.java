package com.deceasedcraft.doomedtraits.server;

import com.deceasedcraft.doomedtraits.AddTrait;
import com.deceasedcraft.doomedtraits.AddTraits;
import java.util.List;
import net.mattlives.doomedmatu.capability.DoomedData;
import net.mattlives.doomedmatu.capability.DoomedDataUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

/** Небольшие эффекты черт, которые вызываются из нескольких мест. */
public final class TraitEffects {
    public static final double WITNESS_RADIUS = 6.0;

    private TraitEffects() {
    }

    /** Каннибал ест человечину: +4 к настроению, свидетели без этой черты -3. */
    public static void cannibalMeal(ServerPlayer sp, DoomedData d) {
        d.addHappiness(4.0F);
        if (!(sp.level() instanceof ServerLevel level)) {
            return;
        }
        double maxSq = WITNESS_RADIUS * WITNESS_RADIUS;
        AABB box = sp.getBoundingBox().inflate(WITNESS_RADIUS);
        List<ServerPlayer> others = level.getEntitiesOfClass(
            ServerPlayer.class, box, p -> p != sp && p.isAlive() && !p.isSpectator() && p.distanceToSqr(sp) <= maxSq
        );
        for (ServerPlayer other : others) {
            DoomedDataUtil.get(other).ifPresent(od -> {
                if (od.isActive() && !AddTraits.active(od, AddTrait.CANNIBAL)) {
                    od.addHappiness(-3.0F);
                }
            });
        }
    }
}
