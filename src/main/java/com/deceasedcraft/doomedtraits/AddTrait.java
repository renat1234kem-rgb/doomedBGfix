package com.deceasedcraft.doomedtraits;

import java.util.ArrayList;
import java.util.List;

/**
 * Наши черты. cost > 0 - перк (стоит очков, в экране показывается как -N),
 * cost < 0 - недостаток (даёт очки, показывается как +N).
 * Бит в нашей собственной маске (mask2) = 1 << ordinal.
 */
public enum AddTrait {
    // --- недостатки ---
    LACTOSE_INTOLERANCE("lactose_intolerance", -1),
    DEMENTIA("dementia", -3),
    SOCIOPHOBIA("sociophobia", -2),
    VEGETARIAN("vegetarian", -3),
    ANOREXIA("anorexia", -3),
    OPIATE_IMMUNITY("opiate_immunity", -2),
    HARDCORE_SCHIZOPHRENIA("hardcore_schizophrenia", -4),
    CHAIN_SMOKER("chain_smoker", -1),
    // --- перки ---
    IRON_SLEEP("iron_sleep", 1),
    CANNIBAL("cannibal", 1),
    GENIUS("genius", 3),
    IN_SHAPE("in_shape", 3),
    IRON_STOMACH("iron_stomach", 2),
    OPTIMIST("optimist", 3),
    FOCUSED("focused", 2);

    private static final List<AddTrait> PERKS = new ArrayList<>();
    private static final List<AddTrait> FLAWS = new ArrayList<>();

    static {
        for (AddTrait t : values()) {
            (t.isPerk() ? PERKS : FLAWS).add(t);
        }
    }

    private final String id;
    private final int cost;

    AddTrait(String id, int cost) {
        this.id = id;
        this.cost = cost;
    }

    public String id() {
        return id;
    }

    public int cost() {
        return cost;
    }

    public boolean isPerk() {
        return cost > 0;
    }

    public int bit() {
        return 1 << ordinal();
    }

    public static List<AddTrait> perks() {
        return PERKS;
    }

    public static List<AddTrait> flaws() {
        return FLAWS;
    }

    public String titleKey() {
        return DoomedTraitsAdditions.MOD_ID + ".trait." + id + ".title";
    }

    public String blurbKey() {
        return DoomedTraitsAdditions.MOD_ID + ".trait." + id + ".blurb";
    }

    public String effectKey() {
        return DoomedTraitsAdditions.MOD_ID + ".trait." + id + ".effect";
    }
}
