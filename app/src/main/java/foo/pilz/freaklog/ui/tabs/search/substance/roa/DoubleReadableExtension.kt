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

fun Double.toReadableString(): String {
    val absValue = kotlin.math.abs(this)
    val df = when {
        absValue >= 100 -> DecimalFormat("#")
        absValue >= 10 -> DecimalFormat("#.#")
        absValue >= 1 -> DecimalFormat("#.##")
        else -> DecimalFormat("#.###")
    }
    // Ensure we use dot as decimal separator regardless of locale
    val symbols = java.text.DecimalFormatSymbols(java.util.Locale.US)
    df.decimalFormatSymbols = symbols
    return df.format(this)
}

fun Double.toPreservedString(): String {
    return this.toString()
}
