package ru.renat.doomedbgfix.server;

import net.mattlives.doomedmatu.body.DismemberService;
import net.mattlives.doomedmatu.body.LimbData;
import net.mattlives.doomedmatu.body.PhysiologyFactors;
import net.mattlives.doomedmatu.capability.DoomedData;
import net.mattlives.doomedmatu.capability.DoomedDataUtil;
import net.mattlives.doomedmatu.config.DoomedConfig;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/** Прослойка над API Doomed: всё version-sensitive живёт здесь. */
public final class CutBridge {
    private CutBridge() {}

    public static DoomedData data(Player player) {
        return player == null ? null : DoomedDataUtil.get(player).orElse(null);
    }

    public static boolean saw(DismemberService.CutTool tool) { return tool == DismemberService.CutTool.SAW; }

    /** Совпадает с ванильными CutTool.painAndTrauma / stumpMuscle / extraContamination. */
    public static float toolPain(DismemberService.CutTool tool) { return saw(tool) ? 1.0f : 1.9f; }
    public static float toolBleed(DismemberService.CutTool tool) { return saw(tool) ? 1.0f : 1.35f; }
    public static float toolContamination(DismemberService.CutTool tool) { return saw(tool) ? 0.0f : 55.0f; }

    /** Тот же порог срастания, что использует LimbHandler. */
    public static int requiredBoneTicks(DoomedData data, LimbData limb) {
        int base = limb.isSplinted() ? 1500 : 36000;
        float mult = (float) DoomedConfig.boneMendRateMultiplier() * PhysiologyFactors.boneRepair(data);
        if (limb.isSplinted() && !limb.isSplintPadded()) mult *= 0.5f;
        double pace = DoomedConfig.paceFactor();
        double scaled = Math.ceil(base / Math.max(1.0E-4D, pace * mult));
        if (scaled >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        return Math.max(1, (int) scaled);
    }

    public static float analgesia(DoomedData data) {
        float opiate = Mth.clamp((float) (data.getActualOpiateReception() / 90.0f), 0.0f, 0.72f);
        float nsaid = Mth.clamp((float) (data.getNsaidLevel() / 280.0f), 0.0f, 0.28f);
        return Mth.clamp(opiate + nsaid, 0.0f, 0.85f);
    }
}
