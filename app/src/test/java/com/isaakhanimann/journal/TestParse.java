/*
 * Copyright (c) 2022. Isaak Hanimann.
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

package com.isaakhanimann.journal;

import com.isaakhanimann.journal.data.substances.classes.SubstanceFile;
import com.isaakhanimann.journal.data.substances.parse.SubstanceParser;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class TestParse {

    @Test
    public void noCrash() {
        SubstanceParser parser = new SubstanceParser();
        SubstanceFile substances = parser.parseSubstanceFile("error");
        assertTrue(substances.getSubstances().isEmpty());
    }

    @Test
    public void noCrashExtract() {
        SubstanceParser parser = new SubstanceParser();
        String result = parser.extractSubstanceString("error");
        assertTrue(result == null);
    }

    @Test
    public void testExtractSubstancesString() {
        String text = "{\n" +
            "  \"data\": {\n" +
            "    \"substances\": [\n" +
            "      {\n" +
            "        \"name\": \"Armodafinil\",\n" +
            "        \"roas\": [\n" +
            "          {\n" +
            "            \"name\": \"oral\"\n" +
            "          }\n" +
            "        ]\n" +
            "      }\n" +
            "    ]\n" +
            "  }\n" +
            "}";
        SubstanceParser parser = new SubstanceParser();
        String result = parser.extractSubstanceString(text);
        assertTrue("[{\"name\":\"Armodafinil\",\"roas\":[{\"name\":\"oral\"}]}]".equals(result));
    }
}
