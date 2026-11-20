package ru.renat.doomedbgfix.bag;

import net.mattlives.doomedmatu.body.ContagionHandler;
import net.mattlives.doomedmatu.capability.DoomedData;
import net.mattlives.doomedmatu.item.BloodBagItem;
import net.mattlives.doomedmatu.item.FluidSyringeItem;
import net.mattlives.doomedmatu.registry.DoomedItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Остаток пакета в наших единицах: 100 = полный.
 * Кровяной пакет определяем максимально устойчиво: instanceof, registry-имя
 * (bloodbag*, кроме empty*) и имя класса - чтобы ёмкость больше не путалась с физраствором.
 */
public final class BagVolume {

    public static final String TAG = "BGFixBagMl";
    public static final int FULL = 100;

    public static final float BLOOD_LITRES = 0.75f;
    public static final float SALINE_LITRES = 0.5f;

    private BagVolume() {}

    public static String path(ItemStack s) {
        if (s == null || s.isEmpty()) return "";
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(s.getItem());
        return id == null ? "" : id.getPath();
    }

    public static boolean isBlood(ItemStack s) {
        if (s == null || s.isEmpty()) return false;
        if (s.getItem() instanceof BloodBagItem) return true;
        String p = path(s);
        if (p.isEmpty() || p.startsWith("empty")) return false;
        if (p.contains("bloodbag")) return true;
        return s.getItem().getClass().getSimpleName().contains("BloodBag");
    }

    public static boolean isSaline(ItemStack s) {
        if (s == null || s.isEmpty()) return false;
        return "saline".equals(path(s));
    }

    public static boolean isBag(ItemStack s) {
        return isBlood(s) || isSaline(s);
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

    public static int realMl(ItemStack s) {
        return Math.round(capacityMl(s) * get(s) / (float) FULL);
    }

    /**
     * Списывает use единиц. Когда пакет выжат досуха, он ВСЕГДА возвращается
     * пустым пакетом: после крови - заражённым, после физраствора - стерильным.
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
        } else {
            FluidSyringeItem.setContaminated(empty, false);
        }
        give(player, empty);
    }

    public static void give(ServerPlayer player, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }
}