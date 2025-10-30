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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public class TimeDifferenceUtils {

    public static String getTimeDifferenceText(Instant fromInstant, Instant toInstant) {
        Duration diff = Duration.between(fromInstant, toInstant);
        float minutes = diff.toMinutes();
        float hours = minutes / 60;
        float days = hours / 24;
        float weeks = days / 7;
        float months = weeks / 4;
        float years = months / 12;
        
        if (years > 2) {
            long yearsBetween = ChronoUnit.YEARS.between(
                DateUtils.getLocalDateTime(fromInstant),
                DateUtils.getLocalDateTime(toInstant)
            );
            return yearsBetween + " years";
        } else if (months > 3) {
            long monthsBetween = ChronoUnit.MONTHS.between(
                DateUtils.getLocalDateTime(fromInstant),
                DateUtils.getLocalDateTime(toInstant)
            );
            return monthsBetween + " months";
        } else if (weeks > 4) {
            long weeksBetween = ChronoUnit.WEEKS.between(
                DateUtils.getLocalDateTime(fromInstant),
                DateUtils.getLocalDateTime(toInstant)
            );
            return weeksBetween + " weeks";
        } else if (days > 2) {
            long daysBetween = ChronoUnit.DAYS.between(
                DateUtils.getLocalDateTime(fromInstant),
                DateUtils.getLocalDateTime(toInstant)
            );
            return daysBetween + " days";
        } else if (hours > 3) {
            Duration duration = Duration.between(fromInstant, toInstant);
            int hoursRounded = (int) (duration.toMinutes() / 60.0 + 0.5);
            return hoursRounded + " hours";
        } else {
            long minutesBetween = ChronoUnit.MINUTES.between(
                DateUtils.getLocalDateTime(fromInstant),
                DateUtils.getLocalDateTime(toInstant)
            );
            return minutesBetween + " minutes";
        }
    }
}
