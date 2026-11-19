package ru.renat.doomedbgfix.server;

import net.mattlives.doomedmatu.body.AmputationState;
import net.mattlives.doomedmatu.body.BodyPart;
import net.mattlives.doomedmatu.capability.DoomedData;
import net.mattlives.doomedmatu.capability.DoomedDataUtil;
import net.minecraft.server.level.ServerPlayer;
import ru.renat.doomedbgfix.config.BGConfig;

/**
 * Ванильная фантомка (AmputationHandler.tick):
 *   phantomPain = 18 + susceptibility * (0.35..0.65), susceptibility = 52..88  ->  36..70 боли,
 *   эпизод 12..40 с, и он входит в maxPain() всего тела.
 * Здесь только идемпотентные потолки: ничего не умножается каждый тик, поэтому
 * значение не схлопывается в ноль и не конфликтует с логикой самого мода.
 * Работает для любой ампутации, не только для нашей мини-игры.
 */
public final class PhantomNerf {

    private PhantomNerf() {}

    public static void tick(ServerPlayer player, DoomedData data) {
        if (!BGConfig.phantomNerf() || data == null || !data.isActive()) return;

        float susCap = BGConfig.phantomSusceptibilityCap();
        float painCap = BGConfig.phantomPainCap();
        int maxEpisode = BGConfig.phantomEpisodeSeconds() * 20;
        boolean changed = false;

        for (BodyPart part : BodyPart.VALUES) {
            AmputationState state = data.getAmputation(part);
            if (state == null || !state.active()) continue;

            if (state.phantomSusceptibility() > susCap) {
                state.setPhantomSusceptibility(susCap);
                changed = true;
            }
            if (state.phantomPain() > painCap) {
                state.setPhantomPain(painCap);
                changed = true;
            }
            if (state.phantomEpisodeTicks() > maxEpisode) {
                state.setPhantomEpisodeTicks(maxEpisode);
                changed = true;
            }
        }

        if (changed) DoomedDataUtil.sync(player);
    }
}
