package foo.pilz.freaklog.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        assertNull(evaluateNumericExpression("5 / 0")) // Division by zero produces infinite, which our parser filters
    }
}
