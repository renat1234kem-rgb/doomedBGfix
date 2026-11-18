package ru.renat.doomedbgfix.cut;

import net.mattlives.doomedmatu.body.BodyPart;
import net.minecraft.nbt.CompoundTag;

import java.util.EnumMap;
import java.util.Map;

public final class CutProgress {
    private final Map<BodyPart, LimbCut> cuts = new EnumMap<>(BodyPart.class);

    public LimbCut get(BodyPart part) { return cuts.computeIfAbsent(part, k -> new LimbCut()); }
    public Map<BodyPart, LimbCut> all() { return cuts; }

    public void copyFrom(CutProgress other) {
        cuts.clear();
        for (Map.Entry<BodyPart, LimbCut> e : other.cuts.entrySet()) {
            get(e.getKey()).load(e.getValue().save());
        }
    }

    public CompoundTag save() {
        CompoundTag root = new CompoundTag();
        for (Map.Entry<BodyPart, LimbCut> e : cuts.entrySet()) {
            if (e.getValue().touched() || e.getValue().woundId() != 0L) {
                root.put(e.getKey().name(), e.getValue().save());
            }
        }
        return root;
    }

    public void load(CompoundTag root) {
        cuts.clear();
        for (String key : root.getAllKeys()) {
            BodyPart part;
            try {
                part = BodyPart.valueOf(key);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            get(part).load(root.getCompound(key));
        }
    }
}
