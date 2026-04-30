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

package foo.pilz.freaklog.data.export

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class InstantSerializerTest {

    private val json = Json

    @Test
    fun testDescriptor_name() {
        assertEquals("Instant", InstantSerializer.descriptor.serialName)
    }

    @Test
    fun testSerialize_knownInstant() {
        val instant = Instant.ofEpochMilli(1672531200000L) // 2023-01-01T00:00:00Z
        val serialized = json.encodeToJsonElement(InstantSerializer, instant)
        assertEquals(1672531200000L, (serialized as JsonPrimitive).long)
    }

    @Test
    fun testSerialize_epochStart() {
        val instant = Instant.EPOCH
        val serialized = json.encodeToJsonElement(InstantSerializer, instant)
        assertEquals(0L, (serialized as JsonPrimitive).long)
    }

    @Test
    fun testSerialize_futureDate() {
        val instant = Instant.ofEpochMilli(2000000000000L) // 2033-05-18T03:33:20Z
        val serialized = json.encodeToJsonElement(InstantSerializer, instant)
        assertEquals(2000000000000L, (serialized as JsonPrimitive).long)
    }

    @Test
    fun testDeserialize_knownValue() {
        val jsonElement = JsonPrimitive(1672531200000.0)
        val deserialized = json.decodeFromJsonElement(InstantSerializer, jsonElement)
        assertEquals(1672531200000L, deserialized.toEpochMilli())
    }

    @Test
    fun testDeserialize_epochStart() {
        val jsonElement = JsonPrimitive(0.0)
        val deserialized = json.decodeFromJsonElement(InstantSerializer, jsonElement)
        assertEquals(Instant.EPOCH, deserialized)
    }

    @Test
    fun testRoundtrip_now() {
        val original = Instant.now()
        val serialized = json.encodeToJsonElement(InstantSerializer, original)
        val jsonWithDouble = JsonPrimitive((serialized as JsonPrimitive).long.toDouble())
        val deserialized = json.decodeFromJsonElement(InstantSerializer, jsonWithDouble)
        assertEquals(original.toEpochMilli(), deserialized.toEpochMilli())
    }

    @Test
    fun testRoundtrip_specificDate() {
        val original = Instant.ofEpochMilli(1700000000000L)
        val serialized = json.encodeToJsonElement(InstantSerializer, original)
        val jsonWithDouble = JsonPrimitive((serialized as JsonPrimitive).long.toDouble())
        val deserialized = json.decodeFromJsonElement(InstantSerializer, jsonWithDouble)
        assertEquals(original, deserialized)
    }

    @Test
    fun testDeserialize_preservesMilliseconds() {
        val millis = 1672531200123L
        val jsonElement = JsonPrimitive(millis.toDouble())
        val deserialized = json.decodeFromJsonElement(InstantSerializer, jsonElement)
        assertEquals(millis, deserialized.toEpochMilli())
    }

    @Test
    fun testMultipleInstants() {
        val instants = listOf(
            Instant.ofEpochMilli(0L),
            Instant.ofEpochMilli(1000000000000L),
            Instant.ofEpochMilli(1500000000000L),
            Instant.ofEpochMilli(1672531200000L),
            Instant.now()
        )

        instants.forEach { original ->
            val serialized = json.encodeToJsonElement(InstantSerializer, original)
            val jsonWithDouble = JsonPrimitive((serialized as JsonPrimitive).long.toDouble())
            val deserialized = json.decodeFromJsonElement(InstantSerializer, jsonWithDouble)
            assertEquals(original.toEpochMilli(), deserialized.toEpochMilli())
        }
    }
}
