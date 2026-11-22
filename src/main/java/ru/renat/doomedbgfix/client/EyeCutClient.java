package ru.renat.doomedbgfix.client;

import net.mattlives.doomedmatu.client.screen.SeveredExaminationScreen;
import net.mattlives.doomedmatu.client.screen.StatusPanelScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import ru.renat.doomedbgfix.net.S2CEyeCutStatePacket;
import ru.renat.doomedbgfix.net.S2COpenEyeCutPacket;

public final class EyeCutClient {
    private static EyeCutScreen current;

    private EyeCutClient() {}

    public static void open(S2COpenEyeCutPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        EyeCutScreen screen = new EyeCutScreen(packet);
        current = screen;
        Screen open = mc.screen;
        if (open instanceof StatusPanelScreen panel) {
            panel.openAuthorizedTreatment(screen, packet.forceInteractive());
            return;
        }
        if (open instanceof SeveredExaminationScreen exam) {
            exam.openAuthorizedTreatment(screen, packet.forceInteractive());
            return;
        }
        mc.setScreen(screen);
    }

    public static void accept(S2CEyeCutStatePacket packet) {
        EyeCutScreen screen = current;
        if (screen != null) screen.accept(packet);
    }

    public static void clear(EyeCutScreen screen) {
        if (current == screen) current = null;
    }
}
