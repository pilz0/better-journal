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

package com.isaakhanimann.journal.ui.tabs.search.substance.roa

import com.isaakhanimann.journal.ui.utils.DoubleReadableUtils
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale


fun Double.toReadableString(): String {
    return DoubleReadableUtils.toReadableString(this)
}

fun Double.toPreservedString(): String {
    return DoubleReadableUtils.toPreservedString(this)
}

fun roundToSignificantDigits(value: Double, significantDigits: Int): Double {
    return DoubleReadableUtils.roundToSignificantDigits(value, significantDigits)
}

fun formatToMaximumFractionDigits(value: Double, maximumFractionDigits: Int): String {
    return DoubleReadableUtils.formatToMaximumFractionDigits(value, maximumFractionDigits)
}
