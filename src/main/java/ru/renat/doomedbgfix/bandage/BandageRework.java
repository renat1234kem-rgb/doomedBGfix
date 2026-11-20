package ru.renat.doomedbgfix.bandage;

import net.mattlives.doomedmatu.body.BodyPart;
import net.mattlives.doomedmatu.body.DressingVisual;
import net.mattlives.doomedmatu.body.LimbData;
import net.mattlives.doomedmatu.body.WoundLedgerService;
import net.mattlives.doomedmatu.body.WoundRecord;
import net.mattlives.doomedmatu.capability.DoomedData;
import net.mattlives.doomedmatu.capability.DoomedDataUtil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import ru.renat.doomedbgfix.config.BGConfig;
import ru.renat.doomedbgfix.server.CutBridge;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Прогрессивная остановка кровотечения повязками (модель Casualties: Unknown)
 * + уход за уже одетыми ранами:
 *  - одетая рана с остатком крови <= порога досворачивается до нуля раз в секунду;
 *  - одетая рана с кровью выше порога остаётся бинтуемой (см. WoundLedgerServiceBandageMixin).
 */
public final class BandageRework {

    /** Минимальная кровь, до которой опускает рану прогрессивная остановка. */
    public static final float FLOOR = 0.25f;

    private static final Map<String, Float> LAST_PROGRESS = new ConcurrentHashMap<>();
    private static final Map<String, Float> SPENT = new ConcurrentHashMap<>();

    private BandageRework() {}

    public static void onBandageProgress(ServerPlayer healer, ServerPlayer patient, BodyPart part,
                                         float targetProgress, boolean finished, int itemSlot) {
        if (!BGConfig.bandageProgressive()) return;
        if (patient == null) return;

        DoomedData data = CutBridge.data(patient);
        if (data == null || !data.isActive()) return;
        LimbData limb = data.getLimb(part);
        if (limb == null || limb.isDismembered()) return;

        WoundRecord wound = WoundLedgerService.selectedOrUrgentDressable(data, part);
        if (wound == null) return;

        float quality = BandageProfiles.quality(dressingPath(healer, itemSlot));
        String key = patient.getUUID() + "|" + part.name() + "|" + wound.id();

        if (finished) {
            stopBleed(limb, wound, BGConfig.bandageFullWrapBonusUnits() * quality);
            LAST_PROGRESS.remove(key);
            SPENT.remove(key);
            DoomedDataUtil.sync(patient);
            return;
        }

        float last = LAST_PROGRESS.getOrDefault(key, 0f);
        if (targetProgress < last - 0.001f) {
            last = 0f;
            SPENT.remove(key);
        }
        float delta = targetProgress - last;
        if (delta <= 0.0001f) return;
        LAST_PROGRESS.put(key, targetProgress);

        if (wound.bleeding() <= FLOOR) return;

        float capacity = BGConfig.bandageCapacityBase() * quality;
        float spent = SPENT.getOrDefault(key, 0f);
        float room = Math.max(0f, capacity - spent);
        if (room <= 0f) return;

        float stop = Math.min(room, delta * capacity);
        if (stop <= 0f) return;

        if (stopBleed(limb, wound, stop)) {
            SPENT.put(key, spent + stop);
        }
        DoomedDataUtil.sync(patient);
    }

    /**
     * Раз в секунду: мелкое остаточное кровотечение под уже наложенной повязкой
     * досворачиваем до нуля, чтобы "полностью забинтованная" рана не капала 0.05 л/мин.
     */
    public static void tick(ServerPlayer player, DoomedData data) {
        if (data == null || !data.isActive() || player.tickCount % 20 != 0) return;
        float cap = BGConfig.dressedResidualBleedUnits();
        if (cap <= 0f) return;

        boolean any = false;
        for (BodyPart part : BodyPart.VALUES) {
            LimbData limb = data.getLimb(part);
            if (limb == null || limb.isDismembered()) continue;
            boolean limbChanged = false;
            for (WoundRecord w : limb.getWounds()) {
                if (w.dressing() == DressingVisual.NONE) continue;
                float b = w.bleeding();
                if (b > 0f && b <= cap) {
                    w.setBleeding(0f);
                    limbChanged = true;
                }
            }
            if (limbChanged) {
                WoundLedgerService.applyProjection(limb);
                any = true;
            }
        }
        if (any) DoomedDataUtil.sync(player);
    }

    private static boolean stopBleed(LimbData limb, WoundRecord wound, float amount) {
        if (amount <= 0f) return false;
        float before = wound.bleeding();
        if (before <= FLOOR) return false;
        wound.setBleeding(Math.max(FLOOR, before - amount));
        WoundLedgerService.applyProjection(limb);
        return true;
    }

    private static String dressingPath(ServerPlayer healer, int slot) {
        if (healer == null) return "";
        ItemStack main = healer.getMainHandItem();
        ItemStack off = healer.getOffhandItem();
        ItemStack inSlot = ItemStack.EMPTY;
        Inventory inv = healer.getInventory();
        if (inv != null && slot >= 0 && slot < inv.getContainerSize()) {
            inSlot = inv.getItem(slot);
        }
        if (isDressing(main)) return path(main);
        if (isDressing(off)) return path(off);
        if (isDressing(inSlot)) return path(inSlot);
        if (main != null && !main.isEmpty()) return path(main);
        if (off != null && !off.isEmpty()) return path(off);
        if (inSlot != null && !inSlot.isEmpty()) return path(inSlot);
        return "";
    }

    private static boolean isDressing(ItemStack s) {
        return s != null && !s.isEmpty() && s.getItem() instanceof net.mattlives.doomedmatu.item.DressingItem;
    }

    private static String path(ItemStack s) {
        if (s == null || s.isEmpty()) return "";
        net.minecraft.resources.ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
        return id == null ? "" : id.getPath();
    }
}