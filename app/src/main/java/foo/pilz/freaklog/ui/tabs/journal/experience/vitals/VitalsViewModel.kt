/*
 * Copyright (c) 2026. Freaklog.
 * This file is part of Freaklog.
 *
 * Freaklog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Freaklog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Freaklog.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.
 */

package foo.pilz.freaklog.ui.tabs.journal.experience.vitals

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import foo.pilz.freaklog.data.room.experiences.entities.BloodPressureReading
import foo.pilz.freaklog.ui.tabs.settings.combinations.UserPreferences
import foo.pilz.freaklog.ui.utils.stateInVm
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/** The time span heart rate is shown for: first ingestion until [AFTER_LAST_INGESTION] after the last, capped at now. */
fun heartRateWindow(ingestionTimes: List<Instant>, now: Instant): Pair<Instant, Instant>? {
    val start = ingestionTimes.minOrNull() ?: return null
    val end = minOf(ingestionTimes.max().plus(AFTER_LAST_INGESTION), now)
    return if (end.isAfter(start)) start to end else null
}

private val AFTER_LAST_INGESTION: Duration = Duration.ofHours(12)

@HiltViewModel
class VitalsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val experienceRepo: ExperienceRepository,
    private val userPreferences: UserPreferences,
) : ViewModel() {

    private val experienceIdFlow = MutableStateFlow<Int?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val readingsFlow: StateFlow<List<BloodPressureReading>> = experienceIdFlow
        .flatMapLatest { id ->
            if (id == null) MutableStateFlow(emptyList()) else experienceRepo.getBloodPressureReadingsFlow(id)
        }
        .stateInVm(viewModelScope, emptyList())

    private val _heartRateSamples = MutableStateFlow<List<HeartRateSample>>(emptyList())
    val heartRateSamples: StateFlow<List<HeartRateSample>> = _heartRateSamples.asStateFlow()

    val isHeartRateEnabledFlow = userPreferences.isHeartRateEnabledFlow.stateInVm(viewModelScope, false)

    fun setExperience(experienceId: Int) {
        if (experienceIdFlow.value == experienceId) return
        experienceIdFlow.value = experienceId
        loadHeartRate()
    }

    /** Reads heart rate for this experience when the user enabled it and Health Connect allows it. */
    @Suppress("TooGenericExceptionCaught")
    fun loadHeartRate() {
        val experienceId = experienceIdFlow.value ?: return
        viewModelScope.launch {
            val isUsable = userPreferences.isHeartRateEnabledFlow.first() &&
                HeartRateHealthConnect.isAvailable(context)
            if (!isUsable) return@launch
            _heartRateSamples.value = try {
                val times = experienceRepo.getIngestionsWithCompanions(experienceId).map { it.ingestion.time }
                val window = heartRateWindow(times, Instant.now())
                if (window != null && HeartRateHealthConnect.isPermissionGranted(context)) {
                    HeartRateHealthConnect.read(context, window.first, window.second)
                } else {
                    emptyList()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Health Connect being unavailable or revoked must never break the experience screen.
                emptyList()
            }
        }
    }

    fun saveReading(existing: BloodPressureReading?, systolic: String, diastolic: String, pulse: String) {
        val experienceId = experienceIdFlow.value ?: return
        if (!isBloodPressureInputValid(systolic, diastolic, pulse)) return
        val sys = systolic.trim().toInt()
        val dia = diastolic.trim().toInt()
        val bpm = pulse.trim().toIntOrNull()
        viewModelScope.launch {
            if (existing == null) {
                experienceRepo.insert(
                    BloodPressureReading(
                        experienceId = experienceId,
                        time = Instant.now(),
                        systolic = sys,
                        diastolic = dia,
                        pulse = bpm,
                    )
                )
            } else {
                experienceRepo.update(existing.copy(systolic = sys, diastolic = dia, pulse = bpm))
            }
        }
    }

    fun deleteReading(reading: BloodPressureReading) {
        viewModelScope.launch { experienceRepo.delete(reading) }
    }
}
