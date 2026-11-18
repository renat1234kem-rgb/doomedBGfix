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
import net.minecraft.util.Mth;
import ru.renat.doomedbgfix.cut.CutLayer;
import ru.renat.doomedbgfix.cut.CutMath;
import ru.renat.doomedbgfix.net.BGNetwork;
import ru.renat.doomedbgfix.net.C2SCutStopPacket;
import ru.renat.doomedbgfix.net.C2SCutWorkPacket;
import ru.renat.doomedbgfix.net.LimbView;
import ru.renat.doomedbgfix.net.S2CCutStatePacket;
import ru.renat.doomedbgfix.net.S2COpenCutPacket;

import java.util.Locale;

/**
 * Новая ампутация: геометрия и темп близки к оригиналу (диск, пила, рез сверху вниз,
 * 0.08 работы за полный проход), но слои видны, кровь идёт с первого движения,
 * прогресс не сбрасывается, кость ломается пропорционально пропилу.
 */
public final class CutScreen extends DoomedScreen {

    private static final float CX = 640f;
    private static final float CY = 352f;
    private static final float R = 160f;
    private static final float STROKE_SPAN = R * 2f * 1.3f;
    private static final long SEND_INTERVAL_MS = 100L;
    private static final float CONTACT_BAND = 46f;

    private final BodyPart part;
    private final int patientId;
    private final long token;
    private final boolean saw;

    private PatientSnapshot snapshot;
    private long snapshotAtMs;
    private LimbView view;

    private float depth;
    private float shown;
    private boolean holding;
    private boolean stopped;
    private boolean severed;
    private float lastX;
    private int lastDir;
    private int reversals;
    private float accum;
    private float contact;
    private long lastSendMs;

    public CutScreen(S2COpenCutPacket packet) {
        super(Component.translatable("doomedbgfix.cut.title"));
        this.part = BodyPart.values()[Mth.clamp(packet.part(), 0, BodyPart.values().length - 1)];
        this.patientId = packet.patientId();
        this.token = packet.token();
        this.saw = packet.tool() == 0;
        this.view = packet.view();
        this.snapshot = packet.snapshot();
        this.snapshotAtMs = System.currentTimeMillis();
        this.depth = packet.depth();
        this.shown = packet.depth();
    }

    @Override protected int virtualW() { return 1280; }
    @Override protected int virtualH() { return 720; }
    @Override protected PainHand.HandPose handPose() { return PainHand.HandPose.GRASP; }

    private float cutY() { return CY - R + 2f * R * shown; }

    @Override
    protected float[] handDrawPos(float vmx, float vmy) {
        if (!holding) return null;
        return new float[]{Mth.clamp(vmx, CX - R, CX + R) + 190f, cutY() + 8f};
    }

    @Override
    protected boolean doMouseClicked(double hx, double hy, int button) {
        if (button != 0 || stopped || severed) return false;
        if (Math.abs(hy - cutY()) > CONTACT_BAND || Math.abs(hx - CX) > R + 70f) return false;
        holding = true;
        lastX = (float) hx;
        lastDir = 0;
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
        if (!holding || stopped || severed) return;
        float dx = hx - lastX;
        lastX = hx;
        boolean onLine = Math.abs(hy - cutY()) <= CONTACT_BAND && Math.abs(hx - CX) <= R + 70f;
        contact = onLine ? 1f : 0f;
        if (!onLine) return;
        float distance = Math.abs(dx);
        if (distance < 0.4f) return;
        int dir = dx > 0 ? 1 : -1;
        if (lastDir != 0 && dir != lastDir) reversals++;
        lastDir = dir;
        float work = distance / STROKE_SPAN * CutMath.WORK_PER_STROKE;
        accum += work;
        CutMath.Tissue tissue = new CutMath.Tissue(view.skin(), view.muscle(), view.bone() == 1, saw, 1f);
        shown = CutMath.advance(shown, work * 0.85f, tissue);
        if (System.currentTimeMillis() - lastSendMs >= SEND_INTERVAL_MS) flush();
    }

    private void flush() {
        if (accum <= 0.0004f || stopped || severed) {
            accum = 0f;
            reversals = 0;
            return;
        }
        BGNetwork.sendWork(new C2SCutWorkPacket(token, part.ordinal(), accum, reversals, contact));
        accum = 0f;
        reversals = 0;
        lastSendMs = System.currentTimeMillis();
    }

    public void accept(S2CCutStatePacket state) {
        if (state.token() != token || state.part() != part.ordinal() || state.patientId() != patientId) return;
        depth = Mth.clamp(state.depth(), 0f, 1f);
        if (state.view() != null) view = state.view();
        if (!state.valid()) {
            stopped = true;
            holding = false;
            return;
        }
        ClientSfx.play(DoomedSounds.SAW.get(), CutLayer.at(depth) == CutLayer.BONE ? 0.72f : 1.05f, 0.35f);
        if (patientId == -1) winceFlash(Math.min(0.35f, 0.08f + depth * 0.2f));
        if (state.severed()) {
            severed = true;
            holding = false;
            flashSuccess();
            ClientSfx.play(DoomedSounds.BONEHIT.get(), 0.6f, 0.6f);
            onClose();
        }
    }

    @Override
    public void onClose() {
        if (!severed && token != 0L) BGNetwork.sendStop(new C2SCutStopPacket(token));
        CutClient.clear(this);
        super.onClose();
    }

    @Override
    protected void renderDoomed(GuiGraphics gg, float hx, float hy, float partialTick) {
        if (holding) sample(hx, hy);
        shown += (depth - shown) * 0.30f;
        if (Math.abs(depth - shown) < 0.002f) shown = depth;

        if (patientId == -1 || snapshot == null) {
            HealthPanelBackdrop.render(gg, this.font);
        } else {
            HealthPanelBackdrop.render(gg, this.font, snapshot, patientId, snapshotAtMs);
        }

        gg.drawCenteredString(this.font, Component.translatable("doomedbgfix.cut.title").getString()
                + " - " + part.displayName(), (int) CX, 96, 0xFFFFFF);

        float top = CY - R;
        float cut = cutY();
        for (int y = (int) top; y < CY + R; y++) {
            float off = (y + 0.5f - CY) / R;
            int half = (int) (R * Math.sqrt(Math.max(0f, 1f - off * off)));
            if (half <= 0) continue;
            gg.fill((int) CX - half - 2, y, (int) CX + half + 2, y + 1, 0xFF4A3A36);
            float f = (y - top) / (2f * R);
            gg.fill((int) CX - half, y, (int) CX + half, y + 1, y < cut ? woundColor(f) : bandColor(f));
        }

        int sawY = Math.round(cut);
        int sawX = Math.round(holding ? Mth.clamp(hx, CX - R, CX + R) : CX);
        if (holding) {
            gg.fill(sawX - 130, sawY - 2, sawX + 180, sawY + 1, 0xFFD8D2C6);
            for (int x = sawX - 126; x < sawX + 176; x += 9) gg.fill(x, sawY + 1, x + 3, sawY + 3, 0xFFD8D2C6);
            gg.fill(sawX + 180, sawY - 6, sawX + 212, sawY + 7, 0xFF5A4632);
        } else {
            for (int x = (int) (CX - R - 26); x < CX + R + 26; x += 10) gg.fill(x, sawY, x + 5, sawY + 1, 0x88D8D2C6);
        }

        float bleed = view == null ? 0f : view.bleed();
        if (shown > 0.01f && bleed > 0.2f) {
            int drops = Math.min(14, 1 + (int) (bleed / 7f));
            long t = System.currentTimeMillis() / 90L;
            for (int i = 0; i < drops; i++) {
                int dx = (int) (Math.sin(i * 12.9898 + t * 0.05) * 0.5 * R);
                int dy = (int) ((t + i * 7L) % 46L);
                gg.fill((int) CX + dx, sawY + 4 + dy, (int) CX + dx + 2, sawY + 7 + dy, 0xC08A0F12);
            }
            int pool = Math.min(90, (int) (bleed * 1.2f));
            gg.fill((int) CX - pool, (int) (CY + R + 16), (int) CX + pool, (int) (CY + R + 20), 0xC06E0B0E);
        }

        CutLayer layer = CutLayer.at(shown);
        gg.drawCenteredString(this.font, Component.translatable(layer.key()).getString()
                + "   " + Math.round(shown * 100f) + "%", (int) CX, (int) (CY + R + 30),
                layer == CutLayer.BONE ? 0xFFE0C060 : 0xFFDDDDDD);

        if (view != null) {
            String blood = String.format(Locale.ROOT, "bleed %.2f L/min   blood %.2f L   pain %d",
                    view.bleedRate(), view.blood(), Math.round(view.pain()));
            gg.drawCenteredString(this.font, blood, (int) CX, (int) (CY + R + 44), 0xFFE08080);
            String tq;
            int tqColor;
            if (view.blocked()) {
                tq = Component.translatable("doomedbgfix.cut.tq.ok").getString();
                tqColor = 0xFF70C070;
            } else if (view.tourniquet()) {
                tq = Component.translatable("doomedbgfix.cut.tq.bad").getString();
                tqColor = 0xFFE0A030;
            } else {
                tq = Component.translatable("doomedbgfix.cut.tq.none").getString();
                tqColor = 0xFFE06060;
            }
            gg.drawCenteredString(this.font, tq, (int) CX, (int) (CY + R + 58), tqColor);
            if (view.bone() == 1) {
                float bp = CutMath.boneProgress(shown);
                gg.fill((int) CX - 100, (int) (CY + R + 72), (int) CX + 100, (int) (CY + R + 78), 0xFF3A2A2A);
                gg.fill((int) CX - 100, (int) (CY + R + 72), (int) (CX - 100 + 200 * bp), (int) (CY + R + 78), 0xFFD0C0A0);
                gg.drawCenteredString(this.font, Component.translatable("doomedbgfix.cut.fracture").getString(),
                        (int) CX, (int) (CY + R + 82), 0xFFC0A080);
            }
        }

        String hint = stopped
                ? Component.translatable("doomedbgfix.cut.hint.paused").getString()
                : (layer == CutLayer.BONE
                    ? Component.translatable("doomedbgfix.cut.hint.bone").getString()
                    : (holding && contact < 0.5f
                        ? Component.translatable("doomedbgfix.cut.hint.contact").getString()
                        : Component.translatable("doomedbgfix.cut.hint.saw").getString()));
        gg.drawCenteredString(this.font, hint, (int) CX, 636, 0xFFFFE0A0);
        gg.drawCenteredString(this.font, Component.translatable("doomedbgfix.cut.hint.esc").getString(),
                (int) CX, 652, 0xFFB0B0B0);
    }

    private int bandColor(float f) {
        float skin = view == null ? 100f : view.skin();
        float muscle = view == null ? 100f : view.muscle();
        boolean fractured = view != null && view.bone() == 1;
        if (f < CutLayer.SKIN.end || f >= 0.94f) return tint(0xC9, 0x8F, 0x6E, skin / 100f);
        if (f < CutLayer.FAT.end || f >= 0.86f) return 0xFFE8D08A;
        if (f < CutLayer.MUSCLE.end || f >= CutLayer.EXIT.start) return tint(0x9E, 0x3B, 0x34, 0.45f + 0.55f * muscle / 100f);
        return fractured ? 0xFFD8B8A8 : 0xFFE8E2D0;
    }

    private int woundColor(float f) {
        if (f >= CutLayer.BONE.start && f < CutLayer.BONE.end) return 0xFF6E3A32;
        return 0xFF5A1010;
    }

    private static int tint(int r, int g, int b, float k) {
        k = Mth.clamp(k, 0.25f, 1f);
        return 0xFF000000 | ((int) (r * k) << 16) | ((int) (g * k) << 8) | (int) (b * k);
    }
}
