package ru.renat.doomedbgfix.bag;

import net.mattlives.doomedmatu.block.entity.FluidBarrelBlockEntity;
import net.mattlives.doomedmatu.body.BloodType;
import net.mattlives.doomedmatu.body.ContagionHandler;
import net.mattlives.doomedmatu.capability.DoomedData;
import net.mattlives.doomedmatu.capability.DoomedDataUtil;
import net.mattlives.doomedmatu.item.BloodBagItem;
import net.mattlives.doomedmatu.item.FluidSyringeItem;
import net.mattlives.doomedmatu.registry.DoomedItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import ru.renat.doomedbgfix.config.BGConfig;
import ru.renat.doomedbgfix.server.CutBridge;

import java.util.Locale;

/**
 * Свой набор крови в пустой пакет. Объём в сообщении берётся ИЗ ФАКТА СНЯТИЯ
 * (donateLitres), а не из определения типа пакета, поэтому цифры всегда сходятся:
 * 0.75 L снято = 750 ml в пакете.
 */
public final class EmptyBagRefill {

    private boolean consumed;

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onUseItem(PlayerInteractEvent.RightClickItem event) {
        handle(event.getLevel(), event.getEntity() instanceof ServerPlayer sp ? sp : null,
                event.getHand(), event.getItemStack());
        if (consumed) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().getBlockEntity(event.getPos()) instanceof FluidBarrelBlockEntity) return;
        handle(event.getLevel(), event.getEntity() instanceof ServerPlayer sp ? sp : null,
                event.getHand(), event.getItemStack());
        if (consumed) {
            event.setUseItem(net.minecraftforge.eventbus.api.Event.Result.DENY);
            event.setCanceled(true);
        }
    }

    private void handle(Level level, ServerPlayer sp, InteractionHand hand, ItemStack stack) {
        consumed = false;
        if (!BGConfig.ownBloodDraw()) return;
        if (level == null || level.isClientSide || sp == null) return;
        if (hand != InteractionHand.MAIN_HAND) return;
        if (stack.isEmpty() || !stack.is(DoomedItems.EMPTY_BLOOD_BAG.get())) return;

        consumed = true;

        DoomedData d = CutBridge.data(sp);
        if (d == null || !d.isActive()) {
            say(sp, "It does nothing to you.");
            return;
        }

        float litres = BGConfig.donateLitres();
        if (d.getBloodVolume() - litres < BGConfig.donateBloodFloor()) {
            say(sp, String.format(Locale.ROOT,
                    "Not enough blood: %.2f L left, need %.2f L to stay after drawing %.2f L.",
                    d.getBloodVolume(), BGConfig.donateBloodFloor(), litres));
            return;
        }
        if (d.getRedCellFraction() < BGConfig.donateRbcMin()) {
            say(sp, String.format(Locale.ROOT,
                    "Blood too thin: %d%% RBC, need %d%%.",
                    Math.round(d.getRedCellFraction() * 100f), Math.round(BGConfig.donateRbcMin() * 100f)));
            return;
        }

        BloodType type = d.getBloodType() == BloodType.UNKNOWN
                ? BloodType.fromUuid(sp.getUUID()) : d.getBloodType();

        d.removeWholeBlood(litres);

        ItemStack full = BloodBagItem.typed(DoomedItems.BLOOD_BAG_HUMAN.get(), type);
        BagVolume.set(full, BagVolume.FULL);
        FluidSyringeItem.addBloodPathogens(full, ContagionHandler.bloodPathogenMask(d));
        if (BGConfig.cleanRefilledBag()) FluidSyringeItem.setContaminated(full, false);

        if (!sp.isCreative()) stack.shrink(1);
        BagVolume.give(sp, full);

        int ml = Math.round(litres * 1000f);
        say(sp, String.format(Locale.ROOT, "Filled %s bag: %d ml (%.2f L). Drawn from you: %.2f L.",
                type.label(), ml, ml / 1000f, litres));
        DoomedDataUtil.sync(sp);
    }

    private static void say(ServerPlayer sp, String text) {
        sp.displayClientMessage(Component.literal(text), true);
    }
}