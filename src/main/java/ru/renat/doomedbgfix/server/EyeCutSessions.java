package ru.renat.doomedbgfix.server;

import net.mattlives.doomedmatu.body.BodyPart;
import net.mattlives.doomedmatu.body.ConsentGate;
import net.mattlives.doomedmatu.body.CoopMedical;
import net.mattlives.doomedmatu.body.EyeFunctionService;
import net.mattlives.doomedmatu.body.HandFunctionService;
import net.mattlives.doomedmatu.body.LimbData;
import net.mattlives.doomedmatu.body.PatientSnapshot;
import net.mattlives.doomedmatu.body.TreatmentKind;
import net.mattlives.doomedmatu.capability.DoomedData;
import net.mattlives.doomedmatu.capability.DoomedDataUtil;
import net.mattlives.doomedmatu.config.DoomedConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import ru.renat.doomedbgfix.config.BGConfig;
import ru.renat.doomedbgfix.cut.CutMath;
import ru.renat.doomedbgfix.eye.EyeAmputationHandler;
import ru.renat.doomedbgfix.eye.EyeCutModel;
import ru.renat.doomedbgfix.eye.EyeImplantHandler;
import ru.renat.doomedbgfix.eye.EyeTools;
import ru.renat.doomedbgfix.net.BGNetwork;
import ru.renat.doomedbgfix.net.LimbView;
import ru.renat.doomedbgfix.net.S2CEyeCutStatePacket;
import ru.renat.doomedbgfix.net.S2COpenEyeCutPacket;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Мини-игры с глазом: удаление (MODE_CUT) и установка импланта (MODE_INSTALL),
 * на себе и на другом игроке. Сервер хранит прогресс 13 сегментов и считает боль, кровь и травму,
 * клиент присылает только "сколько проведено".
 *
 * Прогресс удаления не хранится отдельно: он выводится из здоровья мышц глаза (как глубина реза
 * у конечностей выводится из кожи и мышц). Резали глаз, бросили, вернулись - этапы восстановятся
 * по мышцам, а по мере их заживления прогресс сам откатывается.
 */
public final class EyeCutSessions {

    private static final Map<UUID, EyeCutSession> BY_OPERATOR = new HashMap<>();

    /** Предел прогресса сегмента за тик и за пакет (защита от подделки пакетов). */
    private static final float MAX_PER_TICK = 0.045f;
    private static final float MAX_PER_PACKET = 0.30f;

    private static final double COOP_RANGE_SQ = 36.0;

    // Боль / кровь / травма за ПОЛНЫЙ этап (делится поровну между его сегментами). Индексы: [режим][этап по порядку].
    // Удаление намного больнее обычной ампутации, установка заметно мягче.
    private static final float[][] PAIN_FULL   = {{45f, 70f, 100f}, {10f, 14f, 8f}};
    private static final float[][] BLEED_FULL  = {{10f, 14f, 8f},   {0f, 3f, 2f}};
    private static final float[][] TRAUMA_FULL = {{4f, 8f, 10f},    {0f, 2f, 2f}};
    private static final float HEAD_PAIN_SHARE = 0.4f;
    private static final float HEAD_BLEED_SHARE = 0.5f;

    /** Потолок прогресса при открытии/синхронизации, чтобы "почти готовый" глаз не завершился сам по себе. */
    private static final float OPEN_CAP = 0.97f;
    private static final float SYNC_CAP = 0.995f;
    private static final float SYNC_TOLERANCE = 0.02f;

    private EyeCutSessions() {}

    // ------------------------------------------------------------------ вход

    public static boolean openSelf(ServerPlayer player, BodyPart part, int mode, boolean forceInteractive) {
        if (!CoopMedical.canSelfTreat(player)) return false;
        return start(player, player, part, mode, forceInteractive);
    }

    public static boolean openCoop(ServerPlayer healer, ServerPlayer patient, BodyPart part, int mode, boolean forceInteractive) {
        if (healer == patient || healer.distanceToSqr(patient) > COOP_RANGE_SQ || !healer.hasLineOfSight(patient)) return false;
        return start(healer, patient, part, mode, forceInteractive);
    }

    /**
     * Обработка правого клика по игроку с выбранным глазом (или головой) на панели WITNESS.
     * @return true, если действие обработано (Doomed дальше ничего не делает)
     */
    public static boolean startCoop(ServerPlayer healer, ServerPlayer patient, DoomedData pd, ItemStack held, BodyPart aimed) {
        if (aimed == null || pd == null || !pd.isActive()) return false;
        boolean aimsEye = EyeFunctionService.isEye(aimed) || aimed == BodyPart.HEAD;
        if (!aimsEye) return false;

        if (EyeTools.isImplant(held)) {
            BodyPart part = pickEye(pd, aimed, true);
            if (part == null) {
                healer.displayClientMessage(Component.translatable("doomedbgfix.eye.implant.no_empty_socket_other"), true);
                return true;
            }
            // Как у протезов Doomed: согласие -> после ответа вызовется openAuthorizedConsentedTreatment (см. миксин).
            if (ConsentGate.requestDeferredTreatment(healer, patient, TreatmentKind.INSTALL_PROSTHETIC, part)) {
                openCoop(healer, patient, part, EyeCutModel.MODE_INSTALL, false);
            }
            return true;
        }

        if (EyeTools.isEyeCutTool(held)) {
            BodyPart part = pickEye(pd, aimed, false);
            if (part == null) return false;
            if (!DoomedConfig.crossPlayerAmputation()) {
                healer.displayClientMessage(Component.translatable("doomedbgfix.eye.cut.coop_disabled"), true);
                return true;
            }
            if (!HandFunctionService.canPerformTwoHanded(healer)) {
                healer.displayClientMessage(Component.translatable("doomedbgfix.eye.cut.two_hands"), true);
                return true;
            }
            if (DoomedConfig.requireCrossPlayerTreatmentConsent()) {
                // Здоровый глаз ещё можно спасти, поэтому без ответа пациента (без сознания) не режем.
                if (ConsentGate.cannotAnswer(patient)) {
                    healer.displayClientMessage(Component.translatable("doomedbgfix.eye.cut.cannot_answer"), true);
                    return true;
                }
                // false = пациенту отправлен запрос; после согласия Doomed вызовет openAuthorizedMinigame (см. миксин).
                if (!ConsentGate.clears(healer, patient, TreatmentKind.AMPUTATE, part, 0.0f)) return true;
            }
            openCoop(healer, patient, part, EyeCutModel.MODE_CUT, false);
            return true;
        }
        return false;
    }

    /** Нужный глаз: тот, что выбран, иначе первый подходящий (левый приоритетнее). null - подходящих нет. */
    public static BodyPart pickEye(DoomedData pd, BodyPart aimed, boolean absent) {
        if (EyeFunctionService.isEye(aimed)) {
            return EyeFunctionService.isAbsent(pd, aimed) == absent ? aimed : null;
        }
        if (EyeFunctionService.isAbsent(pd, BodyPart.EYE_LEFT) == absent) return BodyPart.EYE_LEFT;
        if (EyeFunctionService.isAbsent(pd, BodyPart.EYE_RIGHT) == absent) return BodyPart.EYE_RIGHT;
        return null;
    }

    private static boolean start(ServerPlayer operator, ServerPlayer patient, BodyPart part, int mode, boolean force) {
        mode = EyeCutModel.normMode(mode);
        DoomedData pd = CutBridge.data(patient);
        if (!eligible(operator, pd, part, mode)) return false;

        boolean self = operator == patient;
        long token = operator.getRandom().nextLong();
        if (token == 0L) token = 1L;
        long now = operator.serverLevel().getGameTime();
        EyeCutSession s = new EyeCutSession(token, operator.getUUID(), patient.getUUID(),
                self ? -1 : patient.getId(), self, part, mode, now);

        if (mode == EyeCutModel.MODE_CUT) {
            // Начинаем с того места, которое уже "намотали" мышцы глаза.
            float implied = Math.min(OPEN_CAP, EyeCutModel.impliedFromMuscle(pd.getLimb(part).getMuscleHealth()));
            EyeCutModel.fromTotal(implied, mode, s.seg);
        }
        BY_OPERATOR.put(operator.getUUID(), s);

        BGNetwork.sendOpenEye(operator, new S2COpenEyeCutPacket(part.ordinal(), s.patientId, token, mode,
                s.seg.clone(), LimbView.of(pd, part), PatientSnapshot.of(pd), force));
        return true;
    }

    // ------------------------------------------------------------------ условия

    private static boolean toolOk(ServerPlayer operator, int mode) {
        ItemStack held = operator.getMainHandItem();
        return mode == EyeCutModel.MODE_INSTALL ? EyeTools.isImplant(held) : EyeTools.isEyeCutTool(held);
    }

    private static boolean eyeStateOk(DoomedData pd, BodyPart part, int mode) {
        boolean absent = EyeFunctionService.isAbsent(pd, part);
        return mode == EyeCutModel.MODE_INSTALL ? absent : !absent;
    }

    private static boolean eligible(ServerPlayer operator, DoomedData pd, BodyPart part, int mode) {
        return pd != null && pd.isActive() && EyeFunctionService.isEye(part)
                && eyeStateOk(pd, part, mode)
                && toolOk(operator, mode)
                && HandFunctionService.canPerformTwoHanded(operator);
    }

    private static boolean valid(ServerPlayer operator, ServerPlayer patient, DoomedData pd, EyeCutSession s) {
        if (operator == null || !operator.isAlive() || patient == null || pd == null || !pd.isActive()) return false;
        if (!eligible(operator, pd, s.part, s.mode)) return false;
        return s.self || (operator.distanceToSqr(patient) <= COOP_RANGE_SQ && operator.hasLineOfSight(patient));
    }

    // ------------------------------------------------------------------ работа

    public static void work(ServerPlayer operator, long token, int partOrdinal, int index, float delta) {
        EyeCutSession s = BY_OPERATOR.get(operator.getUUID());
        if (s == null || s.token != token || partOrdinal != s.part.ordinal()) return;
        if (!Float.isFinite(delta) || delta <= 0f) return;

        ServerPlayer patient = s.self ? operator : CoopMedical.resolvePatient(operator, s.patientId);
        DoomedData pd = patient == null ? null : CutBridge.data(patient);
        if (!valid(operator, patient, pd, s)) {
            BY_OPERATOR.remove(operator.getUUID());
            BGNetwork.sendEyeState(operator, new S2CEyeCutStatePacket(token, s.part.ordinal(),
                    Math.min(s.stage(), EyeCutModel.STAGES - 1), s.seg.clone(), false, false,
                    pd == null ? null : LimbView.of(pd, s.part)));
            return;
        }

        if (s.mode == EyeCutModel.MODE_CUT) syncFromMuscle(pd, s);

        int stage = s.stage();
        if (stage >= EyeCutModel.STAGES || EyeCutModel.stageOf(s.mode, index) != stage) return;

        long now = operator.serverLevel().getGameTime();
        long ticks = Math.max(1L, Math.min(20L, now - s.lastInputTick));
        s.lastInputTick = now;

        DoomedData od = CutBridge.data(operator);
        float effort = od == null || !od.isActive() ? 1f : CutMath.effort(od.getConsciousness(), od.getPain());
        float cap = Math.min(MAX_PER_PACKET, MAX_PER_TICK * (float) ticks);
        float applied = Math.min(delta, cap) * effort;

        float before = s.seg[index];
        float after = Math.min(1f, before + applied);
        float gained = after - before;
        if (gained > 0f) {
            s.seg[index] = after;
            applyEffects(pd, s, stage, gained);
        }

        boolean done = EyeCutModel.currentStage(s.seg, s.mode) >= EyeCutModel.STAGES;
        boolean completed = false;
        if (done) {
            completed = s.mode == EyeCutModel.MODE_CUT
                    ? EyeAmputationHandler.perform(patient, s.part, operator)
                    : finishInstall(operator, patient, s.part);
            BY_OPERATOR.remove(operator.getUUID());
        } else {
            DoomedDataUtil.sync(patient);
        }

        BGNetwork.sendEyeState(operator, new S2CEyeCutStatePacket(token, s.part.ordinal(),
                Math.min(s.stage(), EyeCutModel.STAGES - 1), s.seg.clone(), !done || completed, completed,
                LimbView.of(pd, s.part)));
    }

    /** Мышцы - источник истины: если они зажили (или глаз травмировали иначе), прогресс подтягивается к ним. */
    private static void syncFromMuscle(DoomedData pd, EyeCutSession s) {
        float implied = Math.min(SYNC_CAP, EyeCutModel.impliedFromMuscle(pd.getLimb(s.part).getMuscleHealth()));
        if (Math.abs(EyeCutModel.total(s.seg, s.mode) - implied) > SYNC_TOLERANCE) {
            EyeCutModel.fromTotal(implied, s.mode, s.seg);
        }
    }

    private static void applyEffects(DoomedData pd, EyeCutSession s, int stage, float gained) {
        int count = EyeCutModel.countOf(s.mode, stage);
        LimbData eye = pd.getLimb(s.part);
        LimbData head = pd.getLimb(BodyPart.HEAD);

        float pain = gained * PAIN_FULL[s.mode][stage] / count * BGConfig.painScale() * (1.0f - CutBridge.analgesia(pd));
        eye.setPain(eye.getPain() + pain);
        head.setPain(head.getPain() + pain * HEAD_PAIN_SHARE);

        float bleed = gained * BLEED_FULL[s.mode][stage] / count * BGConfig.bleedScale();
        eye.setBleed(eye.getBleed() + bleed);
        head.setBleed(head.getBleed() + bleed * HEAD_BLEED_SHARE);

        pd.addTrauma(gained * TRAUMA_FULL[s.mode][stage] / count);
        pd.setAdrenaline(Math.max(pd.getAdrenaline(), 35f));

        if (s.mode == EyeCutModel.MODE_CUT) {
            // Прогресс привязан к мышцам. Кожа только позволяет крови держаться (BleedHandler обрезает кровь
            // до 100 * (1 - skin/100)), поэтому снижаем её вместе с прогрессом, но не до нуля.
            float total = EyeCutModel.total(s.seg, s.mode);
            eye.setMuscleHealth(Math.min(eye.getMuscleHealth(), EyeCutModel.muscleForTotal(total)));
            eye.setSkinHealth(Math.min(eye.getSkinHealth(), 100f - 85f * total));
        }
    }

    private static boolean finishInstall(ServerPlayer operator, ServerPlayer patient, BodyPart part) {
        ItemStack held = operator.getMainHandItem();
        if (!EyeTools.isImplant(held)) return false;
        if (!EyeImplantHandler.install(patient, part)) return false;
        if (!operator.getAbilities().instabuild) held.shrink(1);
        return true;
    }

    // ------------------------------------------------------------------ жизненный цикл

    public static void stop(ServerPlayer operator, long token) {
        EyeCutSession s = BY_OPERATOR.get(operator.getUUID());
        if (s != null && s.token == token) BY_OPERATOR.remove(operator.getUUID());
        // Прогресс удаления сохраняется в мышцах глаза, прогресс установки не сохраняется (имплант остаётся у оператора).
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
}
