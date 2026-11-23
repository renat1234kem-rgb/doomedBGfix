package ru.renat.doomedbgfix.client;

import net.mattlives.doomedmatu.body.BodyPart;
import net.mattlives.doomedmatu.body.PatientSnapshot;
import net.mattlives.doomedmatu.client.ClientSfx;
import net.mattlives.doomedmatu.client.screen.DoomedScreen;
import net.mattlives.doomedmatu.client.screen.HealthPanelBackdrop;
import net.mattlives.doomedmatu.client.screen.PainHand;
import net.mattlives.doomedmatu.registry.DoomedSounds;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import ru.renat.doomedbgfix.DoomedBGFix;
import ru.renat.doomedbgfix.eye.EyeCutModel;
import ru.renat.doomedbgfix.net.BGNetwork;
import ru.renat.doomedbgfix.net.C2SCutStopPacket;
import ru.renat.doomedbgfix.net.C2SEyeCutWorkPacket;
import ru.renat.doomedbgfix.net.LimbView;
import ru.renat.doomedbgfix.net.S2CEyeCutStatePacket;
import ru.renat.doomedbgfix.net.S2COpenEyeCutPacket;

import java.util.Locale;

/**
 * Мини-игры с глазом (на себе и на другом игроке), три этапа в каждой.
 *  Удаление (скальпель): ободок -> четыре мышцы -> зрительный нерв.
 *  Установка (пинцет):   нерв  -> четыре мышцы  -> ободок (швы).
 * Прогресс, боль и кровь считает сервер, здесь только ввод и отрисовка.
 */
public final class EyeCutScreen extends DoomedScreen {

    private static final float CX = 640f;
    private static final float CY = 320f;
    private static final float R = 150f;
    private static final float RING_BAND = 34f;

    private static final float BAR_NEAR = R + 6f;
    private static final float BAR_FAR = R + 130f;
    private static final float BAR_HALF = 24f;
    private static final float HIT_SLACK = 10f;
    private static final float[] MUSCLE_DEG = {-45f, 45f, 135f, 225f};
    private static final float NERVE_DEG = 90f;

    /** Сколько пикселей движения даёт полный прогресс. */
    private static final float RIM_SEG_PX = (float) (R * Math.PI * 2.0 / EyeCutModel.RIM) * 1.1f;
    private static final float MUSCLE_SWEEP_PX = 150f;
    private static final float NERVE_SWEEP_PX = 120f;

    private static final long SEND_INTERVAL_MS = 100L;

    private static final ResourceLocation SCALPEL = new ResourceLocation(DoomedBGFix.MODID, "textures/gui/scalpel.png");
    private static final int SCALPEL_TEX_W = 300;
    private static final int SCALPEL_TEX_H = 60;
    private static final int SCALPEL_W = 170;
    private static final int SCALPEL_H = 34;

    private final BodyPart part;
    private final int patientId;
    private final int mode;
    private final long token;
    private LimbView view;
    private final PatientSnapshot snapshot;
    private final long snapshotAtMs;

    private final float[] server = new float[EyeCutModel.SEGMENTS];
    private final float[] shown = new float[EyeCutModel.SEGMENTS];
    private final float[] pending = new float[EyeCutModel.SEGMENTS];
    private int stage;

    private boolean holding;
    private boolean stopped;
    private boolean finished;
    private float lastX;
    private float lastY;
    private long lastSendMs;
    private long lastSoundMs;

    public EyeCutScreen(S2COpenEyeCutPacket packet) {
        super(Component.translatable(titleKey(EyeCutModel.normMode(packet.mode()))));
        this.part = BodyPart.values()[Mth.clamp(packet.part(), 0, BodyPart.values().length - 1)];
        this.patientId = packet.patientId();
        this.mode = EyeCutModel.normMode(packet.mode());
        this.token = packet.token();
        this.view = packet.view();
        this.snapshot = packet.snapshot();
        this.snapshotAtMs = System.currentTimeMillis();
        System.arraycopy(packet.progress(), 0, server, 0, EyeCutModel.SEGMENTS);
        System.arraycopy(packet.progress(), 0, shown, 0, EyeCutModel.SEGMENTS);
        this.stage = Math.min(EyeCutModel.currentStage(server, mode), EyeCutModel.STAGES - 1);
    }

    @Override protected int virtualW() { return 1280; }
    @Override protected int virtualH() { return 720; }
    @Override protected PainHand.HandPose handPose() {
        return mode == EyeCutModel.MODE_INSTALL ? PainHand.HandPose.TWEEZERS : PainHand.HandPose.GRASP;
    }

    private static String titleKey(int mode) {
        return mode == EyeCutModel.MODE_INSTALL ? "doomedbgfix.eye.install.title" : "doomedbgfix.eye.cut.title";
    }

    private String keyPrefix() {
        return mode == EyeCutModel.MODE_INSTALL ? "doomedbgfix.eye.install." : "doomedbgfix.eye.cut.";
    }

    /** Насколько ткань "цела": при удалении убывает с прогрессом, при установке растёт (прикрепляется). */
    private float intact(float progress) {
        return mode == EyeCutModel.MODE_INSTALL ? progress : 1f - progress;
    }

    @Override
    protected float[] handDrawPos(float vmx, float vmy) {
        if (!holding || mode == EyeCutModel.MODE_INSTALL) return null;
        // Скальпель торчит влево от курсора, рука держит рукоять справа.
        return new float[]{vmx + SCALPEL_W - 30f, vmy + 8f};
    }

    // ---------------------------------------------------------------- input

    @Override
    protected boolean doMouseClicked(double hx, double hy, int button) {
        if (button != 0 || stopped || finished) return false;
        holding = true;
        lastX = (float) hx;
        lastY = (float) hy;
        return true;
    }

    @Override
    protected boolean doMouseDragged(double hx, double hy, int button, double dx, double dy) {
        if (button != 0 || !holding) return false;
        sample((float) hx, (float) hy);
        return true;
    }

    @Override
    protected boolean doMouseReleased(double hx, double hy, int button) {
        if (button != 0) return false;
        holding = false;
        flush();
        return true;
    }

    private void sample(float hx, float hy) {
        if (!holding || stopped || finished) return;
        float prevX = lastX;
        float prevY = lastY;
        lastX = hx;
        lastY = hy;
        float dx = hx - prevX;
        float dy = hy - prevY;
        if (dx * dx + dy * dy < 0.25f) return;

        switch (EyeCutModel.groupAt(mode, stage)) {
            case EyeCutModel.GROUP_RIM -> {
                float r = (float) Math.hypot(hx - CX, hy - CY);
                if (Math.abs(r - R) > RING_BAND) return;
                float a1 = (float) Math.atan2(prevY - CY, prevX - CX);
                float a2 = (float) Math.atan2(hy - CY, hx - CX);
                float da = a2 - a1;
                while (da > Math.PI) da -= (float) (Math.PI * 2.0);
                while (da < -Math.PI) da += (float) (Math.PI * 2.0);
                add(rimIndex(a2), Math.abs(da) * R / RIM_SEG_PX);
            }
            case EyeCutModel.GROUP_MUSCLE -> {
                for (int i = 0; i < EyeCutModel.MUSCLES; i++) {
                    if (inBar(MUSCLE_DEG[i], hx, hy)) {
                        add(EyeCutModel.RIM + i, Math.abs(perp(MUSCLE_DEG[i], dx, dy)) / MUSCLE_SWEEP_PX);
                    }
                }
            }
            default -> {
                if (inBar(NERVE_DEG, hx, hy)) {
                    add(EyeCutModel.RIM + EyeCutModel.MUSCLES, Math.abs(perp(NERVE_DEG, dx, dy)) / NERVE_SWEEP_PX);
                }
            }
        }
        if (System.currentTimeMillis() - lastSendMs >= SEND_INTERVAL_MS) flush();
    }

    private void add(int index, float work) {
        if (index < 0 || index >= EyeCutModel.SEGMENTS || work <= 0f || shown[index] >= 1f) return;
        pending[index] += work;
        shown[index] = Math.min(1f, shown[index] + work * 0.85f);
    }

    private void flush() {
        long now = System.currentTimeMillis();
        for (int i = 0; i < EyeCutModel.SEGMENTS; i++) {
            if (pending[i] > 0.002f && !stopped && !finished) {
                BGNetwork.sendEyeWork(new C2SEyeCutWorkPacket(token, part.ordinal(), i, pending[i]));
            }
            pending[i] = 0f;
        }
        lastSendMs = now;
    }

    private static int rimIndex(float angleRad) {
        float t = (float) ((angleRad + Math.PI / 2.0) % (Math.PI * 2.0));
        if (t < 0f) t += (float) (Math.PI * 2.0);
        return Math.min(EyeCutModel.RIM - 1, (int) (t / (Math.PI * 2.0 / EyeCutModel.RIM)));
    }

    private static boolean inBar(float deg, float x, float y) {
        double a = Math.toRadians(deg);
        float rx = x - CX;
        float ry = y - CY;
        float along = (float) (rx * Math.cos(a) + ry * Math.sin(a));
        float across = (float) (-rx * Math.sin(a) + ry * Math.cos(a));
        return along >= BAR_NEAR - HIT_SLACK && along <= BAR_FAR + HIT_SLACK && Math.abs(across) <= BAR_HALF + HIT_SLACK;
    }

    private static float perp(float deg, float dx, float dy) {
        double a = Math.toRadians(deg);
        return (float) (-dx * Math.sin(a) + dy * Math.cos(a));
    }

    // ---------------------------------------------------------------- server state

    public void accept(S2CEyeCutStatePacket state) {
        if (state.token() != token) return;

        float before = EyeCutModel.total(server, mode);
        System.arraycopy(state.progress(), 0, server, 0, EyeCutModel.SEGMENTS);
        if (state.view() != null) view = state.view();

        if (state.done()) {
            finished = true;
            holding = false;
            java.util.Arrays.fill(shown, 1f);
            flashSuccess();
            if (mode == EyeCutModel.MODE_INSTALL) {
                ClientSfx.play(DoomedSounds.BEEP1.get(), 0.6f, 1.3f);
            } else {
                ClientSfx.play(DoomedSounds.GORE.get(), 0.9f, 0.5f);
            }
            closeNow();
            return;
        }
        stage = Mth.clamp(state.stage(), 0, EyeCutModel.STAGES - 1);
        if (!state.valid()) {
            stopped = true;
            holding = false;
            return;
        }

        float after = EyeCutModel.total(server, mode);
        long now = System.currentTimeMillis();
        if (after > before + 1.0E-4f && now - lastSoundMs >= 220L) {
            lastSoundMs = now;
            if (mode == EyeCutModel.MODE_INSTALL) {
                ClientSfx.play(DoomedSounds.BEEP1.get(), 0.25f, 0.8f + after * 0.5f);
            } else {
                ClientSfx.play(DoomedSounds.CUTEYE.get(), 0.7f, 0.4f);
                // Вспышка боли только у того, кому режут глаз (оператор на другом игроке боли не чувствует).
                if (patientId == -1) winceFlash(Math.min(0.55f, 0.15f + after * 0.4f));
            }
        }
    }

    private void closeNow() {
        EyeCutClient.clear(this);
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        } else {
            super.onClose();
        }
    }

    @Override
    public void onClose() {
        if (!finished && token != 0L) BGNetwork.sendStop(new C2SCutStopPacket(token));
        EyeCutClient.clear(this);
        super.onClose();
    }

    // ---------------------------------------------------------------- rendering

    @Override
    protected void renderDoomed(GuiGraphics gg, float hx, float hy, float partialTick) {
        if (holding) sample(hx, hy);
        for (int i = 0; i < EyeCutModel.SEGMENTS; i++) {
            shown[i] += (server[i] - shown[i]) * 0.25f;
            if (Math.abs(server[i] - shown[i]) < 0.002f) shown[i] = server[i];
        }

        if (patientId == -1 || snapshot == null) {
            HealthPanelBackdrop.render(gg, this.font);
        } else {
            HealthPanelBackdrop.render(gg, this.font, snapshot, patientId, snapshotAtMs);
        }

        gg.drawCenteredString(this.font, Component.translatable(titleKey(mode)).getString()
                + " - " + part.displayName(), (int) CX, 72, 0xFFFFFF);

        drawNerve(gg);
        drawMuscles(gg);
        if (mode == EyeCutModel.MODE_INSTALL) drawImplant(gg); else drawEyeball(gg);
        drawRim(gg);
        drawBlood(gg, hx, hy);
        drawScalpel(gg, hx, hy);
        drawHud(gg);
    }

    private void drawNerve(GuiGraphics gg) {
        float k = intact(shown[EyeCutModel.RIM + EyeCutModel.MUSCLES]);
        boolean active = stageIsGroup(EyeCutModel.GROUP_NERVE);
        int color = lerp(0xFF4A1A1A, active ? 0xFFEDE3A8 : 0xFF9C9678, k);
        float far = BAR_NEAR + 14f + (BAR_FAR - BAR_NEAR - 14f) * k;
        drawBar(gg, NERVE_DEG, R * 0.55f, far, 17f, color);
    }

    private void drawMuscles(GuiGraphics gg) {
        for (int i = 0; i < EyeCutModel.MUSCLES; i++) {
            float k = intact(shown[EyeCutModel.RIM + i]);
            boolean active = stageIsGroup(EyeCutModel.GROUP_MUSCLE);
            int base = active ? 0xFFB84646 : 0xFF8A4A4A;
            int color = lerp(0xFF3A0808, base, k);
            float far = BAR_NEAR + 16f + (BAR_FAR - BAR_NEAR - 16f) * k;
            drawBar(gg, MUSCLE_DEG[i], R * 0.55f, far, BAR_HALF, color);
        }
    }

    /** Полоса вдоль направления deg от радиуса near до far, сужающаяся к дальнему концу. */
    private void drawBar(GuiGraphics gg, float deg, float near, float far, float half, int color) {
        double a = Math.toRadians(deg);
        float ux = (float) Math.cos(a);
        float uy = (float) Math.sin(a);
        float vx = -uy;
        float vy = ux;
        for (float t = near; t <= far; t += 3f) {
            float k = 1f - 0.45f * ((t - near) / Math.max(1f, far - near));
            float w = half * k;
            for (float s = -w; s <= w; s += 3f) {
                int x = Math.round(CX + ux * t + vx * s);
                int y = Math.round(CY + uy * t + vy * s);
                gg.fill(x, y, x + 3, y + 3, color);
            }
        }
    }

    private void drawEyeball(GuiGraphics gg) {
        fillCircle(gg, R, 0xFFEADFD8);
        fillCircle(gg, 64f, 0xFF4F7C88);
        fillCircle(gg, 52f, 0xFF3C6672);
        fillCircle(gg, 26f, 0xFF101010);
        gg.fill((int) CX - 22, (int) CY - 30, (int) CX - 12, (int) CY - 22, 0xCCFFFFFF);
    }

    /** Кибернетический глаз: стальной корпус, линза наливается светом по мере установки. */
    private void drawImplant(GuiGraphics gg) {
        float glow = EyeCutModel.total(shown, mode);
        fillCircle(gg, R, 0xFF70757D);
        fillCircle(gg, R - 9f, 0xFF9AA0A8);
        fillCircle(gg, R - 24f, 0xFF50555C);
        fillCircle(gg, 70f, 0xFF23282D);
        fillCircle(gg, 62f, lerp(0xFF1B4F49, 0xFF2E9A8E, glow));
        fillCircle(gg, 40f, lerp(0xFF123631, 0xFF58D8C6, glow));
        fillCircle(gg, 20f, 0xFF081210);
        gg.fill((int) CX - 24, (int) CY - 32, (int) CX - 12, (int) CY - 24, 0xCCFFFFFF);
    }

    private boolean stageIsGroup(int group) {
        return stage < EyeCutModel.STAGES && EyeCutModel.groupAt(mode, stage) == group;
    }

    private void fillCircle(GuiGraphics gg, float radius, int color) {
        for (int y = (int) (CY - radius); y < CY + radius; y++) {
            float off = (y + 0.5f - CY) / radius;
            int half = (int) (radius * Math.sqrt(Math.max(0f, 1f - off * off)));
            if (half <= 0) continue;
            gg.fill((int) CX - half, y, (int) CX + half, y + 1, color);
        }
    }

    private void drawRim(GuiGraphics gg) {
        float segRad = (float) (Math.PI * 2.0 / EyeCutModel.RIM);
        boolean active = stageIsGroup(EyeCutModel.GROUP_RIM);
        boolean install = mode == EyeCutModel.MODE_INSTALL;
        int step = 0;
        for (float deg = 0f; deg < 360f; deg += 1.2f, step++) {
            float t = (float) Math.toRadians(deg);
            int idx = Math.min(EyeCutModel.RIM - 1, (int) (t / segRad));
            float f = (t - idx * segRad) / segRad;
            boolean done = f <= shown[idx];
            int color;
            if (install) {
                color = done ? 0xFFD8B0A0 : (active ? 0xFF8A1A1A : 0xFF5A1414);
            } else {
                color = done ? 0xFF4A0B0B : (active ? 0xFFD08080 : 0xFFB48A8A);
            }
            float ang = t - (float) (Math.PI / 2.0);
            int x = Math.round(CX + (float) Math.cos(ang) * R);
            int y = Math.round(CY + (float) Math.sin(ang) * R);
            gg.fill(x - 3, y - 3, x + 3, y + 3, color);
            if (install && done && step % 6 == 0) {
                gg.fill(x - 1, y - 6, x + 1, y + 6, 0xFF2A1A1A);
            }
        }
    }

    private void drawBlood(GuiGraphics gg, float hx, float hy) {
        float bleed = view == null ? 0f : view.bleed();
        float total = EyeCutModel.total(shown, mode);
        if (total < 0.02f || bleed < 0.2f) return;
        int drops = Math.min(16, 1 + (int) (bleed / 6f));
        long t = System.currentTimeMillis() / 90L;
        for (int i = 0; i < drops; i++) {
            int dx = (int) (Math.sin(i * 12.9898 + t * 0.05) * 0.8 * R);
            int dy = (int) ((t + i * 7L) % 60L);
            int y0 = (int) (CY + R * 0.4f);
            gg.fill((int) CX + dx, y0 + dy, (int) CX + dx + 2, y0 + dy + 4, 0xC08A0F12);
        }
        int pool = Math.min(110, (int) (bleed * 1.4f));
        gg.fill((int) CX - pool, (int) (CY + R + 150), (int) CX + pool, (int) (CY + R + 155), 0xC06E0B0E);
    }

    private void drawScalpel(GuiGraphics gg, float hx, float hy) {
        if (!holding || mode == EyeCutModel.MODE_INSTALL) return;
        gg.blit(SCALPEL, Math.round(hx), Math.round(hy) - SCALPEL_H / 2 + 4, SCALPEL_W, SCALPEL_H,
                0f, 0f, SCALPEL_TEX_W, SCALPEL_TEX_H, SCALPEL_TEX_W, SCALPEL_TEX_H);
    }

    private void drawHud(GuiGraphics gg) {
        int y = (int) (CY + R + 168);
        String stageName = Component.translatable(keyPrefix() + "stage." + stage).getString();
        gg.drawCenteredString(this.font, stageName + "   " + Math.round(EyeCutModel.total(shown, mode) * 100f) + "%",
                (int) CX, y, 0xFFE8E0D0);

        if (view != null) {
            String line = String.format(Locale.ROOT, "bleed %.2f L/min   blood %.2f L   pain %d",
                    view.bleedRate(), view.blood(), Math.round(view.pain()));
            gg.drawCenteredString(this.font, line, (int) CX, y + 14, 0xFFE08080);
        }

        String hint = stopped
                ? Component.translatable("doomedbgfix.cut.hint.paused").getString()
                : Component.translatable(keyPrefix() + "hint." + stage).getString();
        gg.drawCenteredString(this.font, hint, (int) CX, 636, 0xFFFFE0A0);
        gg.drawCenteredString(this.font, Component.translatable("doomedbgfix.cut.hint.esc").getString(),
                (int) CX, 652, 0xFFB0B0B0);
    }

    private static int lerp(int from, int to, float k) {
        k = Mth.clamp(k, 0f, 1f);
        int r = (int) (((from >> 16) & 0xFF) * (1f - k) + ((to >> 16) & 0xFF) * k);
        int g = (int) (((from >> 8) & 0xFF) * (1f - k) + ((to >> 8) & 0xFF) * k);
        int b = (int) ((from & 0xFF) * (1f - k) + (to & 0xFF) * k);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
