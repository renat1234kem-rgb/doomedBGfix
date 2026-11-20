package com.deceasedcraft.doomedtraits.client;

import com.deceasedcraft.doomedtraits.AddTraits;
import net.mattlives.doomedmatu.client.ClientDoomedData;
import net.minecraft.client.Minecraft;

/** Клиентская проверка «Сосредоточенного» по флагу в маске черт Doomed. */
public final class ClientFocus {
    private ClientFocus() {
    }

    public static boolean focused() {
        if (!Minecraft.getInstance().isSameThread()) {
            return false; // серверные потоки (интегрированный сервер) этим не управляем
        }
        int traits = ClientDoomedData.getTraits();
        return (traits & AddTraits.COST_MARKER) != 0 && (traits & AddTraits.FOCUSED_FLAG) != 0;
    }
}
