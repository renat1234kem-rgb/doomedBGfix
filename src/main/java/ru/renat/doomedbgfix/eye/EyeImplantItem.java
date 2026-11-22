package ru.renat.doomedbgfix.eye;

import net.mattlives.doomedmatu.body.BodyPart;
import net.mattlives.doomedmatu.body.DoomedData;
import net.mattlives.doomedmatu.body.LimbData;
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
        if (level.isClientSide) return InteractionResultHolder.success(player.getItemInHand(hand));
        if (!(player instanceof ServerPlayer sp)) return InteractionResultHolder.fail(player.getItemInHand(hand));

        DoomedData data = DoomedDataUtil.get(player).orElse(null);
        if (data == null || !data.isActive()) {
            sp.displayClientMessage(Component.literal("You need to be Doomed to use this."), true);
            return InteractionResultHolder.fail(player.getItemInHand(hand));
        }

        // Проверяем, есть ли ампутированный глаз
        BodyPart eyePart = findAmputatedEye(data);
        if (eyePart == null) {
            sp.displayClientMessage(Component.literal("No amputated eye to implant."), true);
            return InteractionResultHolder.fail(player.getItemInHand(hand));
        }

        // Устанавливаем имплант
        LimbData limb = data.getLimb(eyePart);
        limb.setDismembered(false);
        limb.setSkinHealth(50f);  // Частично восстановлен
        limb.setMuscleHealth(50f);
        limb.setBleed(0f);
        limb.setPain(20f);

        // Помечаем, что это имплант (для смены спрайта)
        limb.getOrCreateTag().putBoolean("EyeImplant", true);

        DoomedDataUtil.sync(sp);
        player.getItemInHand(hand).shrink(1);
        sp.displayClientMessage(Component.literal("Eye implant installed successfully."), true);

        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    private BodyPart findAmputatedEye(DoomedData data) {
        for (BodyPart part : new BodyPart[]{BodyPart.EYE_LEFT, BodyPart.EYE_RIGHT}) {
            LimbData limb = data.getLimb(part);
            if (limb != null && limb.isDismembered()) return part;
        }
        return null;
    }
}