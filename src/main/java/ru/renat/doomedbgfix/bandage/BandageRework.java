package ru.renat.doomedbgfix.bandage;

import net.mattlives.doomedmatu.body.BodyPart;
import net.mattlives.doomedmatu.body.LimbData;
import net.mattlives.doomedmatu.body.WoundLedgerService;
import net.mattlives.doomedmatu.body.WoundRecord;
import net.mattlives.doomedmatu.capability.DoomedData;
import net.mattlives.doomedmatu.capability.DoomedDataUtil;
import net.mattlives.doomedmatu.item.DressingItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import ru.renat.doomedbgfix.bag.BagVolume;
import ru.renat.doomedbgfix.config.BGConfig;
import ru.renat.doomedbgfix.server.CutBridge;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Прогрессивная остановка кровотечения повязками (модель Casualties: Unknown).
 *
 * Два важных правила, вынесенных из боевых тестов:
 *  1) Кровь НЕ опускается ниже FLOOR (0.25): рана всегда остаётся "бинтуемой",
 *     иначе ванильный BandageSessionPolicy мгновенно закрывает мини-игру
 *     ("нечего бинтовать"), а предмет повязки остаётся висеть мёртвым стеком.
 *  2) Предмет повязки ищется по рукам (main -> off -> slot) и только среди
 *     DressingItem: параметр itemSlot в handleBandageProgress не является
 *     индексом инвентаря (в логе был прочитан gun_turret вместо бинта).
 */
public final class BandageRework {

    /** Минимальная кровь, до которой мы опускаем рану прогрессивной остановкой. */
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
            // бонус за полную намотку, сверх ванильного процентного эффекта
            stopBleed(limb, wound, BGConfig.bandageFullWrapBonusUnits() * quality);
            LAST_PROGRESS.remove(key);
            SPENT.remove(key);
            DoomedDataUtil.sync(patient);
            return;
        }

        float last = LAST_PROGRESS.getOrDefault(key, 0f);
        // прогресс пошёл назад/с нуля - мотают новым бинтом, расход обнуляем
        if (targetProgress < last - 0.001f) {
            last = 0f;
            SPENT.remove(key);
        }
        float delta = targetProgress - last;
        if (delta <= 0.0001f) return;
        LAST_PROGRESS.put(key, targetProgress);

        // рана уже на полу - дальше останавливать нечем, ёмкость не тратим
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

    /** Возвращает true, если кровь реально снижена. Ниже FLOOR не опускаем. */
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

        // сначала - то, что реально является повязкой
        if (main != null && !main.isEmpty() && main.getItem() instanceof DressingItem) return BagVolume.path(main);
        if (off != null && !off.isEmpty() && off.getItem() instanceof DressingItem) return BagVolume.path(off);
        if (inSlot != null && !inSlot.isEmpty() && inSlot.getItem() instanceof DressingItem) return BagVolume.path(inSlot);

        // fallback: просто первый непустой кандидат
        if (main != null && !main.isEmpty()) return BagVolume.path(main);
        if (off != null && !off.isEmpty()) return BagVolume.path(off);
        if (inSlot != null && !inSlot.isEmpty()) return BagVolume.path(inSlot);
        return "";
    }
}