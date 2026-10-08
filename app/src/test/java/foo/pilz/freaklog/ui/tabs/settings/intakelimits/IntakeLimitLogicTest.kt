package foo.pilz.freaklog.ui.tabs.settings.intakelimits

import foo.pilz.freaklog.data.room.experiences.entities.IntakeLimit
import foo.pilz.freaklog.data.room.experiences.entities.IntakeLimitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class IntakeLimitLogicTest {

    private val now: Instant = Instant.parse("2026-05-27T12:00:00Z")

    private fun doseLimit(
        max: Double = 100.0,
        unit: String = "mg",
        windowSeconds: Long = 86_400,
        warningPercent: Int = 80,
    ) = IntakeLimit(
        substanceName = "Caffeine",
        limitType = IntakeLimitType.DOSE,
        maxDose = max,
        unit = unit,
        maxCount = null,
        windowSeconds = windowSeconds,
        warningPercent = warningPercent,
    )

    private fun countLimit(
        max: Int = 3,
        windowSeconds: Long = 604_800,
        warningPercent: Int = 80,
    ) = IntakeLimit(
        substanceName = "Nicotine",
        limitType = IntakeLimitType.COUNT,
        maxDose = null,
        unit = null,
        maxCount = max,
        windowSeconds = windowSeconds,
        warningPercent = warningPercent,
    )

    private fun ago(hours: Long) = now.minus(hours, ChronoUnit.HOURS)

    @Test
    fun convertDose_sameUnitReturnsValue() {
        assertEquals(50.0, convertDose(50.0, "mg", "mg")!!, 0.0001)
        assertEquals(2.0, convertDose(2.0, "cup", "cup")!!, 0.0001)
    }

    @Test
    fun convertDose_convertsBetweenMassUnits() {
        assertEquals(2000.0, convertDose(2.0, "g", "mg")!!, 0.0001)
        assertEquals(0.5, convertDose(500.0, "µg", "mg")!!, 0.0001)
        assertEquals(1.5, convertDose(1500.0, "mg", "g")!!, 0.0001)
    }

    @Test
    fun convertDose_isCaseInsensitive() {
        assertEquals(1000.0, convertDose(1.0, "G", "MG")!!, 0.0001)
    }

    @Test
    fun convertDose_returnsNullForIncompatibleOrMissingUnits() {
        assertNull(convertDose(10.0, "ml", "mg"))
        assertNull(convertDose(10.0, null, "mg"))
        assertNull(convertDose(10.0, "mg", null))
    }

    @Test
    fun evaluateDose_sumsConvertibleAndIgnoresOthers() {
        val past = listOf(
            LimitIngestion(50.0, "mg", ago(1)),
            LimitIngestion(0.02, "g", ago(2)),
            LimitIngestion(null, "mg", ago(3)),
            LimitIngestion(10.0, "ml", ago(1)),
            LimitIngestion(1000.0, "mg", ago(48)),
        )
        val status = evaluateIntakeLimit(doseLimit(), now, past, pending = null)
        assertEquals(70.0, status.currentValue, 0.0001)
        assertEquals(2, status.ignoredIngestionCount)
        assertEquals(70.0, status.currentPercent, 0.0001)
        assertFalse(status.exceedsLimit)
    }

    @Test
    fun evaluateDose_pendingDrivesWarningAndExceed() {
        val past = listOf(LimitIngestion(70.0, "mg", ago(1)))

        val nearWarning = evaluateIntakeLimit(
            doseLimit(), now, past, pending = LimitIngestion(20.0, "mg", now)
        )
        assertEquals(90.0, nearWarning.projectedValue, 0.0001)
        assertTrue(nearWarning.crossesWarning)
        assertFalse(nearWarning.exceedsLimit)

        val overLimit = evaluateIntakeLimit(
            doseLimit(), now, past, pending = LimitIngestion(50.0, "mg", now)
        )
        assertEquals(120.0, overLimit.projectedValue, 0.0001)
        assertTrue(overLimit.exceedsLimit)
    }

    @Test
    fun evaluateCount_countsWindowAndPending() {
        val past = listOf(
            LimitIngestion(1.0, "mg", ago(1)),
            LimitIngestion(1.0, "mg", ago(24)),
            LimitIngestion(1.0, "mg", ago(24 * 9)),
        )
        val status = evaluateIntakeLimit(
            countLimit(), now, past, pending = LimitIngestion(1.0, "mg", now)
        )
        assertEquals(2.0, status.currentValue, 0.0001)
        assertEquals(3.0, status.projectedValue, 0.0001)
        assertTrue(status.crossesWarning)
        assertFalse(status.exceedsLimit)
    }

    @Test
    fun suggestedUnits_putsSubstanceUnitsFirstThenStandardsDeduped() {
        assertEquals(listOf("µg", "mg", "g", "mL"), suggestedDoseUnits(listOf("µg")))
        assertEquals(listOf("mg", "µg", "g", "mL"), suggestedDoseUnits(listOf("mg")))
        assertEquals(
            listOf("tab", "µg", "mg", "g", "mL"),
            suggestedDoseUnits(listOf("tab", " ", "tab"))
        )
        assertEquals(STANDARD_DOSE_UNITS, suggestedDoseUnits(emptyList()))
    }

    @Test
    fun defaultUnit_picksFirstNonBlankElseMg() {
        assertEquals("µg", defaultDoseUnit(listOf(" ", "µg", "mg")))
        assertEquals("mg", defaultDoseUnit(emptyList()))
        assertEquals("mg", defaultDoseUnit(listOf("", "  ")))
    }

    @Test
    fun resetTime_dropsUnderWarningWhenOldestExits() {
        val past = listOf(
            LimitIngestion(60.0, "mg", ago(1)),
            LimitIngestion(30.0, "mg", ago(2)),
        )
        val status = evaluateIntakeLimit(doseLimit(), now, past, pending = null)
        assertNull(status.instantUnderLimit())
        assertEquals(ago(2).plusSeconds(86_400), status.instantUnderWarning())
    }

    @Test
    fun resetTime_dropsUnderLimitWhenContributionExits() {
        val past = listOf(LimitIngestion(80.0, "mg", ago(1)))
        val status =
            evaluateIntakeLimit(doseLimit(), now, past, pending = LimitIngestion(40.0, "mg", now))
        assertTrue(status.exceedsLimit)
        assertEquals(ago(1).plusSeconds(86_400), status.instantUnderLimit())
    }

    @Test
    fun resetTime_fullyResetsWhenLastContributionExits() {
        val past = listOf(
            LimitIngestion(80.0, "mg", ago(1)),
            LimitIngestion(40.0, "mg", ago(2)),
        )
        val status = evaluateIntakeLimit(doseLimit(), now, past, pending = null)
        assertTrue(status.exceedsLimit)
        assertEquals(ago(2).plusSeconds(86_400), status.instantUnderLimit())
        assertEquals(ago(1).plusSeconds(86_400), status.instantFullReset())
    }

    @Test
    fun resetTime_fullResetIncludesPendingIngestion() {
        val past = listOf(
            LimitIngestion(80.0, "mg", ago(1)),
            LimitIngestion(40.0, "mg", ago(2)),
        )
        val status =
            evaluateIntakeLimit(doseLimit(), now, past, pending = LimitIngestion(40.0, "mg", now))
        assertTrue(status.exceedsLimit)
        assertEquals(ago(1).plusSeconds(86_400), status.instantUnderLimit())
        assertEquals(now.plusSeconds(86_400), status.instantFullReset())
    }

    @Test
    fun resetTime_fullResetIsNullWithoutUsage() {
        val status = evaluateIntakeLimit(doseLimit(), now, emptyList(), pending = null)
        assertNull(status.instantFullReset())
        assertNull(status.resetInfo())
    }

    @Test
    fun resetInfo_belowWarningShowsBackToZero() {
        val past = listOf(LimitIngestion(30.0, "mg", ago(1)))
        val status = evaluateIntakeLimit(doseLimit(), now, past, pending = null)
        val reset = status.resetInfo()!!
        assertTrue(reset.startsWith("Back to zero in "))
        assertEquals(ago(1).plusSeconds(86_400), status.instantFullReset())
    }

    @Test
    fun resetInfo_aboveWarningShowsWarningThreshold() {
        val past = listOf(LimitIngestion(90.0, "mg", ago(1)))
        val status = evaluateIntakeLimit(doseLimit(), now, past, pending = null)
        val reset = status.resetInfo()!!
        assertTrue(reset.startsWith("Back under 80% in "))
    }

    @Test
    fun resetInfo_overLimitShowsUnderLimit() {
        val past = listOf(
            LimitIngestion(80.0, "mg", ago(1)),
            LimitIngestion(40.0, "mg", ago(2)),
        )
        val status = evaluateIntakeLimit(doseLimit(), now, past, pending = null)
        val reset = status.resetInfo()!!
        assertTrue(reset.startsWith("Back under the limit in "))
        assertFalse(reset.contains("zero"))
    }
}
