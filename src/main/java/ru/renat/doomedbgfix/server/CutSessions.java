package ru.renat.doomedbgfix.server;

import net.mattlives.doomedmatu.body.BodyPart;
import net.mattlives.doomedmatu.body.BoneState;
import net.mattlives.doomedmatu.body.CoopMedical;
import net.mattlives.doomedmatu.body.DismemberService;
import net.mattlives.doomedmatu.body.HandFunctionService;
import net.mattlives.doomedmatu.body.LimbData;
import net.mattlives.doomedmatu.body.PatientSnapshot;
import net.mattlives.doomedmatu.capability.DoomedData;
import net.mattlives.doomedmatu.capability.DoomedDataUtil;
import net.mattlives.doomedmatu.config.DoomedConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import ru.renat.doomedbgfix.config.BGConfig;
import ru.renat.doomedbgfix.cut.CutCapability;
import ru.renat.doomedbgfix.cut.CutMath;
import ru.renat.doomedbgfix.cut.CutProgress;
import ru.renat.doomedbgfix.cut.LimbCut;
import ru.renat.doomedbgfix.net.BGNetwork;
import ru.renat.doomedbgfix.net.LimbView;
import ru.renat.doomedbgfix.net.S2CCutStatePacket;
import ru.renat.doomedbgfix.net.S2COpenCutPacket;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CutSessions {

    private static final Map<UUID, CutSession> BY_OPERATOR = new HashMap<>();

    private CutSessions() {}

    public static boolean openSelf(ServerPlayer player, BodyPart part, boolean forceInteractive) {
        DoomedData data = CutBridge.data(player);
        DismemberService.CutTool tool = DismemberService.cutTool(player.getMainHandItem());
        if (data == null || !data.isActive() || !DismemberService.isAmputatable(part)
                || data.getLimb(part).isDismembered() || tool == null
                || !HandFunctionService.canPerformTwoHanded(player)) {
            return false;
        }
        start(player, player, -1, part, tool, forceInteractive);
        return true;
    }

    public static boolean openCoop(ServerPlayer healer, ServerPlayer patient, BodyPart part,
                                   DismemberService.CutTool authorizedTool, boolean forceInteractive) {
        DoomedData pd = CutBridge.data(patient);
        if (!DoomedConfig.crossPlayerAmputation() || pd == null || !pd.isActive()
                || !DismemberService.isAmputatable(part) || pd.getLimb(part).isDismembered()
                || authorizedTool == null
                || DismemberService.cutTool(healer.getMainHandItem()) != authorizedTool
                || !HandFunctionService.canPerformTwoHanded(healer)
                || healer.distanceToSqr(patient) > 36.0 || !healer.hasLineOfSight(patient)) {
            return false;
        }
        start(healer, patient, patient.getId(), part, authorizedTool, forceInteractive);
        return true;
    }

    private static void start(ServerPlayer operator, ServerPlayer patient, int patientId, BodyPart part,
                              DismemberService.CutTool tool, boolean forceInteractive) {
        long token = operator.getRandom().nextLong();
        if (token == 0L) token = 1L;
        long now = operator.serverLevel().getGameTime();
        BY_OPERATOR.put(operator.getUUID(), new CutSession(token, operator.getUUID(), patient.getUUID(),
                patientId, part, tool, operator == patient, now));

        DoomedData pd = CutBridge.data(patient);
        LimbCut cut = CutCapability.cut(patient, part);
        LimbData limb = pd.getLimb(part);

        // Уже разорванная конечность начинает мини-игру «покоцанной».
        float free = CutMath.freeDepth(limb.getSkinHealth(), limb.getMuscleHealth());
        if (cut.depth() < free) cut.setDepth(free, now);

        BGNetwork.sendOpen(operator, new S2COpenCutPacket(part.ordinal(), patientId, token, cut.depth(),
                tool.ordinal(), LimbView.of(pd, part), PatientSnapshot.of(pd), forceInteractive));
    }

    public static void work(ServerPlayer operator, long token, int partOrdinal, float work, int reversals, float contact) {
        CutSession session = BY_OPERATOR.get(operator.getUUID());
        if (session == null || session.token != token || partOrdinal < 0 || partOrdinal >= BodyPart.values().length) return;
        BodyPart part = BodyPart.values()[partOrdinal];
        if (part != session.part) return;

        ServerPlayer patient = session.self ? operator : CoopMedical.resolvePatient(operator, session.patientId);
        if (!valid(operator, patient, session)) {
            BY_OPERATOR.remove(operator.getUUID());
            BGNetwork.sendState(operator, new S2CCutStatePacket(partOrdinal, session.patientId, token, 0f, false, false, null));
            return;
        }
        long now = operator.serverLevel().getGameTime();
        long delta = Math.max(1L, now - session.lastInputTick);
        session.lastInputTick = now;

        DoomedData pd = CutBridge.data(patient);
        DoomedData od = CutBridge.data(operator);
        LimbCut cut = CutCapability.cut(patient, part);
        LimbData limb = pd.getLimb(part);

        // Ванильный темп: не больше 0.08 работы за 5 тиков.
        float rateCap = CutMath.WORK_PER_STROKE * (float) delta / 5f;
        float clamped = Mth.clamp(work, 0f, Math.min(BGConfig.maxWorkPerPacket(), rateCap));
        float effort = od == null || !od.isActive() ? 1f : CutMath.effort(od.getConsciousness(), od.getPain());
        float speed = BGConfig.workPerSecond() / CutMath.WORK_PER_SECOND;
        float applied = clamped * effort * CutMath.technique(reversals, contact) * speed;

        CutMath.Tissue tissue = new CutMath.Tissue(limb.getSkinHealth(), limb.getMuscleHealth(),
                limb.getBone() == BoneState.FRACTURED, CutBridge.saw(session.tool), BGConfig.boneResistanceScale());

        float before = cut.depth();
        float after = CutMath.advance(before, applied, tissue);
        cut.setDepth(after, now);

        if (after > before) {
            CutWounds.apply(pd, part, cut, CutBridge.toolBleed(session.tool), CutBridge.toolContamination(session.tool));
            float pain = CutMath.pain(after, applied, CutBridge.toolPain(session.tool)) * BGConfig.painScale();
            pain *= 1.0f - CutBridge.analgesia(pd);
            limb.setPain(limb.getPain() + pain);
            CutBone.onCut(pd, part, cut);
            pd.setAdrenaline(Math.max(pd.getAdrenaline(), 25.0f));
        }

        boolean severed = false;
        if (cut.severed()) {
            severed = finish(operator, patient, pd, session);
        } else {
            DoomedDataUtil.sync(patient);
        }

        BGNetwork.sendState(operator, new S2CCutStatePacket(partOrdinal, session.patientId, token,
                cut.depth(), !severed, severed, LimbView.of(pd, part)));
    }

    private static boolean finish(ServerPlayer operator, ServerPlayer patient, DoomedData pd, CutSession session) {
        BodyPart part = session.part;
        boolean done = DismemberService.performOn(operator, patient, pd, part, session.tool);
        if (!done) return false;
        // performOn даёт 30 траумы, ванильная самоампутация - 10. Компенсируем разницу.
        if (session.self) pd.addTrauma(-20.0f * CutBridge.toolPain(session.tool));
        // Ванильная культя кровит на 100 (2.4 L/min). Приглушаем до уровня Casualties: Unknown.
        AmputationAftercare.scaleStumpBleed(pd, part);
        CutProgress progress = CutCapability.get(patient).orElse(null);
        if (progress != null) {
            for (BodyPart distal : part.distalIncludingSelf()) progress.get(distal).reset();
        }
        BY_OPERATOR.remove(operator.getUUID());
        DoomedDataUtil.sync(patient);
        return true;
    }

    public static void stop(ServerPlayer operator, long token) {
        CutSession session = BY_OPERATOR.get(operator.getUUID());
        if (session != null && session.token == token) BY_OPERATOR.remove(operator.getUUID());
        // Прогресс НЕ сбрасываем: рез остаётся открытым и кровит.
    }

    private static boolean valid(ServerPlayer operator, ServerPlayer patient, CutSession session) {
        if (operator == null || patient == null || !operator.isAlive() || !patient.isAlive()) return false;
        if (!session.self && !DoomedConfig.crossPlayerAmputation()) return false;
        if (DismemberService.cutTool(operator.getMainHandItem()) != session.tool) return false;
        if (!HandFunctionService.canPerformTwoHanded(operator)) return false;
        if (!session.self && (operator.distanceToSqr(patient) > 36.0 || !operator.hasLineOfSight(patient))) return false;
        DoomedData pd = CutBridge.data(patient);
        return pd != null && pd.isActive() && !pd.getLimb(session.part).isDismembered();
    }

    public static void tick(ServerPlayer player) {
        DoomedData data = CutBridge.data(player);
        if (data == null || !data.isActive()) return;
        CutProgress progress = CutCapability.get(player).orElse(null);
        if (progress == null) return;
        long now = player.serverLevel().getGameTime();

        CutSession session = BY_OPERATOR.get(player.getUUID());
        if (session != null && now - session.lastInputTick > BGConfig.idleTimeoutTicks()) {
            BY_OPERATOR.remove(player.getUUID());
            session = null;
        }

        for (Map.Entry<BodyPart, LimbCut> entry : progress.all().entrySet()) {
            BodyPart part = entry.getKey();
            LimbCut cut = entry.getValue();
            if (!cut.touched()) continue;
            LimbData limb = data.getLimb(part);
            if (limb.isDismembered()) {
                cut.reset();
                continue;
            }
            cut.decayByRegen(limb.getSkinHealth(), limb.getMuscleHealth(), BGConfig.decayScale());
            CutBone.tick(data, part, cut);
            if (cut.depth() <= 0.001f && CutWounds.find(data, part, cut) == null) cut.reset();
            if (session != null && session.part == part && now % 20L == 0L) {
                BGNetwork.sendState(player, new S2CCutStatePacket(part.ordinal(), session.patientId,
                        session.token, cut.depth(), true, false, LimbView.of(data, part)));
            }
        }
    }

    public static void forget(UUID player) {
        BY_OPERATOR.remove(player);
    }
}