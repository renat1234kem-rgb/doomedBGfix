package ru.renat.doomedbgfix.client;

import net.mattlives.doomedmatu.client.screen.SeveredExaminationScreen;
import net.mattlives.doomedmatu.client.screen.StatusPanelScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import ru.renat.doomedbgfix.net.S2CCutStatePacket;
import ru.renat.doomedbgfix.net.S2COpenCutPacket;

public final class CutClient {
    private static CutScreen current;

    private CutClient() {}

    public static void open(S2COpenCutPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        CutScreen screen = new CutScreen(packet);
        current = screen;
        Screen open = mc.screen;
        if (packet.patientId() == -1 && open instanceof StatusPanelScreen panel) {
            panel.openAuthorizedTreatment(screen, packet.forceInteractive());
            return;
        }
        if (packet.patientId() == -1 && open instanceof SeveredExaminationScreen exam) {
            exam.openAuthorizedTreatment(screen, packet.forceInteractive());
            return;
        }
        mc.setScreen(screen);
    }

    public static void accept(S2CCutStatePacket packet) {
        CutScreen screen = current;
        if (screen != null) screen.accept(packet);
    }

    public static void clear(CutScreen screen) {
        if (current == screen) current = null;
    }
}