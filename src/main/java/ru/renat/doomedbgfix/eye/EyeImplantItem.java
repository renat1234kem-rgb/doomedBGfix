package ru.renat.doomedbgfix.eye;

import net.mattlives.doomedmatu.body.BodyPart;
import net.mattlives.doomedmatu.capability.DoomedData;
import net.mattlives.doomedmatu.capability.DoomedDataUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class EyeImplantItem extends Item {

    public EyeImplantItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (!(player instanceof ServerPlayer sp)) return InteractionResultHolder.fail(stack);

        DoomedData data = DoomedDataUtil.get(sp).orElse(null);
        if (data == null || !data.isActive()) {
            sp.displayClientMessage(Component.translatable("doomedbgfix.eye.implant.not_doomed"), true);
            return InteractionResultHolder.fail(stack);
        }

        BodyPart eyePart = EyeImplantHandler.findAbsentEye(data);
        if (eyePart == null) {
            sp.displayClientMessage(Component.translatable("doomedbgfix.eye.implant.no_empty_socket"), true);
            return InteractionResultHolder.fail(stack);
        }

        if (!EyeImplantHandler.install(sp, eyePart)) {
            return InteractionResultHolder.fail(stack);
        }

        if (!sp.getAbilities().instabuild) stack.shrink(1);
        sp.displayClientMessage(Component.translatable("doomedbgfix.eye.implant.installed"), true);
        return InteractionResultHolder.success(stack);
    }
}
