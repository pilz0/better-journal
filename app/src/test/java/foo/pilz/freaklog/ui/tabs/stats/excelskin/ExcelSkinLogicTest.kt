package foo.pilz.freaklog.ui.tabs.stats.excelskin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExcelSkinLogicTest {

    @Test
    fun fileNameDeterministicAndInRange() {
        assertEquals(excelFileNameFor(0), excelFileNameFor(EXCEL_FILE_NAMES.size))
        assertTrue(EXCEL_FILE_NAMES.contains(excelFileNameFor(-3)))
        assertTrue(EXCEL_FILE_NAMES.contains(randomExcelFileName()))
    }

    @Test
    fun sumFormulaCoversDataRows() {
        assertEquals("=SUM(B2:B6)", excelSumFormula(5))
        assertEquals("=SUM(B2:B2)", excelSumFormula(1))
        assertEquals("=SUM(B2:B2)", excelSumFormula(0))
    }
}
