package foo.pilz.freaklog.ui.tabs.search.substance.roa

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Test
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DoubleReadableExtensionKtTest {

    @Test
    fun testToReadableString() {
        assertEquals("2", 2.0.toReadableString())
        assertEquals("20", 20.0.toReadableString())
        assertEquals("0.333", (1.0/3.0).toReadableString())
        assertEquals("120", 120.0.toReadableString())
        assertEquals("1.5", 1.5.toReadableString())
        assertEquals("1500", 1500.0.toReadableString())
        assertEquals("12.6", 12.6.toReadableString())
        assertEquals("0.25", 0.25.toReadableString())
        assertEquals("3.33", (10.0/3.0).toReadableString())
        assertEquals("333", (1000.0/3.0).toReadableString())
        assertEquals("33.3", (100.0/3.0).toReadableString())
        assertEquals("1.67", 1.66666.toReadableString())
        assertEquals("123", 122.66666.toReadableString())
        assertEquals("122", 122.33333.toReadableString())
        assertEquals("44.4", 44.4444.toReadableString())
        assertEquals("0.023", 0.023.toReadableString())
    }

    @Test
    fun testNegativeValuesUseAbsoluteMagnitudeForPrecision() {
        assertEquals("-150", (-150.4).toReadableString())
        assertEquals("-12.6", (-12.64).toReadableString())
        assertEquals("-1.67", (-1.66666).toReadableString())
        assertEquals("-0.023", (-0.023).toReadableString())
        assertEquals("0", 0.0.toReadableString())
    }

    @Test
    fun testNonFiniteValuesReturnEmptyString() {
        assertEquals("", Double.NaN.toReadableString())
        assertEquals("", Double.POSITIVE_INFINITY.toReadableString())
        assertEquals("", Double.NEGATIVE_INFINITY.toReadableString())
    }

    @Test
    fun testUsesDotSeparatorRegardlessOfDefaultLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("1.5", 1.5.toReadableString())
            assertEquals("1500", 1500.0.toReadableString())
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun testConcurrentCallsAreConsistent() {
        val inputs = listOf(2.0, 12.6, 1.66666, 0.023, 1500.0, -44.4444)
        val expected = inputs.map { it.toReadableString() }
        val pool = Executors.newFixedThreadPool(8)
        try {
            val futures = (0 until 8).map {
                pool.submit<Boolean> {
                    (0 until 2000).all { i ->
                        val index = i % inputs.size
                        inputs[index].toReadableString() == expected[index]
                    }
                }
            }
            futures.forEach { assertTrue(it.get(30, TimeUnit.SECONDS)) }
        } finally {
            pool.shutdownNow()
        }
    }
}