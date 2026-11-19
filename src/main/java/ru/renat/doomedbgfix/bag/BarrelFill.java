package ru.renat.doomedbgfix.bag;

import net.mattlives.doomedmatu.block.entity.FluidBarrelBlockEntity;
import net.mattlives.doomedmatu.body.DrugType;
import net.mattlives.doomedmatu.registry.DoomedFluids;
import net.mattlives.doomedmatu.registry.DoomedItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.registries.ForgeRegistries;
import ru.renat.doomedbgfix.config.BGConfig;

/** ПКМ пустым пакетом (или недопитым физраствором) по бочке с физраствором/Рингером. */
public final class BarrelFill {

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!BGConfig.barrelFill()) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        Level level = event.getLevel();
        if (level.isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;

        ItemStack held = event.getItemStack();
        boolean empty = held.is(DoomedItems.EMPTY_BLOOD_BAG.get());
        boolean saline = held.is(DoomedItems.SALINE.get());
        if (!empty && !saline) return;

        int missing = empty ? BagVolume.FULL : BagVolume.FULL - BagVolume.get(held);
        if (missing <= 0) return;

        BlockPos pos = event.getPos();
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof FluidBarrelBlockEntity barrel)) return;

        FluidStack in = barrel.tank().getFluid();
        if (in.isEmpty()) return;
        DrugType kind = DoomedFluids.drugFor(in.getFluid());
        if (kind != DrugType.SALINE && kind != DrugType.RINGERSOLUTION) return;

        int perBag = BGConfig.barrelMbPerBag();
        int want = Math.max(1, Math.round(perBag * missing / (float) BagVolume.FULL));
        int before = barrel.tank().getFluidAmount();

        FluidStack drained = barrel.tank().drain(want, IFluidHandler.FluidAction.EXECUTE);
        if (drained.isEmpty() || barrel.tank().getFluidAmount() >= before) return;

        be.setChanged();
        BlockState state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, 3);

        int units = Math.max(1, Math.round(BagVolume.FULL * drained.getAmount() / (float) perBag));

        if (empty) {
            ItemStack full = new ItemStack(DoomedItems.SALINE.get());
            CompoundTag fluid = new CompoundTag();
            fluid.putInt("Amount", drained.getAmount());
            fluid.putString("FluidName", String.valueOf(ForgeRegistries.FLUIDS.getKey(drained.getFluid())));
            if (drained.getTag() != null) fluid.put("Tag", drained.getTag().copy());
            full.getOrCreateTag().put("Fluid", fluid);
            BagVolume.set(full, units);
            if (!sp.isCreative()) held.shrink(1);
            BagVolume.give(sp, full);
        } else {
            BagVolume.set(held, BagVolume.get(held) + units);
        }

        event.setUseBlock(Event.Result.DENY);
        event.setUseItem(Event.Result.DENY);
        event.setCanceled(true);
    }
}
