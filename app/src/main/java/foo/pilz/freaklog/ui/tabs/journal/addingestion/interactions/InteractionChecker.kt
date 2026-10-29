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

package foo.pilz.freaklog.ui.tabs.journal.addingestion.interactions

import foo.pilz.freaklog.data.substances.classes.InteractionType
import foo.pilz.freaklog.data.substances.classes.Interactions
import foo.pilz.freaklog.data.substances.classes.Substance
import foo.pilz.freaklog.data.substances.repositories.SubstanceRepository
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class InteractionChecker @Inject constructor(
    private val substanceRepo: SubstanceRepository,
    private val customProfiles: foo.pilz.freaklog.data.room.experiences.CustomSubstanceProfiles,
) {
    private val wildcardRegexCache = ConcurrentHashMap<String, Regex>()

    fun getInteractionBetween(aName: String, bName: String): Interaction? {
        if (aName == bName) {
            return null
        }
        // What the user declared for a custom substance wins over built-in data.
        customProfiles.getInteraction(aName, bName) { substanceRepo.getSubstance(it)?.categories.orEmpty() }
            ?.let { return Interaction(aName = aName, bName = bName, interactionType = it) }

        val substanceA = substanceRepo.getSubstance(aName)
        val substanceB = substanceRepo.getSubstance(bName)

        val interactionFromAToB = getInteractionFromAToB(substanceA, substanceB, bName)
        val interactionFromBToA = getInteractionFromAToB(substanceB, substanceA, aName)
        if (interactionFromAToB != null && interactionFromBToA != null) {
            val isAtoB = interactionFromAToB.dangerCount >= interactionFromBToA.dangerCount
            val interactionType = if (isAtoB) interactionFromAToB else interactionFromBToA
            return Interaction(
                aName = aName,
                bName = bName,
                interactionType = interactionType,
            )
        } else if (interactionFromAToB != null) {
            return Interaction(
                aName = aName,
                bName = bName,
                interactionType = interactionFromAToB,
            )
        } else if (interactionFromBToA != null) {
            return Interaction(
                aName = aName,
                bName = bName,
                interactionType = interactionFromBToA,
            )
        } else {
            return null
        }
    }

    private val cleanedCache = ConcurrentHashMap<Interactions, CleanedInteractions>()

    private fun getInteractionFromAToB(
        fromSubstance: Substance?,
        toSubstance: Substance?,
        toName: String,
    ): InteractionType? {
        val interactions = fromSubstance?.interactions ?: return null
        val cleaned = cleanedCache.getOrPut(interactions) { cleanInteractions(interactions) }

        val directInteraction = getDirectInteraction(cleaned = cleaned, substanceName = toName)
        if (directInteraction != null) {
            return directInteraction
        }
        val wildcardInteraction = getWildcardInteraction(cleaned = cleaned, substanceName = toName)
        if (wildcardInteraction != null) {
            return wildcardInteraction
        }
        if (toSubstance != null && toSubstance.categories.isNotEmpty()) {
            val classInteraction = getClassInteraction(cleaned = cleaned, categories = toSubstance.categories)
            if (classInteraction != null) {
                return classInteraction
            }
        }
        return null
    }

    private fun getDirectInteraction(cleaned: CleanedInteractions, substanceName: String): InteractionType? {
        return if (cleaned.dangerous.directSet.contains(substanceName)) {
            InteractionType.DANGEROUS
        } else if (cleaned.unsafe.directSet.contains(substanceName)) {
            InteractionType.UNSAFE
        } else if (cleaned.uncertain.directSet.contains(substanceName)) {
            InteractionType.UNCERTAIN
        } else {
            null
        }
    }

    private fun getWildcardInteraction(cleaned: CleanedInteractions, substanceName: String): InteractionType? {
        return if (isWildcardMatch(cleaned.dangerous.wildcards, substanceName)) {
            InteractionType.DANGEROUS
        } else if (isWildcardMatch(cleaned.unsafe.wildcards, substanceName)) {
            InteractionType.UNSAFE
        } else if (isWildcardMatch(cleaned.uncertain.wildcards, substanceName)) {
            InteractionType.UNCERTAIN
        } else {
            null
        }
    }

    private fun getClassInteraction(cleaned: CleanedInteractions, categories: List<String>): InteractionType? {
        return if (isClassMatch(cleaned.dangerous.all, categories)) {
            InteractionType.DANGEROUS
        } else if (isClassMatch(cleaned.unsafe.all, categories)) {
            InteractionType.UNSAFE
        } else if (isClassMatch(cleaned.uncertain.all, categories)) {
            InteractionType.UNCERTAIN
        } else {
            null
        }
    }

    private fun isClassMatch(interactions: List<String>, categories: List<String>): Boolean {
        if (interactions.isEmpty() || categories.isEmpty()) return false
        return categories.any { categoryName ->
            interactions.any { interactionName ->
                interactionName.contains(categoryName, ignoreCase = true)
            }
        }
    }

    private fun isWildcardMatch(wildcards: List<String>, substanceName: String): Boolean {
        if (wildcards.isEmpty()) return false
        return wildcards.any { interaction ->
            val regex = wildcardRegexCache.getOrPut(interaction) {
                buildWildcardRegex(interaction)
            }
            regex.matches(substanceName)
        }
    }

    private fun buildWildcardRegex(interaction: String): Regex {
        val parts = interaction.split(Regex("(?i)x"))
        val pattern = parts.joinToString(separator = "[\\S]{2}") { Regex.escape(it) }
        return Regex(pattern = pattern, option = RegexOption.IGNORE_CASE)
    }

    private fun cleanCategory(interactions: List<String>): CleanedCategory {
        val extended = extendAndCleanInteractions(interactions)
        return CleanedCategory(
            directSet = extended.toSet(),
            wildcards = extended.filter { it.contains('x', ignoreCase = true) },
            all = extended,
        )
    }

    private fun cleanInteractions(interactions: Interactions): CleanedInteractions {
        return CleanedInteractions(
            dangerous = cleanCategory(interactions.dangerous),
            unsafe = cleanCategory(interactions.unsafe),
            uncertain = cleanCategory(interactions.uncertain),
        )
    }

    private class CleanedCategory(
        val directSet: Set<String>,
        val wildcards: List<String>,
        val all: List<String>,
    )

    private class CleanedInteractions(
        val dangerous: CleanedCategory,
        val unsafe: CleanedCategory,
        val uncertain: CleanedCategory,
    )

    private fun extendAndCleanInteractions(interactions: List<String>): List<String> {
        return interactions.flatMap { name ->
            when (name) {
                "Substituted amphetamines" -> {
                    return@flatMap substitutedAmphetamines
                }
                "Serotonin releasers" -> {
                    return@flatMap serotoninReleasers
                }
                "Tricyclic antidepressants" -> {
                    return@flatMap emptyList() // remove because we don't want to match "depressant" with it and there is no substance that belongs to that class
                }
                else -> {
                    return@flatMap listOf(name)
                }
            }
        }.distinct()
    }

    private val serotoninReleasers = listOf(
        "MDMA",
        "MDA",
        "Mephedrone"
    )

    private val substitutedAmphetamines = listOf(
        "Amphetamine",
        "Methamphetamine",
        "Ethylamphetamine",
        "Propylamphetamine",
        "Isopropylamphetamine",
        "Bromo-DragonFLY",
        "Lisdexamfetamine",
        "Clobenzorex",
        "Dimethylamphetamine",
        "Selegiline",
        "Benzphetamine",
        "Ortetamine",
        "3-Methylamphetamine",
        "4-Methylamphetamine",
        "4-MMA",
        "Xylopropamine",
        "ß-methylamphetamine",
        "3-phenylmethamphetamine",
        "2-FA",
        "2-FMA",
        "2-FEA",
        "3-FA",
        "3-FMA",
        "3-FEA",
        "Fenfluramine",
        "Norfenfluramine",
        "4-FA",
        "4-FMA",
        "4-CA",
        "4-BA",
        "4-IA",
        "DCA",
        "4-HA",
        "4-HMA",
        "3,4-DHA",
        "OMA",
        "3-MA",
        "MMMA",
        "MMA",
        "PMA",
        "PMMA",
        "PMEA",
        "4-ETA",
        "TMA-2",
        "TMA-6",
        "4-MTA",
        "5-API",
        "Cathine",
        "Phenmetrazine",
        "3-FPM",
        "Prolintane"
    )

}

data class Interaction(
    val aName: String,
    val bName: String,
    val interactionType: InteractionType,
)