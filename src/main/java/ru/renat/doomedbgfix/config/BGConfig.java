package ru.renat.doomedbgfix.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;
import java.util.stream.Collectors;

public final class BGConfig {
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue ENABLE_REWORK;
    private static final ForgeConfigSpec.BooleanValue DROP_TOURNIQUET;
    private static final ForgeConfigSpec.BooleanValue BAG_PARTIAL;
    private static final ForgeConfigSpec.BooleanValue BARREL_FILL;
    private static final ForgeConfigSpec.IntValue BARREL_MB;
    private static final ForgeConfigSpec.DoubleValue WORK_PER_SECOND;
    private static final ForgeConfigSpec.DoubleValue BLEED_SCALE;
    private static final ForgeConfigSpec.DoubleValue PAIN_SCALE;
    private static final ForgeConfigSpec.DoubleValue SOFT_PAIN_SCALE;
    private static final ForgeConfigSpec.DoubleValue DECAY_SCALE;
    private static final ForgeConfigSpec.DoubleValue BONE_RESISTANCE_SCALE;
    private static final ForgeConfigSpec.DoubleValue MAX_WORK_PER_PACKET;
    private static final ForgeConfigSpec.IntValue IDLE_TIMEOUT_TICKS;
    private static final ForgeConfigSpec.IntValue SAW_SOUND_MS;
    private static final ForgeConfigSpec.BooleanValue FORCE_DEEP_WOUND;
    private static final ForgeConfigSpec.BooleanValue PHANTOM_NERF;
    private static final ForgeConfigSpec.DoubleValue PHANTOM_SUS_CAP;
    private static final ForgeConfigSpec.DoubleValue PHANTOM_PAIN_CAP;
    private static final ForgeConfigSpec.IntValue PHANTOM_EPISODE_SECONDS;
    private static final ForgeConfigSpec.DoubleValue STUMP_BLEED_SCALE;
    private static final ForgeConfigSpec.DoubleValue RESIDUAL_PAIN_CAP;
    private static final ForgeConfigSpec.DoubleValue STUMP_HEAL_BOOST;
    private static final ForgeConfigSpec.BooleanValue OWN_DRAW;
    private static final ForgeConfigSpec.DoubleValue DONATE_FLOOR;
    private static final ForgeConfigSpec.DoubleValue DONATE_RBC;
    private static final ForgeConfigSpec.DoubleValue DONATE_LITRES;
    private static final ForgeConfigSpec.BooleanValue CLEAN_REFILL;
    private static final ForgeConfigSpec.BooleanValue BANDAGE_PROGRESSIVE;
    private static final ForgeConfigSpec.DoubleValue BANDAGE_CAPACITY;
    private static final ForgeConfigSpec.DoubleValue BANDAGE_FULL_BONUS;
    private static final ForgeConfigSpec.DoubleValue BANDAGE_QUALITY_DEFAULT;
    private static final ForgeConfigSpec.DoubleValue DRESSED_RESIDUAL;
    private static final ForgeConfigSpec.ConfigValue<List<? extends String>> BANDAGE_PROFILES;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Doomed BG Fix - amputation rework").push("cut");
        ENABLE_REWORK = b.comment("Replace the vanilla amputation minigame. false = vanilla behaviour.")
                .define("enableRework", true);
        WORK_PER_SECOND = b.comment("Global saw speed. Vanilla: ~2.21 work total, 0.08 per full stroke.")
                .defineInRange("workPerSecond", 0.075D, 0.01D, 0.5D);
        BLEED_SCALE = b.comment("Extra multiplier on the in-progress bleed curve (peak 36 units).")
                .defineInRange("bleedScale", 1.0D, 0.0D, 3.0D);
        PAIN_SCALE = b.comment("Extra multiplier on the whole pain formula.")
                .defineInRange("painScale", 1.0D, 0.0D, 3.0D);
        SOFT_PAIN_SCALE = b.comment("Pain multiplier for skin/fat/muscle only. Bone is untouched.")
                .defineInRange("softTissuePainScale", 2.5D, 0.1D, 8.0D);
        DECAY_SCALE = b.comment("How fast an abandoned cut closes. 1.0 = 1:1 with limb regeneration.")
                .defineInRange("decayScale", 2.0D, 0.0D, 20.0D);
        BONE_RESISTANCE_SCALE = b.comment("Bone hardness. 1.0 = vanilla resistance 4.5.")
                .defineInRange("boneResistanceScale", 1.0D, 0.2D, 4.0D);
        MAX_WORK_PER_PACKET = b.comment("Anti-cheat clamp per client packet.")
                .defineInRange("maxWorkPerPacket", 0.25D, 0.05D, 2.0D);
        IDLE_TIMEOUT_TICKS = b.comment("Session closes after this many idle ticks. Progress is NEVER lost.")
                .defineInRange("idleTimeoutTicks", 6000, 200, 72000);
        SAW_SOUND_MS = b.comment("Minimum gap between saw sounds, milliseconds. 0 = no cooldown.")
                .defineInRange("sawSoundCooldownMs", 320, 0, 2000);
        FORCE_DEEP_WOUND = b.comment("Keep the cut registered as a deep wound so it can be stitched immediately.")
                .define("forceDeepWound", true);
        b.pop();

        b.comment("Vanilla phantom pain: 18 + susceptibility(52..88) * 0.35..0.65 = 36..70.")
                .push("phantom");
        PHANTOM_NERF = b.define("phantomNerf", true);
        PHANTOM_SUS_CAP = b.defineInRange("phantomSusceptibilityCap", 24.0D, 0.0D, 88.0D);
        PHANTOM_PAIN_CAP = b.defineInRange("phantomPainCap", 12.0D, 0.0D, 100.0D);
        PHANTOM_EPISODE_SECONDS = b.defineInRange("phantomEpisodeSeconds", 8, 1, 60);
        b.pop();

        b.comment("Stump aftercare. The stump wound lives on the PROXIMAL segment.")
                .push("aftercare");
        STUMP_BLEED_SCALE = b.comment("Ceiling for stump bleed, as a fraction of vanilla 100.",
                        "0.28 -> ~1.2 L/min. 1.0 = vanilla (2.4 L/min).")
                .defineInRange("stumpBleedScale", 0.28D, 0.0D, 1.0D);
        RESIDUAL_PAIN_CAP = b.comment("Ceiling for residual stump pain. 100 = vanilla.")
                .defineInRange("residualPainCap", 40.0D, 0.0D, 100.0D);
        STUMP_HEAL_BOOST = b.comment("Extra stump healing per second, works while bleeding at 35% rate.")
                .defineInRange("stumpHealBoost", 3.0D, 0.0D, 20.0D);
        b.pop();

        b.comment("Bandages, calibrated after Casualties: Unknown.",
                        "1 Doomed bleed unit ~ 0.024 L/min (measured: 100 units = 2.40 L/min).")
                .push("bandage");
        BANDAGE_PROGRESSIVE = b.comment("Enable per-wrap fixed bleeding stop. false = vanilla all-or-nothing.")
                .define("progressiveBandageBleed", true);
        BANDAGE_CAPACITY = b.comment("Bleeding units stopped by a FULL wrap of a quality-1.0 dressing.",
                        "28 units ~ 0.67 L/min, i.e. the plain Doomed bandage in CU terms.")
                .defineInRange("bandageCapacityBase", 28.0D, 0.0D, 200.0D);
        BANDAGE_FULL_BONUS = b.comment("Extra units stopped instantly at 100% wrap, on top of the",
                        "vanilla percentage effect. 0 = vanilla bonus only.")
                .defineInRange("fullWrapBonusUnits", 0.0D, 0.0D, 100.0D);
        BANDAGE_QUALITY_DEFAULT = b.comment("Quality for dressing items not listed in bandageProfiles.")
                .defineInRange("bandageQualityDefault", 1.0D, 0.05D, 10.0D);
        DRESSED_RESIDUAL = b.comment("Residual bleed handling for ALREADY DRESSED wounds, in bleed units.",
                        "At or below this value the seep under the dressing clots to 0 once per second.",
                        "Above it the wound stays re-bandageable (second bandage on top).",
                        "4 units ~ 0.1 L/min. 0 disables both behaviours.")
                .defineInRange("dressedResidualBleedUnits", 4.0D, 0.0D, 50.0D);
        BANDAGE_PROFILES = b.comment("Quality per dressing (registry path). Multiplies stop power AND capacity.")
                .defineList("bandageProfiles", List.of(
                        "alginate=1.6",
                        "plasticbandage=1.6",
                        "analgesicgauze=1.1",
                        "sterilizedbandage=1.0",
                        "bandage=1.0",
                        "rippeddressing=0.4",
                        "adhesivebandage=0.15"
                ), o -> o instanceof String);
        b.pop();

        b.push("fixes");
        DROP_TOURNIQUET = b.comment("Drop the tourniquet when the limb it sits on is removed.")
                .define("dropTourniquetOnLimbLoss", true);
        BAG_PARTIAL = b.comment("Bags keep remaining volume instead of being destroyed on first use.")
                .define("partialBagUse", true);
        BARREL_FILL = b.comment("RMB empty/part-used bag on a saline barrel to fill or top up.")
                .define("barrelFill", true);
        BARREL_MB = b.comment("Millibuckets drained for one full bag.")
                .defineInRange("barrelMbPerBag", 500, 100, 4000);
        OWN_DRAW = b.comment("Handle drawing blood into an empty bag ourselves, with explicit refusal messages.")
                .define("ownBloodDraw", true);
        DONATE_FLOOR = b.comment("Blood volume that must REMAIN after drawing, litres.")
                .defineInRange("donateBloodFloor", 2.5D, 0.5D, 4.5D);
        DONATE_RBC = b.comment("Minimum red cell fraction to donate (vanilla 0.8).")
                .defineInRange("donateRbcMin", 0.5D, 0.1D, 1.0D);
        DONATE_LITRES = b.comment("Litres drawn per bag. Keep 0.75 so it matches a full blood bag.")
                .defineInRange("donateLitres", 0.75D, 0.1D, 1.5D);
        CLEAN_REFILL = b.comment("Clear the 'used before' contamination flag on a refilled bag.")
                .define("cleanRefilledBag", true);
        b.pop();
        SPEC = b.build();
    }

    private BGConfig() {}
    public static boolean enableRework()      { return ENABLE_REWORK.get(); }
    public static boolean dropTourniquet()    { return DROP_TOURNIQUET.get(); }
    public static boolean bagPartialUse()     { return BAG_PARTIAL.get(); }
    public static boolean barrelFill()        { return BARREL_FILL.get(); }
    public static int barrelMbPerBag()        { return BARREL_MB.get(); }
    public static boolean ownBloodDraw()      { return OWN_DRAW.get(); }
    public static float donateBloodFloor()    { return (float) (double) DONATE_FLOOR.get(); }
    public static float donateRbcMin()        { return (float) (double) DONATE_RBC.get(); }
    public static float donateLitres()        { return (float) (double) DONATE_LITRES.get(); }
    public static boolean cleanRefilledBag()  { return CLEAN_REFILL.get(); }
    public static float workPerSecond()       { return (float) (double) WORK_PER_SECOND.get(); }
    public static float bleedScale()          { return (float) (double) BLEED_SCALE.get(); }
    public static float painScale()           { return (float) (double) PAIN_SCALE.get(); }
    public static float softTissuePainScale() { return (float) (double) SOFT_PAIN_SCALE.get(); }
    public static float decayScale()          { return (float) (double) DECAY_SCALE.get(); }
    public static float boneResistanceScale() { return (float) (double) BONE_RESISTANCE_SCALE.get(); }
    public static float maxWorkPerPacket()    { return (float) (double) MAX_WORK_PER_PACKET.get(); }
    public static int idleTimeoutTicks()      { return IDLE_TIMEOUT_TICKS.get(); }
    public static int sawSoundCooldownMs()    { return SAW_SOUND_MS.get(); }
    public static boolean forceDeepWound()    { return FORCE_DEEP_WOUND.get(); }
    public static boolean phantomNerf()       { return PHANTOM_NERF.get(); }
    public static float phantomSusceptibilityCap() { return (float) (double) PHANTOM_SUS_CAP.get(); }
    public static float phantomPainCap()      { return (float) (double) PHANTOM_PAIN_CAP.get(); }
    public static int phantomEpisodeSeconds() { return PHANTOM_EPISODE_SECONDS.get(); }
    public static float stumpBleedScale()     { return (float) (double) STUMP_BLEED_SCALE.get(); }
    public static float residualPainCap()     { return (float) (double) RESIDUAL_PAIN_CAP.get(); }
    public static float stumpHealBoost()      { return (float) (double) STUMP_HEAL_BOOST.get(); }
    public static boolean bandageProgressive()      { return BANDAGE_PROGRESSIVE.get(); }
    public static float bandageCapacityBase()       { return (float) (double) BANDAGE_CAPACITY.get(); }
    public static float bandageFullWrapBonusUnits() { return (float) (double) BANDAGE_FULL_BONUS.get(); }
    public static float bandageQualityDefault()     { return (float) (double) BANDAGE_QUALITY_DEFAULT.get(); }
    public static float dressedResidualBleedUnits() { return (float) (double) DRESSED_RESIDUAL.get(); }
    public static List<String> bandageProfiles() {
        return BANDAGE_PROFILES.get().stream().map(Object::toString).collect(Collectors.toList());
    }
}