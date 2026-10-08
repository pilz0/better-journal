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

package foo.pilz.freaklog.data.room.experiences

import foo.pilz.freaklog.data.room.experiences.entities.CustomInteractionSeverity
import foo.pilz.freaklog.data.room.experiences.entities.CustomInteractionTargetType
import foo.pilz.freaklog.data.room.experiences.relations.CustomSubstanceWithEverything
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.classes.InteractionType
import foo.pilz.freaklog.data.substances.classes.roa.Roa
import foo.pilz.freaklog.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory view of every custom substance profile, keyed by substance name.
 *
 * Built-in substance data is available synchronously, and much of the app
 * (timelines, the interaction checker) relies on that. This keeps custom
 * profiles readable the same way instead of making those call sites suspend.
 */
@Singleton
class CustomSubstanceProfiles @Inject constructor(
    dao: CustomSubstanceDao,
    @ApplicationScope scope: CoroutineScope,
) {
    val byNameFlow: StateFlow<Map<String, CustomSubstanceWithEverything>> =
        dao.getAllWithEverythingFlow()
            .map { all -> all.associateBy { it.substance.name } }
            .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    fun getRoa(substanceName: String, route: AdministrationRoute): Roa? =
        findRoa(byNameFlow.value, substanceName, route)

    fun getInteraction(aName: String, bName: String, categoriesOf: (String) -> List<String>): InteractionType? =
        findCustomInteraction(byNameFlow.value, aName, bName, categoriesOf)
}

fun findRoa(
    profiles: Map<String, CustomSubstanceWithEverything>,
    substanceName: String,
    route: AdministrationRoute,
): Roa? = profiles[substanceName]?.roaInfos?.firstOrNull { it.route == route }?.toRoa()

/**
 * The most severe interaction either substance declares against the other.
 * A declared target matches by substance name, or by one of the other
 * substance's categories for category targets; both ignore case.
 */
fun findCustomInteraction(
    profiles: Map<String, CustomSubstanceWithEverything>,
    aName: String,
    bName: String,
    categoriesOf: (String) -> List<String>,
): InteractionType? {
    fun declaredBy(owner: String, other: String): List<InteractionType> =
        profiles[owner]?.interactions.orEmpty()
            .filter { interaction ->
                when (interaction.targetType) {
                    CustomInteractionTargetType.CATEGORY ->
                        categoriesOf(other).any { it.equals(interaction.targetName, ignoreCase = true) }
                    else -> interaction.targetName.equals(other, ignoreCase = true)
                }
            }
            .map {
                when (it.severity) {
                    CustomInteractionSeverity.DANGEROUS -> InteractionType.DANGEROUS
                    CustomInteractionSeverity.UNSAFE -> InteractionType.UNSAFE
                    CustomInteractionSeverity.UNCERTAIN -> InteractionType.UNCERTAIN
                }
            }
    return (declaredBy(aName, bName) + declaredBy(bName, aName)).maxByOrNull { it.dangerCount }
}
