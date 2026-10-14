package foo.pilz.freaklog.ui.tabs.journal.experience.bloodpressure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BloodPressureValidationTest {

    @Test
    fun `valid inputs with integers and commas are accepted`() {
        assertTrue(isBloodPressureInputValid("120", "80"))
        assertTrue(isBloodPressureInputValid(" 120.5 ", " 80.2 "))
        assertTrue(isBloodPressureInputValid("120,5", "80,2"))
        val errors = bloodPressureFieldErrors("120,5", "80,2")
        assertNull(errors.systolic)
        assertNull(errors.diastolic)
    }

    @Test
    fun `systolic must be strictly greater than diastolic`() {
        assertFalse(isBloodPressureInputValid("80", "80"))
        assertFalse(isBloodPressureInputValid("70", "80"))
        val errors = bloodPressureFieldErrors("80", "80")
        assertEquals("Must be above diastolic", errors.systolic)
        assertNull(errors.diastolic)
    }

    @Test
    fun `values outside Health Connect range are rejected`() {
        assertFalse(isBloodPressureInputValid("19", "15"))
        assertFalse(isBloodPressureInputValid("201", "80"))
        assertFalse(isBloodPressureInputValid("120", "9"))
        assertFalse(isBloodPressureInputValid("120", "181"))

        val errorsLow = bloodPressureFieldErrors("19", "9")
        assertEquals("Must be between $MIN_SYSTOLIC and $MAX_SYSTOLIC mmHg", errorsLow.systolic)
        assertEquals("Must be between $MIN_DIASTOLIC and $MAX_DIASTOLIC mmHg", errorsLow.diastolic)
    }

    @Test
    fun `blank or non-number inputs are handled`() {
        assertFalse(isBloodPressureInputValid("", ""))
        assertFalse(isBloodPressureInputValid("abc", "xyz"))

        val blankErrors = bloodPressureFieldErrors("", "")
        assertNull(blankErrors.systolic)
        assertNull(blankErrors.diastolic)

        val nonNumErrors = bloodPressureFieldErrors("abc", "xyz")
        assertEquals("Enter a number", nonNumErrors.systolic)
        assertEquals("Enter a number", nonNumErrors.diastolic)
    }
}
