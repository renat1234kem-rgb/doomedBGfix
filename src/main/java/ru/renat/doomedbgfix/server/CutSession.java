package ru.renat.doomedbgfix.server;

import net.mattlives.doomedmatu.body.BodyPart;
import net.mattlives.doomedmatu.body.DismemberService;

import java.util.UUID;

public final class CutSession {
    public final long token;
    public final UUID operator;
    public final UUID patient;
    public final int patientId;
    public final BodyPart part;
    public final DismemberService.CutTool tool;
    public final boolean self;
    public long lastInputTick;

    public CutSession(long token, UUID operator, UUID patient, int patientId, BodyPart part,
                      DismemberService.CutTool tool, boolean self, long tick) {
        this.token = token;
        this.operator = operator;
        this.patient = patient;
        this.patientId = patientId;
        this.part = part;
        this.tool = tool;
        this.self = self;
        this.lastInputTick = tick;
    }
}
