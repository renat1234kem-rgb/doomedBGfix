package ru.renat.doomedbgfix.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class BGConfig {
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue ENABLE_REWORK;
    private static final ForgeConfigSpec.BooleanValue DROP_TOURNIQUET;
    private static final ForgeConfigSpec.DoubleValue WORK_PER_SECOND;
    private static final ForgeConfigSpec.DoubleValue BLEED_SCALE;
    private static final ForgeConfigSpec.DoubleValue PAIN_SCALE;
    private static final ForgeConfigSpec.DoubleValue DECAY_SCALE;
    private static final ForgeConfigSpec.DoubleValue BONE_RESISTANCE_SCALE;
    private static final ForgeConfigSpec.DoubleValue MAX_WORK_PER_PACKET;
    private static final ForgeConfigSpec.IntValue IDLE_TIMEOUT_TICKS;
    private static final ForgeConfigSpec.BooleanValue FORCE_DEEP_WOUND;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Doomed BG Fix - amputation rework").push("cut");
        ENABLE_REWORK = b.comment("Replace the vanilla amputation minigame. false = vanilla behaviour.")
                .define("enableRework", true);
        WORK_PER_SECOND = b.comment("Global saw speed. Vanilla: ~2.21 work total, 0.08 per full stroke.")
                .defineInRange("workPerSecond", 0.075D, 0.01D, 0.5D);
        BLEED_SCALE = b.comment("Bleeding while cutting. Final stump bleed stays vanilla (100).")
                .defineInRange("bleedScale", 1.0D, 0.0D, 3.0D);
        PAIN_SCALE = b.comment("Pain while cutting.").defineInRange("painScale", 1.0D, 0.0D, 3.0D);
        DECAY_SCALE = b.comment("How fast an abandoned cut closes. 1.0 = 1:1 with limb regeneration.")
                .defineInRange("decayScale", 2.0D, 0.0D, 20.0D);
        BONE_RESISTANCE_SCALE = b.comment("Bone hardness. 1.0 = vanilla resistance 4.5.")
                .defineInRange("boneResistanceScale", 1.0D, 0.2D, 4.0D);
        MAX_WORK_PER_PACKET = b.comment("Anti-cheat clamp per client packet.")
                .defineInRange("maxWorkPerPacket", 0.25D, 0.05D, 2.0D);
        IDLE_TIMEOUT_TICKS = b.comment("Session closes after this many idle ticks. Progress is NEVER lost.")
                .defineInRange("idleTimeoutTicks", 6000, 200, 72000);
        FORCE_DEEP_WOUND = b.comment("Keep the cut registered as a deep wound so it can be stitched immediately.")
                .define("forceDeepWound", true);
        b.pop();
        b.push("fixes");
        DROP_TOURNIQUET = b.comment("Drop the tourniquet as an item when the limb it sits on is removed.")
                .define("dropTourniquetOnLimbLoss", true);
        b.pop();
        SPEC = b.build();
    }

    private BGConfig() {}
    public static boolean enableRework()      { return ENABLE_REWORK.get(); }
    public static boolean dropTourniquet()    { return DROP_TOURNIQUET.get(); }
    public static float workPerSecond()       { return (float) (double) WORK_PER_SECOND.get(); }
    public static float bleedScale()          { return (float) (double) BLEED_SCALE.get(); }
    public static float painScale()           { return (float) (double) PAIN_SCALE.get(); }
    public static float decayScale()          { return (float) (double) DECAY_SCALE.get(); }
    public static float boneResistanceScale() { return (float) (double) BONE_RESISTANCE_SCALE.get(); }
    public static float maxWorkPerPacket()    { return (float) (double) MAX_WORK_PER_PACKET.get(); }
    public static int idleTimeoutTicks()      { return IDLE_TIMEOUT_TICKS.get(); }
    public static boolean forceDeepWound()    { return FORCE_DEEP_WOUND.get(); }
}
