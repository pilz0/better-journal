/*
 * Copyright (c) 2024. Isaak Hanimann.
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

package foo.pilz.freaklog.ui.tabs.search.substance.roa

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

// Ensure we use dot as decimal separator regardless of locale
private val usSymbols = DecimalFormatSymbols(Locale.US)

// DecimalFormat is not thread-safe, so each thread gets its own set, indexed by precision:
// "#", "#.#", "#.##", "#.###"
private val readableFormats = ThreadLocal.withInitial {
    arrayOf("#", "#.#", "#.##", "#.###").map { DecimalFormat(it, usSymbols) }
}

fun Double.toReadableString(): String {
    if (!this.isFinite()) return ""
    val absValue = kotlin.math.abs(this)
    val fractionDigits = when {
        absValue >= 100 -> 0
        absValue >= 10 -> 1
        absValue >= 1 -> 2
        else -> 3
    }
    return readableFormats.get()!![fractionDigits].format(this)
}

fun Double.toPreservedString(): String {
    return this.toString()
}
