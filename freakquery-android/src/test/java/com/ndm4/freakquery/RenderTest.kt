package com.ndm4.freakquery

import org.junit.Assert.assertEquals
import org.junit.Test

class RenderTest {

  @Test
  fun render_replacesEachTagAndKeepsSurroundingText() {
    assertEquals("a  b  c", FreakQuery.render("a {{}} b {{  }} c", emptyList()))
  }

  @Test
  fun render_keepsUnclosedOpeningsLiterally() {
    assertEquals("x {{ y", FreakQuery.render("x {{ y", emptyList()))
    assertEquals("{{{{", FreakQuery.render("{{{{", emptyList()))
  }

  @Test(timeout = 5_000)
  fun render_manyUnclosedOpeningsStayLinear() {
    val template = "{{".repeat(200_000)
    assertEquals(template, FreakQuery.render(template, emptyList()))
  }

  @Test
  fun render_groupedRowsMetricsDoNotThrowClassCastException() {
    val rows =
        listOf(
            mapOf("substance" to "MDMA", "dose" to 100, "unit" to "mg", "time" to 1700000000000L),
            mapOf("substance" to "MDMA", "dose" to 50, "unit" to "mg", "time" to 1700003600000L),
        )
    val sumResult = FreakQuery.query("binges | group_sum", rows)
    assertEquals("150", sumResult)

    val durationResult = FreakQuery.query("binges | group_duration", rows)
    assertEquals("1h 0m", durationResult)

    val fieldResult = FreakQuery.query("binges | field=dose", rows)
    assertEquals("100", fieldResult)

    val mainSubstanceResult = FreakQuery.query("binges | main_substance", rows)
    assertEquals("MDMA", mainSubstanceResult)
  }

  @Test
  fun render_emptyGroupedRows_handlesMetricsGracefully() {
    assertEquals("0", FreakQuery.query("binges | group_sum", emptyList()))
    assertEquals("0s", FreakQuery.query("binges | group_duration", emptyList()))
    assertEquals("", FreakQuery.query("binges | main_substance", emptyList()))
  }

  @Test
  fun render_boundedOutputStopsWhenBudgetExceeded() {
    val config = FreakQueryConfig(maxOutputLength = 20)
    val result = FreakQuery.render("abcdefghijklmnopqrstuvwxyz1234567890", emptyList(), config)
    assertEquals(20, result.length)
    assertEquals("abcdefghijklmnopqrst", result)
  }

  @Test
  fun jsonRender_compactWithoutIndentation() {
    val rows = listOf(mapOf("substance" to "LSD", "dose" to 100))
    val result = FreakQuery.query("all | json", rows)
    org.junit.Assert.assertFalse(result.contains("\n"))
  }
}
