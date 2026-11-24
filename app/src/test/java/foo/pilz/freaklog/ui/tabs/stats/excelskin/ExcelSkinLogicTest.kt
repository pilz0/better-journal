package foo.pilz.freaklog.ui.tabs.stats.excelskin

import org.junit.Assert.assertEquals
import org.junit.Test

class ExcelSkinLogicTest {

  @Test
  fun sumFormulaCoversDataRows() {
    assertEquals("=SUM(B2:B6)", excelSumFormula(5))
    assertEquals("=SUM(B2:B2)", excelSumFormula(1))
    assertEquals("=SUM(B2:B2)", excelSumFormula(0))
  }
}
