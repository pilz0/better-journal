/*
 * Copyright (c) 2022-2023. Isaak Hanimann.
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

package foo.pilz.freaklog.data.webhook

import org.junit.Assert.assertEquals
import org.junit.Test

class WebhookServiceTest {

    private val webhookService = WebhookService()

    @Test
    fun testProcessTemplate_allValuesPresent() {
        val template = "{user}: [{dose} {unit} ]{substance} via {route}[ ({site})][\n> {note}]"
        val values = mapOf(
            "user" to "Alice",
            "dose" to "100",
            "unit" to "mg",
            "substance" to "Caffeine",
            "route" to "oral",
            "site" to "arm",
            "note" to "Feeling good"
        )
        
        val result = webhookService.processTemplate(template, values)
        
        assertEquals("Alice: 100 mg Caffeine via oral (arm)\n> Feeling good", result)
    }

    @Test
    fun testProcessTemplate_emptyDose() {
        val template = "{user}: [{dose} {unit} ]{substance} via {route}[ ({site})][\n> {note}]"
        val values = mapOf(
            "user" to "Bob",
            "dose" to "",
            "unit" to "",
            "substance" to "LSD",
            "route" to "sublingual",
            "site" to "",
            "note" to "Trip report"
        )
        
        val result = webhookService.processTemplate(template, values)
        
        assertEquals("Bob: LSD via sublingual\n> Trip report", result)
    }

    @Test
    fun testProcessTemplate_emptySite() {
        val template = "{user}: [{dose} {unit} ]{substance} via {route}[ ({site})][\n> {note}]"
        val values = mapOf(
            "user" to "Charlie",
            "dose" to "50",
            "unit" to "μg",
            "substance" to "LSD",
            "route" to "oral",
            "site" to "",
            "note" to "Starting"
        )
        
        val result = webhookService.processTemplate(template, values)
        
        assertEquals("Charlie: 50 μg LSD via oral\n> Starting", result)
    }

    @Test
    fun testProcessTemplate_emptyNote() {
        val template = "{user}: [{dose} {unit} ]{substance} via {route}[ ({site})][\n> {note}]"
        val values = mapOf(
            "user" to "Dave",
            "dose" to "200",
            "unit" to "mg",
            "substance" to "MDMA",
            "route" to "oral",
            "site" to "stomach",
            "note" to ""
        )
        
        val result = webhookService.processTemplate(template, values)
        
        assertEquals("Dave: 200 mg MDMA via oral (stomach)", result)
    }

    @Test
    fun testProcessTemplate_allOptionalEmpty() {
        val template = "{user}: [{dose} {unit} ]{substance} via {route}[ ({site})][\n> {note}]"
        val values = mapOf(
            "user" to "Eve",
            "dose" to "",
            "unit" to "",
            "substance" to "Cannabis",
            "route" to "smoked",
            "site" to "",
            "note" to ""
        )
        
        val result = webhookService.processTemplate(template, values)
        
        assertEquals("Eve: Cannabis via smoked", result)
    }

    @Test
    fun testProcessTemplate_simpleTemplate() {
        val template = "{user} took {substance}"
        val values = mapOf(
            "user" to "Frank",
            "substance" to "Aspirin"
        )
        
        val result = webhookService.processTemplate(template, values)
        
        assertEquals("Frank took Aspirin", result)
    }

    @Test
    fun testProcessTemplate_nestedBrackets() {
        // Note: The current implementation doesn't support true nested brackets.
        // It processes all bracket pairs at the same level independently.
        // This test shows the actual behavior - nested brackets are processed as separate blocks.
        val template = "{user}: {substance}[ {dose}][ {unit}]"
        val values = mapOf(
            "user" to "Grace",
            "substance" to "Ibuprofen",
            "dose" to "400",
            "unit" to "mg"
        )
        
        val result = webhookService.processTemplate(template, values)
        
        assertEquals("Grace: Ibuprofen 400 mg", result)
    }

    @Test
    fun testProcessTemplate_withHyperlink() {
        val template = "{user}: [{dose} {unit} ]{substance} via {route}"
        val values = mapOf(
            "user" to "Henry",
            "dose" to "100",
            "unit" to "mg",
            "substance" to "[Caffeine](<https://psychonautwiki.org/wiki/Caffeine>)",
            "route" to "oral"
        )
        
        val result = webhookService.processTemplate(template, values)
        
        assertEquals("Henry: 100 mg [Caffeine](<https://psychonautwiki.org/wiki/Caffeine>) via oral", result)
    }

    @Test
    fun testProcessTemplate_estimatedDose() {
        val template = "{user}: [{dose} {unit} ]{substance}"
        val values = mapOf(
            "user" to "Ivy",
            "dose" to "~25",
            "unit" to "mg",
            "substance" to "THC"
        )
        
        val result = webhookService.processTemplate(template, values)
        
        assertEquals("Ivy: ~25 mg THC", result)
    }

    @Test
    fun testProcessTemplate_unicodeCharacters() {
        val template = "{user}: [{dose} {unit} ]{substance} via {route}"
        val values = mapOf(
            "user" to "José",
            "dose" to "50",
            "unit" to "μg",
            "substance" to "LSD",
            "route" to "sublingual"
        )
        
        val result = webhookService.processTemplate(template, values)
        
        assertEquals("José: 50 μg LSD via sublingual", result)
    }
}
