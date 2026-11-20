package com.deceasedcraft.doomedtraits.server;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraftforge.registries.ForgeRegistries;

/** Определяет, является ли предмет молочным или мясным. Списки легко дополнять. */
public final class FoodClassifier {
    /** true = рыба и морепродукты тоже считаются мясом (строгое вегетарианство). */
    public static final boolean FISH_COUNTS_AS_MEAT = true;

    private static final Set<String> DAIRY_IDS = Set.of(
        "minecraft:milk_bucket",
        "doomedmatu:milk",
        "doomedmatu:chocolatemilk"
    );

    private static final Set<String> MEAT_IDS = Set.of(
        "minecraft:beef", "minecraft:cooked_beef",
        "minecraft:porkchop", "minecraft:cooked_porkchop",
        "minecraft:chicken", "minecraft:cooked_chicken",
        "minecraft:mutton", "minecraft:cooked_mutton",
        "minecraft:rabbit", "minecraft:cooked_rabbit", "minecraft:rabbit_stew",
        "minecraft:rotten_flesh",
        "doomedmatu:bucketofchicken", "doomedmatu:pemmican",
        "doomedmatu:raw_human_flesh", "doomedmatu:cooked_human_flesh"
    );

    private static final Set<String> FISH_IDS = Set.of(
        "minecraft:cod", "minecraft:cooked_cod",
        "minecraft:salmon", "minecraft:cooked_salmon",
        "minecraft:tropical_fish", "minecraft:pufferfish"
    );

    // Молочное: подстроки в пути предмета ("cheeseburger", "milkshake", "goat_cheese")
    private static final String[] DAIRY_SUBSTR = {
        "cheese", "milk", "yogurt", "yoghurt", "cream", "butter", "dairy", "kefir", "custard", "whey", "cake"
    };
    private static final String[] DAIRY_EXCLUDE_SUBSTR = {
        "coconut", "almond", "soy", "vegan", "plant", "peanut", "cocoa", "shea"
    };
    // Короткие слова, которые нельзя искать подстрокой ("goat" содержит "oat")
    private static final Set<String> DAIRY_EXCLUDE_WORDS = Set.of("oat", "oats", "rice", "nut", "nuts");

    // Мясо: «твёрдые» подстроки - мясо, даже если рядом есть растительное слово
    private static final String[] MEAT_HARD_SUBSTR = {
        "beef", "pork", "bacon", "mutton", "venison", "jerky", "flesh"
    };
    private static final Set<String> MEAT_HARD_WORDS = Set.of("ham", "lamb", "salami", "pepperoni", "brisket", "bologna", "ribs");
    // «Мягкие» подстроки: мясо, если нет признака растительной замены
    private static final String[] MEAT_SOFT_SUBSTR = {
        "meat", "burger", "steak", "sausage", "patty", "nugget", "chicken", "kebab", "hotdog", "drumstick", "fillet"
    };
    private static final String[] PLANT_BASED_SUBSTR = {
        "vegan", "veggie", "vegetable", "plant", "tofu", "mushroom", "seitan", "soy"
    };
    private static final String[] FISH_SUBSTR = {
        "fish", "salmon", "shrimp", "lobster", "calamari", "sushi", "sashimi", "sardine", "anchovy"
    };
    private static final Set<String> FISH_WORDS = Set.of("cod", "tuna", "crab", "squid", "trout", "herring", "eel");

    private static final TagKey<Item> TAG_MILK = tag("forge", "milk");
    private static final TagKey<Item> TAG_MILK_BUCKETS = tag("forge", "buckets/milk");
    private static final TagKey<Item> TAG_CHEESE = tag("forge", "cheese");
    private static final TagKey<Item> TAG_RAW_MEATS = tag("forge", "raw_meats");
    private static final TagKey<Item> TAG_COOKED_MEATS = tag("forge", "cooked_meats");
    private static final TagKey<Item> TAG_RAW_FISHES = tag("forge", "raw_fishes");
    private static final TagKey<Item> TAG_COOKED_FISHES = tag("forge", "cooked_fishes");

    private FoodClassifier() {
    }

    private static TagKey<Item> tag(String ns, String path) {
        return ItemTags.create(new ResourceLocation(ns, path));
    }

    /** Можно ли это съесть или выпить (шприцы, бинты и т.п. под это не попадают). */
    public static boolean isConsumable(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        String id = idOf(stack);
        if (DAIRY_IDS.contains(id) || MEAT_IDS.contains(id) || FISH_IDS.contains(id)) {
            return true;
        }
        UseAnim anim = stack.getUseAnimation();
        return stack.isEdible() || anim == UseAnim.EAT || anim == UseAnim.DRINK;
    }

    public static boolean isDairy(ItemStack stack) {
        String id = idOf(stack);
        if (DAIRY_IDS.contains(id) || stack.is(TAG_MILK) || stack.is(TAG_MILK_BUCKETS) || stack.is(TAG_CHEESE)) {
            return true;
        }
        String path = pathOf(id);
        if (!containsAnySubstring(path, DAIRY_SUBSTR)) {
            return false;
        }
        return !containsAnySubstring(path, DAIRY_EXCLUDE_SUBSTR) && !containsAnyWord(words(path), DAIRY_EXCLUDE_WORDS);
    }

    public static boolean isMeat(ItemStack stack) {
        String id = idOf(stack);
        if (MEAT_IDS.contains(id) || stack.is(TAG_RAW_MEATS) || stack.is(TAG_COOKED_MEATS)) {
            return true;
        }
        boolean fishTag = stack.is(TAG_RAW_FISHES) || stack.is(TAG_COOKED_FISHES);
        if (FISH_COUNTS_AS_MEAT && (FISH_IDS.contains(id) || fishTag)) {
            return true;
        }
        String path = pathOf(id);
        Set<String> words = words(path);
        if (containsAnySubstring(path, MEAT_HARD_SUBSTR) || containsAnyWord(words, MEAT_HARD_WORDS)) {
            return true;
        }
        boolean plantBased = containsAnySubstring(path, PLANT_BASED_SUBSTR);
        if (!plantBased && containsAnySubstring(path, MEAT_SOFT_SUBSTR)) {
            // яйца и яичные блюда с курицей в названии мясом не считаем ("chicken_egg")
            return !(path.contains("chicken") && path.contains("egg") && !path.contains("meat"));
        }
        if (FISH_COUNTS_AS_MEAT && !plantBased && !path.contains("egg")) {
            return containsAnySubstring(path, FISH_SUBSTR) || containsAnyWord(words, FISH_WORDS);
        }
        return false;
    }

    private static String idOf(ItemStack stack) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return key == null ? "" : key.toString();
    }

    private static String pathOf(String id) {
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }

    /** Разбивает путь предмета на слова по _ / - . и т.п. */
    private static Set<String> words(String path) {
        return new HashSet<>(Arrays.asList(path.split("[^a-z0-9]+")));
    }

    private static boolean containsAnySubstring(String path, String[] needles) {
        for (String n : needles) {
            if (path.contains(n)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAnyWord(Set<String> words, Set<String> wanted) {
        for (String w : words) {
            if (wanted.contains(w)) {
                return true;
            }
        }
        return false;
    }
}
