package foo.pilz.freaklog.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DoseExpressionTest {

  @Test
  fun evaluateNumericExpression_handlesSimpleNumbers() {
    assertEquals(42.0, evaluateNumericExpression("42")!!, 1e-9)
    assertEquals(3.14, evaluateNumericExpression("3.14")!!, 1e-9)
    assertEquals(3.14, evaluateNumericExpression("3,14")!!, 1e-9)
    assertEquals(0.5, evaluateNumericExpression(".5")!!, 1e-9)
  }

  @Test
  fun evaluateNumericExpression_handlesBasicArithmetic() {
    assertEquals(15.0, evaluateNumericExpression("10 + 5")!!, 1e-9)
    assertEquals(7.5, evaluateNumericExpression("15 / 2")!!, 1e-9)
    assertEquals(37.5, evaluateNumericExpression("50 * 0.75")!!, 1e-9)
    assertEquals(6.0, evaluateNumericExpression("10 - 4")!!, 1e-9)
  }

  @Test
  fun evaluateNumericExpression_handlesOperatorPrecedence() {
    assertEquals(17.0, evaluateNumericExpression("2 + 3 * 5")!!, 1e-9)
    assertEquals(25.0, evaluateNumericExpression("(2 + 3) * 5")!!, 1e-9)
    assertEquals(8.0, evaluateNumericExpression("20 / (2 + 3) * 2")!!, 1e-9)
  }

  @Test
  fun evaluateNumericExpression_handlesUnaryOperators() {
    assertEquals(-5.0, evaluateNumericExpression("-5")!!, 1e-9)
    assertEquals(5.0, evaluateNumericExpression("+5")!!, 1e-9)
    assertEquals(-15.0, evaluateNumericExpression("-(10 + 5)")!!, 1e-9)
  }

  @Test
  fun evaluateNumericExpression_handlesWhitespaceAndDecimals() {
    assertEquals(10.5, evaluateNumericExpression("  10  +  0,5  ")!!, 1e-9)
  }

  @Test
  fun evaluateNumericExpression_returnsNullForInvalidInput() {
    assertNull(evaluateNumericExpression(""))
    assertNull(evaluateNumericExpression("   "))
    assertNull(evaluateNumericExpression("abc"))
    assertNull(evaluateNumericExpression("10 +"))
    assertNull(evaluateNumericExpression("((5 + 2)"))
    assertNull(
        evaluateNumericExpression("5 / 0")
    ) // Division by zero produces infinite, which our parser filters
  }

  @Test
  fun evaluateNumericExpression_returnsNullForNonFiniteLiterals() {
    assertNull(evaluateNumericExpression("NaN"))
    assertNull(evaluateNumericExpression("Infinity"))
    assertNull(evaluateNumericExpression("-Infinity"))
    assertNull(evaluateNumericExpression("+Infinity"))
  }

  @Test
  fun isValidDoseInput_validatesDoseAndStandardDeviation() {
    // Valid doses
    assertTrue(isValidDoseInput(10.0))
    assertTrue(isValidDoseInput(0.001))
    assertTrue(isValidDoseInput(100.0, 5.0))
    assertTrue(isValidDoseInput(100.0, 0.0))
    assertTrue(isValidDoseInput(MAX_VALID_DOSE))

    // Invalid doses
    assertFalse(isValidDoseInput(null))
    assertFalse(isValidDoseInput(0.0))
    assertFalse(isValidDoseInput(-5.0))
    assertFalse(isValidDoseInput(Double.NaN))
    assertFalse(isValidDoseInput(Double.POSITIVE_INFINITY))
    assertFalse(isValidDoseInput(MAX_VALID_DOSE + 1.0))

    // Invalid standard deviation
    assertFalse(isValidDoseInput(10.0, -1.0))
    assertFalse(isValidDoseInput(10.0, Double.NaN))
    assertFalse(isValidDoseInput(10.0, Double.POSITIVE_INFINITY))
    assertFalse(isValidDoseInput(10.0, MAX_VALID_DOSE + 1.0))
    assertFalse(isValidDoseInput(10.0, 1e308))
  }
}
