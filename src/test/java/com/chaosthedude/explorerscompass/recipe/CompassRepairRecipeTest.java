package com.chaosthedude.explorerscompass.recipe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompassRepairRecipeTest {
    @Test void combinesRemainingSearchesAndVanillaBonus() {
        assertEquals(15, CompassRepairRecipe.repairedDamage(100, 60, 60));
        assertEquals(0, CompassRepairRecipe.repairedDamage(100, 0, 75));
        assertEquals(95, CompassRepairRecipe.repairedDamage(100, 100, 100));
    }

    @Test void handlesDisabledDurabilitySmallLimitsAndInvalidDamage() {
        assertEquals(0, CompassRepairRecipe.repairedDamage(0, 0, 0));
        assertEquals(1, CompassRepairRecipe.repairedDamage(1, 1, 1));
        assertEquals(0, CompassRepairRecipe.repairedDamage(10, -5, 9));
        assertEquals(0, CompassRepairRecipe.repairedDamage(Integer.MAX_VALUE, 0, 0));
    }

}
