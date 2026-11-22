package ru.renat.doomedbgfix.server;

import net.mattlives.doomedmatu.body.BodyPart;
import ru.renat.doomedbgfix.eye.EyeCutModel;

import java.util.UUID;

public final class EyeCutSession {
    public final long token;
    public final UUID operator;
    public final BodyPart part;
    public final float[] seg = new float[EyeCutModel.SEGMENTS];
    public int stage = EyeCutModel.STAGE_RIM;
    public long lastInputTick;

    public EyeCutSession(long token, UUID operator, BodyPart part, long tick) {
        this.token = token;
        this.operator = operator;
        this.part = part;
        this.lastInputTick = tick;
    }
}
