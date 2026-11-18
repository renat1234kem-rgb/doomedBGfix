package ru.renat.doomedbgfix;

import org.junit.jupiter.api.Test;
import ru.renat.doomedbgfix.cut.CutLayer;
import ru.renat.doomedbgfix.cut.CutMath;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CutMathTest {

    private final CutMath.Tissue healthy = CutMath.tissue(100f, 100f, false, true);

    @Test
    void totalWorkIsCloseToVanilla() {
        float work = CutMath.remainingWork(0f, healthy);
        assertTrue(work > 2.0f && work < 2.5f, "work=" + work);
    }

    @Test
    void boneBoundariesMatchVanilla() {
        assertEquals(0.34f, CutLayer.BONE.start, 1e-4);
        assertEquals(0.66f, CutLayer.BONE.end, 1e-4);
        assertEquals(4.5f, CutLayer.BONE.resistance, 1e-4);
    }

    @Test
    void damagedLimbStartsDeeper() {
        assertTrue(CutMath.freeDepth(20f, 30f) > 0.25f);
        assertEquals(0f, CutMath.freeDepth(100f, 100f), 1e-4);
    }

    @Test
    void advanceIsMonotonicAndClamped() {
        float d = 0f;
        for (int i = 0; i < 200; i++) d = CutMath.advance(d, 0.08f, healthy);
        assertEquals(1f, d, 1e-4);
    }

    @Test
    void boneCreditShrinksWithDepth() {
        assertEquals(36000, CutMath.boneCredit(0f, 36000));
        assertEquals(18000, CutMath.boneCredit(0.5f, 36000));
        assertEquals(0, CutMath.boneCredit(1f, 36000));
    }

    @Test
    void bleedingGrowsWithDepth() {
        assertTrue(CutMath.bleedTarget(0.05f, 1f) < CutMath.bleedTarget(0.3f, 1f));
        assertTrue(CutMath.bleedTarget(0.3f, 1f) <= 100f);
    }
}
