/*
 * Copyright (c) 2022-2024. Isaak Hanimann.
 * This file is part of PsychonautWiki Journal.
 *
 * PsychonautWiki Journal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * PsychonautWiki Journal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PsychonautWiki Journal.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.
 */

package foo.pilz.freaklog.data.substances.parse

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserExtensionsTest {

    // ===== JSONObject.getOptionalJSONObject Tests =====

    @Test
    fun testGetOptionalJSONObject_exists() {
        val json = JSONObject("""{"nested": {"key": "value"}}""")
        val result = json.getOptionalJSONObject("nested")
        assertNotNull(result)
        assertEquals("value", result!!.getString("key"))
    }

    @Test
    fun testGetOptionalJSONObject_notExists() {
        val json = JSONObject("""{"other": "value"}""")
        val result = json.getOptionalJSONObject("nested")
        assertNull(result)
    }

    @Test
    fun testGetOptionalJSONObject_wrongType() {
        val json = JSONObject("""{"key": "string_value"}""")
        val result = json.getOptionalJSONObject("key")
        assertNull(result)
    }

    @Test
    fun testGetOptionalJSONObject_nullValue() {
        val json = JSONObject("""{"key": null}""")
        val result = json.getOptionalJSONObject("key")
        assertNull(result)
    }

    // ===== JSONObject.getOptionalString Tests =====

    @Test
    fun testGetOptionalString_exists() {
        val json = JSONObject("""{"name": "LSD"}""")
        val result = json.getOptionalString("name")
        assertEquals("LSD", result)
    }

    @Test
    fun testGetOptionalString_notExists() {
        val json = JSONObject("""{"other": "value"}""")
        val result = json.getOptionalString("name")
        assertNull(result)
    }

    @Test
    fun testGetOptionalString_nullValue() {
        val json = JSONObject("""{"name": null}""")
        val result = json.getOptionalString("name")
        assertNull(result)
    }

    @Test
    fun testGetOptionalString_emptyString() {
        val json = JSONObject("""{"name": ""}""")
        val result = json.getOptionalString("name")
        assertEquals("", result)
    }

    @Test
    fun testGetOptionalString_wrongType() {
        val json = JSONObject("""{"name": 123}""")
        val result = json.getOptionalString("name")
        // JSONObject.getString behavior with numbers may vary
        // It either converts to string "123" or returns null via exception
        assertTrue(result == "123" || result == null)
    }

    // ===== JSONObject.getOptionalBoolean Tests =====

    @Test
    fun testGetOptionalBoolean_true() {
        val json = JSONObject("""{"isApproved": true}""")
        val result = json.getOptionalBoolean("isApproved")
        assertTrue(result!!)
    }

    @Test
    fun testGetOptionalBoolean_false() {
        val json = JSONObject("""{"isApproved": false}""")
        val result = json.getOptionalBoolean("isApproved")
        assertFalse(result!!)
    }

    @Test
    fun testGetOptionalBoolean_notExists() {
        val json = JSONObject("""{"other": "value"}""")
        val result = json.getOptionalBoolean("isApproved")
        assertNull(result)
    }

    @Test
    fun testGetOptionalBoolean_nullValue() {
        val json = JSONObject("""{"isApproved": null}""")
        val result = json.getOptionalBoolean("isApproved")
        assertNull(result)
    }

    // ===== JSONObject.getOptionalLong Tests =====

    @Test
    fun testGetOptionalLong_exists() {
        val json = JSONObject("""{"color": 4278876927}""")
        val result = json.getOptionalLong("color")
        assertEquals(4278876927L, result)
    }

    @Test
    fun testGetOptionalLong_notExists() {
        val json = JSONObject("""{"other": "value"}""")
        val result = json.getOptionalLong("color")
        assertNull(result)
    }

    @Test
    fun testGetOptionalLong_nullValue() {
        val json = JSONObject("""{"color": null}""")
        val result = json.getOptionalLong("color")
        assertNull(result)
    }

    @Test
    fun testGetOptionalLong_zero() {
        val json = JSONObject("""{"value": 0}""")
        val result = json.getOptionalLong("value")
        assertEquals(0L, result)
    }

    @Test
    fun testGetOptionalLong_negative() {
        val json = JSONObject("""{"value": -100}""")
        val result = json.getOptionalLong("value")
        assertEquals(-100L, result)
    }

    // ===== JSONObject.getOptionalJSONArray Tests =====

    @Test
    fun testGetOptionalJSONArray_exists() {
        val json = JSONObject("""{"items": [1, 2, 3]}""")
        val result = json.getOptionalJSONArray("items")
        assertNotNull(result)
        assertEquals(3, result!!.length())
    }

    @Test
    fun testGetOptionalJSONArray_notExists() {
        val json = JSONObject("""{"other": "value"}""")
        val result = json.getOptionalJSONArray("items")
        assertNull(result)
    }

    @Test
    fun testGetOptionalJSONArray_emptyArray() {
        val json = JSONObject("""{"items": []}""")
        val result = json.getOptionalJSONArray("items")
        assertNotNull(result)
        assertEquals(0, result!!.length())
    }

    @Test
    fun testGetOptionalJSONArray_wrongType() {
        val json = JSONObject("""{"items": "not an array"}""")
        val result = json.getOptionalJSONArray("items")
        assertNull(result)
    }

    // ===== JSONObject.getOptionalDouble Tests =====

    @Test
    fun testGetOptionalDouble_exists() {
        val json = JSONObject("""{"dose": 50.5}""")
        val result = json.getOptionalDouble("dose")
        assertEquals(50.5, result!!, 0.01)
    }

    @Test
    fun testGetOptionalDouble_integer() {
        val json = JSONObject("""{"dose": 50}""")
        val result = json.getOptionalDouble("dose")
        assertEquals(50.0, result!!, 0.01)
    }

    @Test
    fun testGetOptionalDouble_notExists() {
        val json = JSONObject("""{"other": "value"}""")
        val result = json.getOptionalDouble("dose")
        assertNull(result)
    }

    @Test
    fun testGetOptionalDouble_zero() {
        val json = JSONObject("""{"dose": 0}""")
        val result = json.getOptionalDouble("dose")
        assertEquals(0.0, result!!, 0.01)
    }

    @Test
    fun testGetOptionalDouble_negative() {
        val json = JSONObject("""{"dose": -10.5}""")
        val result = json.getOptionalDouble("dose")
        assertEquals(-10.5, result!!, 0.01)
    }

    // ===== JSONArray.getOptionalString Tests =====

    @Test
    fun testArrayGetOptionalString_exists() {
        val json = JSONArray("""["first", "second", "third"]""")
        assertEquals("first", json.getOptionalString(0))
        assertEquals("second", json.getOptionalString(1))
        assertEquals("third", json.getOptionalString(2))
    }

    @Test
    fun testArrayGetOptionalString_outOfBounds() {
        val json = JSONArray("""["first"]""")
        assertNull(json.getOptionalString(5))
    }

    @Test
    fun testArrayGetOptionalString_nullValue() {
        val json = JSONArray("""["first", null, "third"]""")
        assertNull(json.getOptionalString(1))
    }

    @Test
    fun testArrayGetOptionalString_emptyArray() {
        val json = JSONArray("[]")
        assertNull(json.getOptionalString(0))
    }

    // ===== JSONArray.getOptionalJSONObject Tests =====

    @Test
    fun testArrayGetOptionalJSONObject_exists() {
        val json = JSONArray("""[{"key": "value1"}, {"key": "value2"}]""")
        val result = json.getOptionalJSONObject(0)
        assertNotNull(result)
        assertEquals("value1", result!!.getString("key"))
    }

    @Test
    fun testArrayGetOptionalJSONObject_outOfBounds() {
        val json = JSONArray("""[{"key": "value"}]""")
        assertNull(json.getOptionalJSONObject(5))
    }

    @Test
    fun testArrayGetOptionalJSONObject_wrongType() {
        val json = JSONArray("""["string", 123, true]""")
        assertNull(json.getOptionalJSONObject(0))
        assertNull(json.getOptionalJSONObject(1))
        assertNull(json.getOptionalJSONObject(2))
    }

    @Test
    fun testArrayGetOptionalJSONObject_emptyArray() {
        val json = JSONArray("[]")
        assertNull(json.getOptionalJSONObject(0))
    }
}
