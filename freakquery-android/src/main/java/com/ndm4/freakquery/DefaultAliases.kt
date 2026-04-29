package com.ndm4.freakquery

internal object DefaultAliases {
    private fun normalized(entries: Map<String, String>): Map<String, String> =
        entries.mapKeys { Aliases.norm(it.key) }

    val route: Map<String, String> = normalized(
        mapOf(
            "iv" to "intravenous",
            "inject" to "intravenous",
            "injected" to "intravenous",
            "intravenous" to "intravenous",
            "oral" to "oral",
            "po" to "oral",
            "swallowed" to "oral",
            "insufflated" to "intranasal",
            "snorted" to "intranasal",
            "snort" to "intranasal",
            "nasal" to "intranasal",
            "intranasal" to "intranasal",
            "smoked" to "smoked",
            "vaped" to "smoked",
            "vapourized" to "smoked",
            "vaporized" to "smoked",
            "rectal" to "intrarectal",
            "boof" to "intrarectal",
            "boofed" to "intrarectal",
            "intrarectal" to "intrarectal",
            "sublingual" to "sublingual",
            "transdermal" to "transdermal"
        )
    )

    val site: Map<String, String> = normalized(
        mapOf(
            "l nostril" to "left nostril",
            "left nostril" to "left nostril",
            "r nostril" to "right nostril",
            "right nostril" to "right nostril",
            "both nose" to "both nostrils",
            "both nostrils" to "both nostrils",
            "left hand" to "left dorsal hand",
            "right hand" to "right dorsal hand"
        )
    )

    val unit: Map<String, String> = normalized(
        mapOf(
            "μg" to "ug",
            "µg" to "ug",
            "mcg" to "ug",
            "microgram" to "ug",
            "micrograms" to "ug",
            "micro gram" to "ug",
            "micro grams" to "ug",
            "milligram" to "mg",
            "milligrams" to "mg",
            "miligram" to "mg",
            "miligrams" to "mg",
            "gram" to "g",
            "grams" to "g",
            "kilogram" to "kg",
            "kilograms" to "kg",
            "milliliter" to "ml",
            "milliliters" to "ml",
            "millilitre" to "ml",
            "millilitres" to "ml",
            "liter" to "l",
            "liters" to "l",
            "litre" to "l",
            "litres" to "l",
            "pill" to "pill",
            "pills" to "pill",
            "capsule" to "capsule",
            "capsules" to "capsule",
            "cap" to "capsule",
            "caps" to "capsule",
            "joint" to "joint",
            "joints" to "joint",
            "blunt" to "blunt",
            "blunts" to "blunt",
            "spliff" to "spliff",
            "spliffs" to "spliff",
            "bowl" to "bowl",
            "bowls" to "bowl",
            "cone" to "cone",
            "cones" to "cone",
            "puff" to "puff",
            "puffs" to "puff",
            "hit" to "hit",
            "hits" to "hit",
            "toke" to "hit",
            "tokes" to "hit",
            "drink" to "drink",
            "drinks" to "drink",
            "beer" to "beer",
            "beers" to "beer",
            "shot" to "shot",
            "shots" to "shot",
            "glass" to "glass",
            "glasses" to "glass",
            "wine glass" to "glass",
            "wine glasses" to "glass",
            "drop" to "drop",
            "drops" to "drop",
            "spray" to "spray",
            "sprays" to "spray",
            "line" to "line",
            "lines" to "line",
            "rail" to "line",
            "rails" to "line",
            "bump" to "bump",
            "bumps" to "bump",
            "point" to "point",
            "points" to "point",
            "piece" to "piece",
            "pieces" to "piece",
            "rock" to "rock",
            "rocks" to "rock",
            "crystal" to "crystal",
            "crystals" to "crystal",
            "stamp" to "stamp",
            "stamps" to "stamp",
            "blotter" to "blotter",
            "blotters" to "blotter",
            "tab" to "blotter",
            "tabs" to "blotter",
            "unit" to "unit",
            "units" to "unit",
            "dose" to "dose",
            "doses" to "dose",
            "serving" to "serving",
            "servings" to "serving",
            "u" to "unit"
        )
    )

    val substance: Map<String, String> = GeneratedSubstanceAliases.map

    val all: Map<String, Map<String, String>> = mapOf(
        "route" to route,
        "site" to site,
        "unit" to unit,
        "substance" to substance
    )
}
