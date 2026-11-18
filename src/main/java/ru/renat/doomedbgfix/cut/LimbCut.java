package ru.renat.doomedbgfix.cut;

import net.minecraft.nbt.CompoundTag;

/** Персистентное состояние реза одной конечности. */
public final class LimbCut {
    private float depth;
    private long lastTouchTick;
    private long woundId;
    private float lastSkin = -1f;
    private float lastMuscle = -1f;

    public float depth()        { return depth; }
    public long lastTouchTick() { return lastTouchTick; }
    public long woundId()       { return woundId; }
    public boolean touched()    { return depth > 0f; }
    public boolean severed()    { return depth >= 1f; }
    public boolean inBone()     { return depth >= CutLayer.BONE.start; }
    public float boneProgress() { return CutMath.boneProgress(depth); }

    public void setDepth(float d, long tick) {
        this.depth = CutLayer.clamp01(d);
        if (this.depth > 0f) this.lastTouchTick = tick;
    }

    public void setWoundId(long id) { this.woundId = Math.max(0L, id); }

    public void decayByRegen(float skinNow, float muscleNow, float scale) {
        if (lastSkin >= 0f) {
            float regen = Math.max(0f, skinNow - lastSkin) + Math.max(0f, muscleNow - lastMuscle);
            if (regen > 0f) depth = Math.max(0f, depth - regen / 200f * scale);
        }
        lastSkin = skinNow;
        lastMuscle = muscleNow;
    }

    public void syncBone(float boneProgress) {
        float target = CutLayer.BONE.start + CutLayer.clamp01(boneProgress) * CutLayer.BONE.span();
        if (depth > CutLayer.BONE.start && target < depth) depth = target;
    }

    public void clampToBoneStart() {
        if (depth > CutLayer.BONE.start) depth = CutLayer.BONE.start;
    }

    public void reset() {
        depth = 0f; woundId = 0L; lastSkin = -1f; lastMuscle = -1f; lastTouchTick = 0L;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putFloat("depth", depth);
        t.putLong("touch", lastTouchTick);
        t.putLong("wound", woundId);
        return t;
    }

    public void load(CompoundTag t) {
        depth = CutLayer.clamp01(t.getFloat("depth"));
        lastTouchTick = t.getLong("touch");
        woundId = Math.max(0L, t.getLong("wound"));
        lastSkin = -1f;
        lastMuscle = -1f;
    }
}
