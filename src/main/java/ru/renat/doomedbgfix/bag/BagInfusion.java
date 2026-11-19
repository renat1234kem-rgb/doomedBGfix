package ru.renat.doomedbgfix.bag;

import net.mattlives.doomedmatu.body.BloodType;
import net.mattlives.doomedmatu.body.BodyPart;
import net.mattlives.doomedmatu.body.Consumption;
import net.mattlives.doomedmatu.body.ContagionHandler;
import net.mattlives.doomedmatu.body.DoomedBlood;
import net.mattlives.doomedmatu.body.DrugType;
import net.mattlives.doomedmatu.body.ForeignBodyKind;
import net.mattlives.doomedmatu.body.OripathyHandler;
import net.mattlives.doomedmatu.body.PathogenType;
import net.mattlives.doomedmatu.body.TransfusionReactionHandler;
import net.mattlives.doomedmatu.body.VesselInvolvement;
import net.mattlives.doomedmatu.body.WoundLedgerService;
import net.mattlives.doomedmatu.body.WoundType;
import net.mattlives.doomedmatu.capability.DoomedData;
import net.mattlives.doomedmatu.capability.DoomedDataUtil;
import net.mattlives.doomedmatu.item.BloodBagItem;
import net.mattlives.doomedmatu.item.FluidSyringeItem;
import net.mattlives.doomedmatu.item.SalineItem;
import net.mattlives.doomedmatu.registry.DoomedItems;
import net.mattlives.doomedmatu.registry.DoomedSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import ru.renat.doomedbgfix.server.CutBridge;

/**
 * Замена ванильной ветки DrugService.injectFluid для пакетов.
 * Оригинал всегда делал bag.shrink(1) и писал "The rest is spoiled".
 * Здесь списывается только реально влитый объём, физиология считается по той же
 * формуле (0.75 Л * frac для крови, 0.5 Л * frac для физраствора).
 */
public final class BagInfusion {

    private BagInfusion() {}

    /** true, если этот укол идёт из пакета, а не из шприца. */
    public static boolean handles(ServerPlayer healer, DrugType drug) {
        if (drug == DrugType.BLOOD) return find(healer, drug) != null;
        if (drug != DrugType.SALINE) return false;
        return held(healer) != null;
    }

    private static ItemStack held(ServerPlayer healer) {
        ItemStack main = healer.getMainHandItem();
        if (main.is(DoomedItems.SALINE.get())) return main;
        ItemStack off = healer.getOffhandItem();
        if (off.is(DoomedItems.SALINE.get())) return off;
        return null;
    }

    /** Ищем сначала уже начатый пакет, чтобы доливать его, а не вскрывать новый. */
    public static ItemStack find(ServerPlayer healer, DrugType drug) {
        ItemStack main = healer.getMainHandItem();
        if (matches(main, drug)) return main;
        ItemStack off = healer.getOffhandItem();
        if (matches(off, drug)) return off;
        Inventory inv = healer.getInventory();
        ItemStack fresh = null;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!matches(s, drug)) continue;
            if (BagVolume.get(s) < BagVolume.FULL) return s;
            if (fresh == null) fresh = s;
        }
        return fresh;
    }

    private static boolean matches(ItemStack s, DrugType drug) {
        if (s.isEmpty()) return false;
        return drug == DrugType.BLOOD ? s.getItem() instanceof BloodBagItem : s.is(DoomedItems.SALINE.get());
    }

    public static void inject(ServerPlayer healer, ServerPlayer patient, BodyPart part, DrugType drug, float requestedMl) {
        DoomedData data = CutBridge.data(patient);
        if (data == null || !data.isActive() || part == null || data.getLimb(part).isDismembered()) return;

        ItemStack bag = find(healer, drug);
        if (bag == null) {
            healer.displayClientMessage(Component.literal("You have no " + (drug == DrugType.BLOOD ? "blood" : "saline") + "."), true);
            return;
        }

        int use = Math.min(Math.max(0, Math.round(requestedMl)), BagVolume.get(bag));
        if (use <= 0) return;
        float frac = use / (float) BagVolume.FULL;

        if (drug == DrugType.SALINE) {
            SalineItem.settleAge(healer.level(), bag, 1.0f);
            if (SalineItem.isExpired(bag)) {
                BagVolume.consume(healer, data, bag, BagVolume.get(bag));
                healer.displayClientMessage(Component.literal("That bag was opened a day ago. It has gone cloudy."), true);
                DoomedDataUtil.sync(patient);
                return;
            }
            SalineItem.open(healer.level(), bag);
        } else {
            BloodBagItem.settleAge(healer.level(), bag, 1.0f);
            if (BloodBagItem.isExpired(bag)) return;
        }

        // след от иглы - маленький прокол, как при обычной инъекции
        WoundLedgerService.create(data, part, WoundType.PUNCTURE,
                part.ordinal() * 7919L + patient.getUUID().getLeastSignificantBits(),
                0.5f, 0.0f, 0.3f, 0.1f, 0.0f, 0.0f,
                ForeignBodyKind.NONE, 0, VesselInvolvement.NONE);

        if (drug == DrugType.BLOOD) {
            if (FluidSyringeItem.isPhysicallyContaminated(bag)) {
                data.getLimb(part).setInfection(Math.min(100f, data.getLimb(part).getInfection() + 12f));
                patient.displayClientMessage(Component.literal("That bag has been used before."), true);
            }
            int carried = FluidSyringeItem.bloodPathogenMask(bag);
            OripathyHandler.transfusion(patient, data, part, carried);
            for (PathogenType p : PathogenType.VALUES) {
                if ((carried & (1 << p.ordinal())) != 0) ContagionHandler.bloodExposure(patient, data, p, 1.0f);
            }
            BloodType donor = BloodBagItem.ensureType(bag, healer.getRandom());
            data.addWholeBlood(0.75f * frac);
            BloodType recipient = data.getBloodType() == BloodType.UNKNOWN
                    ? BloodType.fromUuid(patient.getUUID()) : data.getBloodType();
            data.setBloodType(recipient);
            float divergence = DoomedBlood.divergenceSeverity(DoomedBlood.factor(patient));
            float clash = donor.mismatchSeverity(recipient);
            float raw = Math.min(100f, clash + divergence);
            float mismatch = raw * frac;
            if (mismatch > 0f) {
                TransfusionReactionHandler.start(patient, data, mismatch, 0.75f * frac * (1.2f + raw / 100f));
            }
        } else {
            data.setBloodVolume(data.getBloodVolume() + 0.5f * frac);
            data.setBloodViscosity(data.getBloodViscosity() - 40f * frac);
            Consumption.drink(patient, data, 60f * frac);
        }

        ItemStack before = bag;
        BagVolume.consume(healer, data, bag, use);
        if (!before.isEmpty() && BagVolume.get(before) > 0) {
            healer.displayClientMessage(Component.literal(
                    "Pushed " + use + "% - " + BagVolume.realMl(before) + " ml left in the bag."), true);
        }

        patient.level().playSound(null, patient.getX(), patient.getY(), patient.getZ(),
                DoomedSounds.SYRINGE.get(), SoundSource.PLAYERS, 0.6f, 0.9f);
        DoomedDataUtil.sync(patient);
        if (healer != patient) DoomedDataUtil.sync(healer);
    }
}
