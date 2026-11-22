package ru.renat.doomedbgfix.eye;

import net.mattlives.doomedmatu.registry.DoomedItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Какими предметами можно вырезать глаз: нож для растений из Doomed и любое холодное оружие. */
public final class EyeTools {

    private static final TagKey<Item> FORGE_SWORDS  = forge("tools/swords");
    private static final TagKey<Item> FORGE_AXES    = forge("tools/axes");
    private static final TagKey<Item> FORGE_KNIVES  = forge("tools/knives");
    private static final TagKey<Item> FORGE_DAGGERS = forge("tools/daggers");

    private EyeTools() {}

    private static TagKey<Item> forge(String path) {
        return ItemTags.create(new ResourceLocation("forge", path));
    }

    public static boolean isEyeCutTool(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.is(DoomedItems.FORAGING_KNIFE.get())) return true;
        return stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES)
                || stack.is(FORGE_SWORDS) || stack.is(FORGE_AXES)
                || stack.is(FORGE_KNIVES) || stack.is(FORGE_DAGGERS);
    }
}
