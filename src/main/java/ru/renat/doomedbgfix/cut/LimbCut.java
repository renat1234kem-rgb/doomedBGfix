package ru.renat.doomedbgfix.cut;

import net.minecraft.nbt.CompoundTag;

/** Персистентное состояние реза одной конечности. */
public final class LimbCut {

    private float depth;
    private long lastTouchTick;
    private long woundId;
    private boolean boneThrough;
    private float lastSkin = -1f;
    private float lastMuscle = -1f;

    public float depth()        { return depth; }
    public long lastTouchTick() { return lastTouchTick; }
    public long woundId()       { return woundId; }
    public boolean touched()    { return depth > 0f; }
    public boolean severed()    { return depth >= 1f; }
    public boolean inBone()     { return depth >= CutLayer.BONE.start; }
    public float boneProgress() { return CutMath.boneProgress(depth); }

    /** Кость пропилена насквозь — обратно она уже не отрастает. */
    public boolean boneThrough() { return boneThrough; }

    /** Ниже этого предела прогресс откатиться не может. */
    private float floor() { return boneThrough ? CutLayer.BONE.end : 0f; }

    public void setDepth(float d, long tick) {
        float v = CutLayer.clamp01(d);
        if (v >= CutLayer.BONE.end) boneThrough = true;
        this.depth = Math.max(floor(), v);
        if (this.depth > 0f) this.lastTouchTick = tick;
    }

    public void setWoundId(long id) { this.woundId = Math.max(0L, id); }

    public void decayByRegen(float skinNow, float muscleNow, float scale) {
        if (lastSkin >= 0f) {
            float regen = Math.max(0f, skinNow - lastSkin) + Math.max(0f, muscleNow - lastMuscle);
            if (regen > 0f) depth = Math.max(floor(), depth - regen / 200f * scale);
        }
        lastSkin = skinNow;
        lastMuscle = muscleNow;
    }

    /** Откат только внутри костного слоя и только пока кость не пропилена насквозь. */
    public void syncBone(float boneProgress) {
        if (boneThrough) return;
        float target = CutLayer.BONE.start + CutLayer.clamp01(boneProgress) * CutLayer.BONE.span();
        if (depth > CutLayer.BONE.start && target < depth) {
            depth = Math.max(CutLayer.BONE.start, target);
        }
    }

    public void clampToBoneStart() {
        if (!boneThrough && depth > CutLayer.BONE.start) depth = CutLayer.BONE.start;
    }

    public void reset() {
        depth = 0f; woundId = 0L; boneThrough = false;
        lastSkin = -1f; lastMuscle = -1f; lastTouchTick = 0L;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putFloat("depth", depth);
        t.putLong("touch", lastTouchTick);
        t.putLong("wound", woundId);
        t.putBoolean("boneThrough", boneThrough);
        return t;
    }

    public void load(CompoundTag t) {
        depth = CutLayer.clamp01(t.getFloat("depth"));
        lastTouchTick = t.getLong("touch");
        woundId = Math.max(0L, t.getLong("wound"));
        boneThrough = t.getBoolean("boneThrough") || depth >= CutLayer.BONE.end;
        if (boneThrough && depth < CutLayer.BONE.end) depth = CutLayer.BONE.end;
        lastSkin = -1f;
        lastMuscle = -1f;
    }
}