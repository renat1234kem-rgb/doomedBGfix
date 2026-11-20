package ru.renat.doomedbgfix.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import ru.renat.doomedbgfix.bag.BagSweeper;
import ru.renat.doomedbgfix.bandage.BandageRework;

public final class CutTicker {

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (event.player instanceof ServerPlayer sp) {
            CutSessions.tick(sp);
            PhantomNerf.tick(sp, CutBridge.data(sp));
            AmputationAftercare.tick(sp, CutBridge.data(sp));
            BandageRework.tick(sp, CutBridge.data(sp));
            BagSweeper.tick(sp);
        }
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CutSessions.forget(event.getEntity().getUUID());
    }
}