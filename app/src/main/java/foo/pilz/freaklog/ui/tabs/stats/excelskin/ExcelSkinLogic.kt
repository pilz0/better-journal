package foo.pilz.freaklog.ui.tabs.stats.excelskin

const val EXCEL_SKIN_OPEN_PROBABILITY = 0.05
const val EXCEL_SKIN_MIN_DELAY_MS = 1_800_000L
const val EXCEL_SKIN_MAX_DELAY_MS = 5_400_000L

val EXCEL_FILE_NAMES = listOf(
    "Mengenübersicht.xlsx",
    "Fuckass Revision 2 2026.xlsx",
    "IHateMyJob.csv"
)

fun excelFileNameFor(seed: Int): String = EXCEL_FILE_NAMES[Math.floorMod(seed, EXCEL_FILE_NAMES.size)]

fun randomExcelFileName(): String = EXCEL_FILE_NAMES.random()

fun excelSumFormula(dataRowCount: Int): String =
    "=SUM(B2:B${(dataRowCount + 1).coerceAtLeast(2)})"
