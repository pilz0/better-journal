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

package com.isaakhanimann.journal.ui.utils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class AxisDrawableUtils {

    public static List<Instant> getInstantsBetween(Instant startTime, Instant endTime, long stepSizeInHours) {
        Instant firstDate = nearestFullHourInTheFuture(startTime);
        List<Instant> fullHours = new ArrayList<>();
        Instant checkTime = firstDate;
        while (checkTime.isBefore(endTime)) {
            fullHours.add(checkTime);
            checkTime = checkTime.plus(stepSizeInHours, ChronoUnit.HOURS);
        }
        return fullHours;
    }

    public static Instant nearestFullHourInTheFuture(Instant instant) {
        Instant oneHourInFuture = instant.plus(1, ChronoUnit.HOURS);
        LocalDateTime dateTime = oneHourInFuture.atZone(ZoneId.systemDefault()).toLocalDateTime();
        int seconds = dateTime.getSecond();
        int minutes = dateTime.getMinute();
        LocalDateTime newDateTime = dateTime.minusMinutes(minutes).minusSeconds(seconds);
        return newDateTime.atZone(ZoneId.systemDefault()).toInstant();
    }
}
