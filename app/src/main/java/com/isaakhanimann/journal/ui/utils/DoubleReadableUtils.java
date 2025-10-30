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

package com.isaakhanimann.journal.ui.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;

public class DoubleReadableUtils {

    public static String toReadableString(double value) {
        int numberOfSignificantDigits = (value > 1) ? 3 : 2;
        double roundedNumber = roundToSignificantDigits(value, numberOfSignificantDigits);
        return formatToMaximumFractionDigits(roundedNumber, 6);
    }

    public static String toPreservedString(double value) {
        NumberFormat numberFormat = NumberFormat.getInstance(Locale.US);
        numberFormat.setGroupingUsed(false);
        return numberFormat.format(value);
    }

    public static double roundToSignificantDigits(double value, int significantDigits) {
        if (value == 0.0) return 0.0;
        BigDecimal bigDecimal = new BigDecimal(value);
        int scale = significantDigits - bigDecimal.precision() + bigDecimal.scale();
        return bigDecimal.setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    public static String formatToMaximumFractionDigits(double value, int maximumFractionDigits) {
        DecimalFormat df = new DecimalFormat();
        df.setDecimalFormatSymbols(DecimalFormatSymbols.getInstance(Locale.US));
        df.setDecimalSeparatorAlwaysShown(false);
        df.setMinimumFractionDigits(0);
        df.setMaximumFractionDigits(maximumFractionDigits);
        df.setGroupingUsed(false);
        
        return df.format(value);
    }
}
