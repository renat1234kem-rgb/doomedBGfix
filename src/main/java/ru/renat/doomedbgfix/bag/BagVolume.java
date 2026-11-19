package ru.renat.doomedbgfix.bag;

import net.mattlives.doomedmatu.body.ContagionHandler;
import net.mattlives.doomedmatu.capability.DoomedData;
import net.mattlives.doomedmatu.item.BloodBagItem;
import net.mattlives.doomedmatu.item.FluidSyringeItem;
import net.mattlives.doomedmatu.registry.DoomedItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Остаток пакета в тех же единицах, что использует мод: 100 = полный пакет.
 * Кровь: 100 ед = 0.75 Л, физраствор: 100 ед = 0.5 Л (константы самого Doomed).
 */
public final class BagVolume {

    public static final String TAG = "BGFixBagMl";
    public static final int FULL = 100;

    private BagVolume() {}

    public static boolean isBag(ItemStack s) {
        return !s.isEmpty() && (s.getItem() instanceof BloodBagItem || s.is(DoomedItems.SALINE.get()));
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

    /** Реальный объём в миллилитрах для тултипа. */
    public static int realMl(ItemStack s) {
        float litres = s.getItem() instanceof BloodBagItem ? 0.75f : 0.5f;
        return Math.round(litres * 1000f * get(s) / (float) FULL);
    }

    public static int capacityMl(ItemStack s) {
        return s.getItem() instanceof BloodBagItem ? 750 : 500;
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

        boolean blood = target.getItem() instanceof BloodBagItem;
        int mask = FluidSyringeItem.bloodPathogenMask(target);
        target.shrink(1);

        ItemStack empty = new ItemStack(DoomedItems.EMPTY_BLOOD_BAG.get());
        if (blood) {
            // как в оригинале: пакет из-под крови считается использованным
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
