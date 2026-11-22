package ru.renat.doomedbgfix.eye;

import net.mattlives.doomedmatu.body.BodyPart;
import net.mattlives.doomedmatu.body.PatientSnapshot;
import net.mattlives.doomedmatu.client.ClientSfx;
import net.mattlives.doomedmatu.client.screen.DoomedScreen;
import net.mattlives.doomedmatu.client.screen.HealthPanelBackdrop;
import net.mattlives.doomedmatu.client.screen.PainHand;
import net.mattlives.doomedmatu.registry.DoomedSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Мини-игра ампутации глаза.
 * Отличается от ампутации конечностей:
 * - Меньше масштаб (глаз ~2-3 см)
 * - Скальпель вместо пилы
 * - Быстрее (~15-20 секунд)
 * - Другая анатомия: конъюнктива → мышцы → зрительный нерв
 */
public class EyeAmputationScreen extends DoomedScreen {

    private static final float CX = 640f;
    private static final float CY = 352f;
    private static final float R = 80f;  // Меньше радиус (глаз маленький)
    private static final long SEND_INTERVAL_MS = 100L;

    private final BodyPart part;  // EYE_LEFT или EYE_RIGHT
    private final int patientId;
    private final long token;
    
    private PatientSnapshot snapshot;
    private long snapshotAtMs;
    private float depth;
    private float shown;
    private boolean holding;
    private boolean stopped;
    private boolean severed;
    private float lastX;
    private int lastDir;
    private int reversals;
    private float accum;
    private long lastSendMs;
    private long lastSoundMs;

    public EyeAmputationScreen(BodyPart part, int patientId, PatientSnapshot snapshot, long token) {
        super(Component.translatable("doomedbgfix.eye.amputation.title"));
        this.part = part;
        this.patientId = patientId;
        this.snapshot = snapshot;
        this.snapshotAtMs = System.currentTimeMillis();
        this.token = token;
    }

    @Override protected int virtualW() { return 1280; }
    @Override protected int virtualH() { return 720; }
    @Override protected PainHand.HandPose handPose() { return PainHand.HandPose.GRASP; }

    @Override
    protected void renderDoomed(GuiGraphics gg, float hx, float hy, float partialTick) {
        // Рендер мини-игры (упрощённо)
        if (patientId == -1 || snapshot == null) {
            HealthPanelBackdrop.render(gg, this.font);
        } else {
            HealthPanelBackdrop.render(gg, this.font, snapshot, patientId, snapshotAtMs);
        }

        gg.drawCenteredString(this.font, 
            Component.translatable("doomedbgfix.eye.amputation.title").getString() + 
            " - " + part.displayName(), (int) CX, 96, 0xFFFFFF);

        // Рисуем глаз (круг)
        for (int y = (int)(CY - R); y < CY + R; y++) {
            float off = (y + 0.5f - CY) / R;
            int half = (int)(R * Math.sqrt(Math.max(0f, 1f - off * off)));
            if (half <= 0) continue;
            
            // Цвет глаза (белок + радужка)
            int color = 0xFFFFFFFF; // Белый по умолчанию
            float distFromCenter = Math.abs(y - CY) / R;
            if (distFromCenter < 0.3f) color = 0xFF4488FF; // Синяя радужка
            if (distFromCenter < 0.1f) color = 0xFF000000; // Чёрный зрачок
            
            gg.fill((int)CX - half, y, (int)CX + half, y + 1, color);
        }

        // Линия разреза
        float cutY = CY - R + 2f * R * shown;
        gg.fill((int)(CX - R), (int)cutY, (int)(CX + R), (int)cutY + 2, 0xFFFF0000);

        // HUD
        gg.drawCenteredString(this.font, 
            Math.round(shown * 100f) + "%", (int) CX, (int)(CY + R + 30), 0xFFDDDDDD);
    }

    // ... остальная логика ввода (аналогична CutScreen)
}