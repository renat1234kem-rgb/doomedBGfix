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
import ru.renat.doomedbgfix.server.EyeCutSessions;

/**
 * Готовый имплант. ПКМ в воздух - мини-игра установки себе в пустую глазницу.
 * Установка другому игроку идёт через правый клик по нему (см. CoopMedicalEyeMixin).
 */
public class EyeImplantItem extends Item {

    public EyeImplantItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp)) return InteractionResultHolder.fail(stack);

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

        if (!EyeCutSessions.openSelf(sp, eyePart, EyeCutModel.MODE_INSTALL, false)) {
            return InteractionResultHolder.fail(stack);
        }
        return InteractionResultHolder.success(stack);
    }
}
