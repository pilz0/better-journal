package foo.pilz.freaklog.ui.tabs.settings.intakelimits

import foo.pilz.freaklog.data.room.experiences.ExperienceRepository
import java.time.Instant

suspend fun loadIntakeLimitStatuses(
    experienceRepo: ExperienceRepository,
    substanceName: String,
    pendingDose: Double?,
    pendingUnits: String?,
    pendingCustomUnitId: Int?,
    excludeIngestionId: Int? = null,
): List<IntakeLimitStatus> {
    val limits = experienceRepo.getIntakeLimitsForSubstance(substanceName).filter { it.isEnabled }
    if (limits.isEmpty()) return emptyList()
    val now = Instant.now()
    val maxWindow = limits.maxOf { it.windowSeconds }
    val since = now.minusSeconds(maxWindow)
    val past = experienceRepo.getIngestionsWithCustomUnitsForSubstanceSince(substanceName, since)
        .filter { excludeIngestionId == null || it.ingestion.id != excludeIngestionId }
        .map { LimitIngestion(it.pureDose, it.originalUnit, it.ingestion.time) }
    val pending =
        pendingLimitIngestion(experienceRepo, pendingDose, pendingUnits, pendingCustomUnitId, now)
    return limits.map { evaluateIntakeLimit(it, now, past, pending) }
}

suspend fun loadBreachedIntakeLimits(
    experienceRepo: ExperienceRepository,
    substanceName: String,
    pendingDose: Double?,
    pendingUnits: String?,
    pendingCustomUnitId: Int?,
    excludeIngestionId: Int? = null,
): List<IntakeLimitStatus> =
    loadIntakeLimitStatuses(
        experienceRepo = experienceRepo,
        substanceName = substanceName,
        pendingDose = pendingDose,
        pendingUnits = pendingUnits,
        pendingCustomUnitId = pendingCustomUnitId,
        excludeIngestionId = excludeIngestionId,
    ).filter { it.crossesWarning }

private suspend fun pendingLimitIngestion(
    experienceRepo: ExperienceRepository,
    pendingDose: Double?,
    pendingUnits: String?,
    pendingCustomUnitId: Int?,
    now: Instant,
): LimitIngestion {
    if (pendingCustomUnitId != null) {
        val customUnit = experienceRepo.getCustomUnit(pendingCustomUnitId)
        val perUnit = customUnit?.dose
        return LimitIngestion(
            effectiveDose = if (pendingDose != null && perUnit != null) pendingDose * perUnit else null,
            unit = customUnit?.originalUnit,
            time = now,
        )
    }
    return LimitIngestion(
        effectiveDose = pendingDose,
        unit = pendingUnits?.ifBlank { null },
        time = now,
    )
}
