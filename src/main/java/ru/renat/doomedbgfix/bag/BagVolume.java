package ru.renat.doomedbgfix.bag;

import net.mattlives.doomedmatu.body.ContagionHandler;
import net.mattlives.doomedmatu.capability.DoomedData;
import net.mattlives.doomedmatu.item.BloodBagItem;
import net.mattlives.doomedmatu.item.FluidSyringeItem;
import net.mattlives.doomedmatu.registry.DoomedItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Остаток пакета в единицах мода: 100 = полный пакет.
 * Объём считаем из ОДНОГО источника истины, чтобы тултип, сообщение и
 * физиология не разъезжались (было "500 ml, 0.75 L").
 */
public final class BagVolume {

    public static final String TAG = "BGFixBagMl";
    public static final int FULL = 100;

    /** Совпадает с константами Doomed: BloodBagItem = 0.75 L, SalineItem = 0.5 L. */
    public static final float BLOOD_LITRES = 0.75f;
    public static final float SALINE_LITRES = 0.5f;

    private BagVolume() {}

    public static boolean isBlood(ItemStack s) {
        if (s.isEmpty()) return false;
        // instanceof + явная проверка id: раньше пакет иногда падал в ветку физраствора
        return s.getItem() instanceof BloodBagItem || s.is(DoomedItems.BLOOD_BAG_HUMAN.get());
    }

    public static boolean isBag(ItemStack s) {
        return !s.isEmpty() && (isBlood(s) || s.is(DoomedItems.SALINE.get()));
    }

    public static int get(ItemStack s) {
        if (!isBag(s) || s.getTag() == null || !s.getTag().contains(TAG)) return FULL;
        return Math.max(0, Math.min(FULL, s.getTag().getInt(TAG)));
    }

    public static void set(ItemStack s, int units) {
        int v = Math.max(0, Math.min(FULL, units));
        if (v == FULL) s.removeTagKey(TAG);
        else s.getOrCreateTag().putInt(TAG, v);
    }

    public static float litresFull(ItemStack s) {
        return isBlood(s) ? BLOOD_LITRES : SALINE_LITRES;
    }

    public static int capacityMl(ItemStack s) {
        return Math.round(litresFull(s) * 1000f);
    }

    /** Реальный остаток в миллилитрах. */
    public static int realMl(ItemStack s) {
        return Math.round(capacityMl(s) * get(s) / (float) FULL);
    }

    /**
     * Списывает use единиц. Стек-safe: если пакетов несколько, отделяем один,
     * чтобы не испортить объём остальным. Когда пакет пуст - выдаём пустой пакет.
     */
    public static void consume(ServerPlayer player, DoomedData patient, ItemStack stack, int use) {
        int left = Math.max(0, get(stack) - use);

        ItemStack target;
        if (stack.getCount() > 1) {
            target = stack.split(1);
            set(target, left);
            give(player, target);
        } else {
            target = stack;
            set(target, left);
        }

        if (left > 0) return;

        boolean blood = isBlood(target);
        int mask = FluidSyringeItem.bloodPathogenMask(target);
        target.shrink(1);

        ItemStack empty = new ItemStack(DoomedItems.EMPTY_BLOOD_BAG.get());
        if (blood) {
            FluidSyringeItem.setContaminated(empty, true);
            FluidSyringeItem.addBloodPathogens(empty, mask | ContagionHandler.bloodPathogenMask(patient));
        }
        give(player, empty);
    }

    public static void give(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }
}