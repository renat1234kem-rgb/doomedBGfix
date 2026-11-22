package ru.renat.doomedbgfix.server;

import net.mattlives.doomedmatu.body.BodyPart;
import net.mattlives.doomedmatu.body.CoopMedical;
import net.mattlives.doomedmatu.body.EyeFunctionService;
import net.mattlives.doomedmatu.body.HandFunctionService;
import net.mattlives.doomedmatu.body.LimbData;
import net.mattlives.doomedmatu.capability.DoomedData;
import net.mattlives.doomedmatu.capability.DoomedDataUtil;
import net.minecraft.server.level.ServerPlayer;
import ru.renat.doomedbgfix.config.BGConfig;
import ru.renat.doomedbgfix.cut.CutMath;
import ru.renat.doomedbgfix.eye.EyeAmputationHandler;
import ru.renat.doomedbgfix.eye.EyeCutModel;
import ru.renat.doomedbgfix.eye.EyeTools;
import ru.renat.doomedbgfix.net.BGNetwork;
import ru.renat.doomedbgfix.net.LimbView;
import ru.renat.doomedbgfix.net.S2CEyeCutStatePacket;
import ru.renat.doomedbgfix.net.S2COpenEyeCutPacket;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Мини-игра удаления глаза (сам себе). Сервер хранит прогресс по 13 сегментам и считает
 * боль, кровь и повреждение тканей. Клиент только присылает "сколько прорезал".
 */
public final class EyeCutSessions {

    private static final Map<UUID, EyeCutSession> BY_OPERATOR = new HashMap<>();

    /** Максимум прогресса одного сегмента в секунду (защита от подделки пакетов). */
    private static final float MAX_PER_TICK = 0.045f;
    private static final float MAX_PER_PACKET = 0.30f;

    // Боль / кровь / травма за ПОЛНЫЙ этап (делится поровну между сегментами этапа).
    // Для сравнения: пила режет конечность примерно на 60-100 боли, тут глаз упирается в 100 уже к мышцам.
    private static final float[] PAIN_FULL   = {45f, 70f, 100f};
    private static final float[] BLEED_FULL  = {10f, 14f, 8f};
    private static final float[] TRAUMA_FULL = {4f, 8f, 10f};
    private static final float HEAD_PAIN_SHARE = 0.4f;
    private static final float HEAD_BLEED_SHARE = 0.5f;

    private EyeCutSessions() {}

    public static boolean openSelf(ServerPlayer player, BodyPart part, boolean forceInteractive) {
        DoomedData data = CutBridge.data(player);
        if (data == null || !data.isActive() || !EyeFunctionService.isEye(part)
                || data.getLimb(part).isDismembered()
                || !EyeEligibility.canStart(player)) {
            return false;
        }
        long token = player.getRandom().nextLong();
        if (token == 0L) token = 1L;
        long now = player.serverLevel().getGameTime();
        EyeCutSession session = new EyeCutSession(token, player.getUUID(), part, now);
        BY_OPERATOR.put(player.getUUID(), session);

        BGNetwork.sendOpenEye(player, new S2COpenEyeCutPacket(part.ordinal(), token, session.seg.clone(),
                LimbView.of(data, part), forceInteractive));
        return true;
    }

    public static void work(ServerPlayer operator, long token, int partOrdinal, int index, float delta) {
        EyeCutSession s = BY_OPERATOR.get(operator.getUUID());
        if (s == null || s.token != token || partOrdinal != s.part.ordinal()) return;
        if (!Float.isFinite(delta) || delta <= 0f) return;
        if (EyeCutModel.stageOf(index) != s.stage) return;

        DoomedData data = CutBridge.data(operator);
        if (!valid(operator, data, s)) {
            BY_OPERATOR.remove(operator.getUUID());
            BGNetwork.sendEyeState(operator, new S2CEyeCutStatePacket(token, s.part.ordinal(), s.stage,
                    s.seg.clone(), false, false, data == null ? null : LimbView.of(data, s.part)));
            return;
        }

        long now = operator.serverLevel().getGameTime();
        long ticks = Math.max(1L, Math.min(20L, now - s.lastInputTick));
        s.lastInputTick = now;

        float cap = Math.min(MAX_PER_PACKET, MAX_PER_TICK * ticks);
        float effort = CutMath.effort(data.getConsciousness(), data.getPain());
        float applied = Math.min(delta, cap) * effort;

        float before = s.seg[index];
        float after = Math.min(1f, before + applied);
        float gained = after - before;
        if (gained > 0f) {
            s.seg[index] = after;
            applyEffects(data, s, gained);
        }

        boolean finished = false;
        if (EyeCutModel.stageDone(s.seg, s.stage)) {
            s.stage++;
            if (s.stage >= EyeCutModel.STAGES) {
                finished = EyeAmputationHandler.perform(operator, s.part);
                BY_OPERATOR.remove(operator.getUUID());
            }
        }
        if (!finished) DoomedDataUtil.sync(operator);

        BGNetwork.sendEyeState(operator, new S2CEyeCutStatePacket(token, s.part.ordinal(),
                Math.min(s.stage, EyeCutModel.STAGES - 1), s.seg.clone(), !(s.stage >= EyeCutModel.STAGES) || finished,
                finished, LimbView.of(data, s.part)));
    }

    private static void applyEffects(DoomedData data, EyeCutSession s, float gained) {
        int stage = s.stage;
        int count = EyeCutModel.count(stage);
        LimbData eye = data.getLimb(s.part);
        LimbData head = data.getLimb(BodyPart.HEAD);

        float pain = gained * PAIN_FULL[stage] / count * BGConfig.painScale() * (1.0f - CutBridge.analgesia(data));
        eye.setPain(eye.getPain() + pain);
        head.setPain(head.getPain() + pain * HEAD_PAIN_SHARE);

        float bleed = gained * BLEED_FULL[stage] / count * BGConfig.bleedScale();
        eye.setBleed(eye.getBleed() + bleed);
        head.setBleed(head.getBleed() + bleed * HEAD_BLEED_SHARE);

        data.addTrauma(gained * TRAUMA_FULL[stage] / count);
        data.setAdrenaline(Math.max(data.getAdrenaline(), 35f));

        // Ткани портятся по мере реза (но не до нуля: нуль = "глаза нет", это делает removeEye в конце).
        float rim = EyeCutModel.stageProgress(s.seg, EyeCutModel.STAGE_RIM);
        float muscle = EyeCutModel.stageProgress(s.seg, EyeCutModel.STAGE_MUSCLE);
        eye.setSkinHealth(Math.min(eye.getSkinHealth(), 100f - 85f * rim));
        eye.setMuscleHealth(Math.min(eye.getMuscleHealth(), 100f - 85f * muscle));
    }

    private static boolean valid(ServerPlayer operator, DoomedData data, EyeCutSession s) {
        if (operator == null || !operator.isAlive() || data == null || !data.isActive()) return false;
        if (data.getLimb(s.part).isDismembered()) return false;
        return EyeEligibility.canContinue(operator);
    }

    public static void stop(ServerPlayer operator, long token) {
        EyeCutSession s = BY_OPERATOR.get(operator.getUUID());
        if (s != null && s.token == token) BY_OPERATOR.remove(operator.getUUID());
        // Прогресс не копится: следующая попытка начнётся с нуля, но раны и боль остаются.
    }

    public static void tick(ServerPlayer player) {
        EyeCutSession s = BY_OPERATOR.get(player.getUUID());
        if (s == null) return;
        long now = player.serverLevel().getGameTime();
        if (now - s.lastInputTick > BGConfig.idleTimeoutTicks()) BY_OPERATOR.remove(player.getUUID());
    }

    public static void forget(UUID player) {
        BY_OPERATOR.remove(player);
    }

    /** Условия на игрока и инструмент вынесены отдельно, чтобы start и continue не расходились. */
    private static final class EyeEligibility {
        static boolean canContinue(ServerPlayer p) {
            return EyeTools.isEyeCutTool(p.getMainHandItem()) && HandFunctionService.canPerformTwoHanded(p);
        }
        static boolean canStart(ServerPlayer p) {
            return CoopMedical.canSelfTreat(p) && canContinue(p);
        }
    }
}
