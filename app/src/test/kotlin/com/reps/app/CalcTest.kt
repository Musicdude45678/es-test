package com.reps.app

import com.reps.app.data.model.WeightUnit
import com.reps.app.domain.Calc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalcTest {
    @Test fun platesPerSideBasic() {
        assertEquals(listOf(20.0), Calc.platesPerSide(60.0, 20.0, WeightUnit.KG))
        assertEquals(listOf(20.0, 2.5), Calc.platesPerSide(65.0, 20.0, WeightUnit.KG))
        assertEquals(listOf(25.0, 25.0), Calc.platesPerSide(120.0, 20.0, WeightUnit.KG))
        assertEquals(listOf(25.0, 5.0, 2.5), Calc.platesPerSide(85.0, 20.0, WeightUnit.KG))
        assertEquals(emptyList<Double>(), Calc.platesPerSide(20.0, 20.0, WeightUnit.KG))
        assertEquals(emptyList<Double>(), Calc.platesPerSide(15.0, 20.0, WeightUnit.KG))
    }

    @Test fun platesPerSideLb() {
        // 135 lb total with a 45 lb bar = one 45 each side
        val totalKg = WeightUnit.LB.toKg(135.0)
        val barKg = WeightUnit.LB.toKg(45.0)
        assertEquals(listOf(45.0), Calc.platesPerSide(totalKg, barKg, WeightUnit.LB))
    }

    @Test fun snapRoundsToIncrement() {
        assertEquals(62.5, Calc.snap(62.4, 2.5), 0.0001)
        assertEquals(60.0, Calc.snap(61.2, 2.5), 0.0001)
        assertEquals(22.5, Calc.snap(20.0 + 2.5, 2.5), 0.0001)
    }

    @Test fun formatWeight() {
        assertEquals("60 kg", Calc.formatWeight(60.0, WeightUnit.KG))
        assertEquals("62.5 kg", Calc.formatWeight(62.5, WeightUnit.KG))
        assertEquals("61.25", Calc.formatWeight(61.25, WeightUnit.KG, withUnit = false))
        assertEquals("132.28 lb", Calc.formatWeight(60.0, WeightUnit.LB))
    }

    @Test fun oneRepMax() {
        assertEquals(100.0, Calc.estimate1Rm(100.0, 1), 0.0001)
        assertTrue(Calc.estimate1Rm(100.0, 5) > 100.0)
        assertEquals(0.0, Calc.estimate1Rm(100.0, 0), 0.0001)
    }

    @Test fun durations() {
        assertEquals("1:05", Calc.formatDuration(65_000))
        assertEquals("1:00:00", Calc.formatDuration(3_600_000))
        assertEquals("1h 5m", Calc.formatDurationShort(65 * 60_000))
    }
}
