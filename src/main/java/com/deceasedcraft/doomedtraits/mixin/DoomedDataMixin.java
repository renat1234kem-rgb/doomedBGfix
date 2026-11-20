package com.deceasedcraft.doomedtraits.mixin;

import com.deceasedcraft.doomedtraits.AddTrait;
import com.deceasedcraft.doomedtraits.AddTraits;
import com.deceasedcraft.doomedtraits.DoomedDataExt;
import com.deceasedcraft.doomedtraits.SkillShift;
import com.deceasedcraft.doomedtraits.server.AnorexiaStart;
import com.deceasedcraft.doomedtraits.server.MoodEffects;
import net.mattlives.doomedmatu.capability.DoomedData;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Всё, что наши черты делают внутри DoomedData: хранение mask2, сдвиги навыков,
 * опыт, настроение, отравление, опиаты.
 */
@Mixin(value = DoomedData.class, remap = false)
public abstract class DoomedDataMixin implements DoomedDataExt {
    @Unique
    private int doomedtraitsadd$mask2;

    @Override
    public int doomedtraitsadd$getMask2() {
        return this.doomedtraitsadd$mask2;
    }

    @Override
    public void doomedtraitsadd$setMask2(int mask2) {
        this.doomedtraitsadd$mask2 = mask2 & AddTraits.ALL_MASK;
    }

    private DoomedData doomedtraitsadd$self() {
        return (DoomedData) (Object) this;
    }

    // ---------- хранение ----------

    @Inject(method = "serializeNBT()Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"), remap = false)
    private void doomedtraitsadd$save(CallbackInfoReturnable<CompoundTag> cir) {
        cir.getReturnValue().putInt("doomedtraitsadd_mask2", this.doomedtraitsadd$mask2);
    }

    @Inject(method = "deserializeNBT(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("RETURN"), remap = false)
    private void doomedtraitsadd$load(CompoundTag tag, CallbackInfo ci) {
        this.doomedtraitsadd$mask2 = tag.getInt("doomedtraitsadd_mask2") & AddTraits.ALL_MASK;
    }

    @Inject(method = "copyFrom", at = @At("RETURN"), remap = false)
    private void doomedtraitsadd$copy(DoomedData other, CallbackInfo ci) {
        this.doomedtraitsadd$mask2 = ((DoomedDataExt) (Object) other).doomedtraitsadd$getMask2();
    }

    /** Если Doomed выставил черты без нашего маркера (сброс, смена режима) - наши черты тоже сбрасываются. */
    @Inject(method = "setTraits", at = @At("TAIL"), remap = false)
    private void doomedtraitsadd$afterSetTraits(int mask, CallbackInfo ci) {
        if ((mask & AddTraits.COST_MARKER) == 0) {
            this.doomedtraitsadd$mask2 = 0;
        }
    }

    // ---------- навыки: Слабоумие, Анорексия, В форме ----------

    @Inject(method = "getIntelligence", at = @At("RETURN"), cancellable = true, remap = false)
    private void doomedtraitsadd$intLevel(CallbackInfoReturnable<Float> cir) {
        if (AddTraits.active(doomedtraitsadd$self(), AddTrait.DEMENTIA)) {
            cir.setReturnValue(SkillShift.level(cir.getReturnValueF(), AddTraits.DEMENTIA_INT_LEVELS));
        }
    }

    @Inject(method = "getExpIntelligence", at = @At("RETURN"), cancellable = true, remap = false)
    private void doomedtraitsadd$intExp(CallbackInfoReturnable<Float> cir) {
        if (AddTraits.active(doomedtraitsadd$self(), AddTrait.DEMENTIA)) {
            cir.setReturnValue(SkillShift.exp(cir.getReturnValueF(), AddTraits.DEMENTIA_INT_LEVELS));
        }
    }

    @Inject(method = "getSkillStrength", at = @At("RETURN"), cancellable = true, remap = false)
    private void doomedtraitsadd$strLevel(CallbackInfoReturnable<Float> cir) {
        int shift = AddTraits.bodyShift(doomedtraitsadd$self());
        if (shift != 0) {
            cir.setReturnValue(SkillShift.level(cir.getReturnValueF(), shift));
        }
    }

    @Inject(method = "getExpStrength", at = @At("RETURN"), cancellable = true, remap = false)
    private void doomedtraitsadd$strExp(CallbackInfoReturnable<Float> cir) {
        int shift = AddTraits.bodyShift(doomedtraitsadd$self());
        if (shift != 0) {
            cir.setReturnValue(SkillShift.exp(cir.getReturnValueF(), shift));
        }
    }

    @Inject(method = "getSkillResilience", at = @At("RETURN"), cancellable = true, remap = false)
    private void doomedtraitsadd$resLevel(CallbackInfoReturnable<Float> cir) {
        int shift = AddTraits.bodyShift(doomedtraitsadd$self());
        if (shift != 0) {
            cir.setReturnValue(SkillShift.level(cir.getReturnValueF(), shift));
        }
    }

    @Inject(method = "getExpResilience", at = @At("RETURN"), cancellable = true, remap = false)
    private void doomedtraitsadd$resExp(CallbackInfoReturnable<Float> cir) {
        int shift = AddTraits.bodyShift(doomedtraitsadd$self());
        if (shift != 0) {
            cir.setReturnValue(SkillShift.exp(cir.getReturnValueF(), shift));
        }
    }

    // ---------- опыт навыков ----------

    @ModifyVariable(method = "addIntelligenceXp", at = @At("HEAD"), argsOnly = true, remap = false)
    private float doomedtraitsadd$intXp(float xp) {
        return xp * AddTraits.intXpMult(doomedtraitsadd$self());
    }

    @ModifyVariable(method = "addStrengthXp", at = @At("HEAD"), argsOnly = true, remap = false)
    private float doomedtraitsadd$strXp(float xp) {
        return xp * AddTraits.bodyXpMult(doomedtraitsadd$self());
    }

    @ModifyVariable(method = "addResilienceXp", at = @At("HEAD"), argsOnly = true, remap = false)
    private float doomedtraitsadd$resXp(float xp) {
        return xp * AddTraits.bodyXpMult(doomedtraitsadd$self());
    }

    // ---------- настроение: Оптимист ----------

    @ModifyVariable(method = "addHappiness", at = @At("HEAD"), argsOnly = true, remap = false)
    private float doomedtraitsadd$happiness(float delta) {
        DoomedData self = doomedtraitsadd$self();
        if (AddTraits.active(self, AddTrait.OPTIMIST)) {
            return delta < 0.0F ? delta * 0.70F : delta * 1.15F;
        }
        if (AddTraits.OPTIMIST_AURA.contains(self)) {
            return delta < 0.0F ? delta * 0.92F : delta * 1.05F;
        }
        return delta;
    }

    // ---------- отравление: Железный желудок ----------

    @ModifyVariable(method = "addSicknessAmount", at = @At("HEAD"), argsOnly = true, remap = false)
    private float doomedtraitsadd$sickness(float delta) {
        if (delta > 0.0F && AddTraits.active(doomedtraitsadd$self(), AddTrait.IRON_STOMACH)) {
            return delta * (AddTraits.IN_FUMES.get() ? 0.10F : 0.50F);
        }
        return delta;
    }

    // ---------- опиаты: Невосприимчивость ----------

    @Inject(method = "addOpiateDepot", at = @At("HEAD"), cancellable = true, remap = false)
    private void doomedtraitsadd$noDepot(float amount, float absorbPerSec, CallbackInfo ci) {
        if (AddTraits.active(doomedtraitsadd$self(), AddTrait.OPIATE_IMMUNITY)) {
            ci.cancel();
        }
    }

    @Inject(method = "addOralOpiate", at = @At("HEAD"), cancellable = true, remap = false)
    private void doomedtraitsadd$noOral(float amount, CallbackInfo ci) {
        if (AddTraits.active(doomedtraitsadd$self(), AddTrait.OPIATE_IMMUNITY)) {
            ci.cancel();
        }
    }

    // ---------- временное настроение и жажда убийства ----------

    @ModifyVariable(method = "setMood", at = @At("HEAD"), argsOnly = true, remap = false)
    private float doomedtraitsadd$tempMood(float mood) {
        float off = MoodEffects.tempMoodOffset(doomedtraitsadd$self());
        return off == 0.0F ? mood : Math.max(-100.0F, Math.min(100.0F, mood + off));
    }

    @Inject(method = "getSystolicBP", at = @At("RETURN"), cancellable = true, remap = false)
    private void doomedtraitsadd$bloodlustBp(CallbackInfoReturnable<Float> cir) {
        float bonus = MoodEffects.bloodlustBp(doomedtraitsadd$self());
        if (bonus != 0.0F) {
            cir.setReturnValue(cir.getReturnValueF() + bonus);
        }
    }

    @Inject(method = "getHeartRate", at = @At("RETURN"), cancellable = true, remap = false)
    private void doomedtraitsadd$bloodlustHr(CallbackInfoReturnable<Float> cir) {
        float bonus = MoodEffects.bloodlustHeartRate(doomedtraitsadd$self());
        if (bonus != 0.0F) {
            cir.setReturnValue(cir.getReturnValueF() + bonus);
        }
    }

    // ---------- новое тело после смерти ----------

    @Inject(method = "resetVitals", at = @At("TAIL"), remap = false)
    private void doomedtraitsadd$afterResetVitals(CallbackInfo ci) {
        MoodEffects.forget(doomedtraitsadd$self());
        AnorexiaStart.apply(doomedtraitsadd$self());
    }
}
