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
}
