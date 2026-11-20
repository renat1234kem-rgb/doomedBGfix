package ru.renat.doomedbgfix.bag;

import net.mattlives.doomedmatu.item.FluidSyringeItem;
import net.mattlives.doomedmatu.registry.DoomedItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Страховка от "мёртвых" пакетов: если в инвентаре остался пакет с нашим тегом 0
 * (сессия оборвалась, стек не сконвертировался), раз в секунду превращаем его
 * в пустой пакет для крови: после крови - заражённый, после физраствора - стерильный.
 */
public final class BagSweeper {

    private BagSweeper() {}

    public static void tick(ServerPlayer player) {
        if (player == null || player.tickCount % 20 != 0) return;
        Inventory inv = player.getInventory();
        if (inv == null) return;

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s == null || s.isEmpty() || !BagVolume.isBag(s)) continue;
            if (BagVolume.get(s) != 0) continue;

            boolean blood = BagVolume.isBlood(s);
            int mask = blood ? FluidSyringeItem.bloodPathogenMask(s) : 0;

            ItemStack empty = new ItemStack(DoomedItems.EMPTY_BLOOD_BAG.get());
            if (blood) {
                FluidSyringeItem.setContaminated(empty, true);
                FluidSyringeItem.addBloodPathogens(empty, mask);
            } else {
                FluidSyringeItem.setContaminated(empty, false);
            }
            empty.setCount(s.getCount());
            inv.setItem(i, empty);
        }
    }
}