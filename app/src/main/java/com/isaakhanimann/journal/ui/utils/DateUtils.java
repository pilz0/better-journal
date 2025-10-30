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

import java.text.DateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;

public class DateUtils {

    public static Instant getInstant(int year, int month, int day, int hourOfDay, int minute) {
        LocalDateTime dateTime = LocalDateTime.of(year, month, day, hourOfDay, minute);
        return dateTime.atZone(ZoneId.systemDefault()).toInstant();
    }

    public static String getStringOfPattern(Instant instant, String pattern) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return dateTime.format(formatter);
    }

    public static String getDateWithWeekdayText(Instant instant) {
        return getStringOfPattern(instant, "EEE dd MMM yyyy");
    }

    public static String getShortWeekdayText(Instant instant) {
        return getStringOfPattern(instant, "EEE");
    }

    public static String getShortTimeWithWeekdayText(Instant instant) {
        return getShortWeekdayText(instant) + " " + getShortTimeText(instant);
    }

    public static String getShortTimeText(Instant instant) {
        DateFormat timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault());
        Date date = Date.from(instant);
        return timeFormat.format(date);
    }

    public static String getStringOfPattern(LocalDateTime localDateTime, String pattern) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return localDateTime.format(formatter);
    }

    public static String getDateWithWeekdayText(LocalDateTime localDateTime) {
        return getStringOfPattern(localDateTime, "EEE dd MMM yyyy");
    }

    public static String getShortTimeText(LocalDateTime localDateTime) {
        Instant instant = getInstant(localDateTime);
        return getShortTimeText(instant);
    }

    public static LocalDateTime getLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    public static Instant getInstant(LocalDateTime localDateTime) {
        return localDateTime.atZone(ZoneId.systemDefault()).toInstant();
    }
}
