package ru.renat.doomedbgfix.server;

import net.mattlives.doomedmatu.body.BodyPart;
import ru.renat.doomedbgfix.eye.EyeCutModel;

import java.util.UUID;

public final class EyeCutSession {
    public final long token;
    public final UUID operator;
    public final UUID patient;
    /** Id сущности пациента для клиента, -1 если оператор оперирует себя. */
    public final int patientId;
    public final boolean self;
    public final BodyPart part;
    public final int mode;
    public final float[] seg = new float[EyeCutModel.SEGMENTS];
    public long lastInputTick;

    public EyeCutSession(long token, UUID operator, UUID patient, int patientId, boolean self,
                         BodyPart part, int mode, long tick) {
        this.token = token;
        this.operator = operator;
        this.patient = patient;
        this.patientId = patientId;
        this.self = self;
        this.part = part;
        this.mode = mode;
        this.lastInputTick = tick;
    }

    public int stage() {
        return EyeCutModel.currentStage(seg, mode);
    }
}
