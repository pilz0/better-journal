package foo.pilz.freaklog.data.substances.classes.roa

/** Puts doses logged in µg, mg, g, ml, l, ... on one scale so stats can add them up. */
internal object DoseUnits {
  // Largest first, so displayUnit can pick the first one the amount reaches.
  private val massInMg = linkedMapOf("kg" to 1e6, "g" to 1e3, "mg" to 1.0, "µg" to 1e-3)
  private val volumeInMl = linkedMapOf("l" to 1e3, "ml" to 1.0, "µl" to 1e-3)

  // Accepted when logged, but totals are never shown in them.
  private val extraVolumeInMl =
      mapOf(
          "dl" to 100.0,
          "deciliter" to 100.0,
          "deciliters" to 100.0,
          "decilitre" to 100.0,
          "decilitres" to 100.0,
          "cl" to 10.0,
          "centiliter" to 10.0,
          "centiliters" to 10.0,
          "centilitre" to 10.0,
          "centilitres" to 10.0,
          "ul" to 1e-3,
          "mcl" to 1e-3,
          "microliter" to 1e-3,
          "microliters" to 1e-3,
          "microlitre" to 1e-3,
          "microlitres" to 1e-3,
      )

  /**
   * The base unit of [unit]'s family and the factor that converts [unit] into it. Units without a
   * known scale (pills, hits, ...) are their own base. Null for a missing unit.
   */
  fun toBase(unit: String?): Pair<String, Double>? {
    if (unit.isNullOrBlank()) return null
    val normalized = RoaDose.normalizeUnitString(unit)
    massInMg[normalized]?.let {
      return "mg" to it
    }
    (volumeInMl[normalized] ?: extraVolumeInMl[normalized])?.let {
      return "ml" to it
    }
    return normalized to 1.0
  }

  /**
   * The unit to show [amount] (given in [baseUnit]) in and the factor to divide it by: [preferred]
   * when it belongs to the same family, otherwise the largest unit the amount reaches 1 in.
   */
  fun displayUnit(
      baseUnit: String,
      amount: Double,
      preferred: String? = null,
  ): Pair<String, Double> {
    val preferredFactor = toBase(preferred)?.takeIf { it.first == baseUnit }?.second
    if (preferred != null && preferredFactor != null) return preferred to preferredFactor
    val scale =
        when (baseUnit) {
          "mg" -> massInMg
          "ml" -> volumeInMl
          else -> return baseUnit to 1.0
        }
    if (amount <= 0.0) return baseUnit to 1.0
    return (scale.entries.firstOrNull { amount >= it.value } ?: scale.entries.last()).toPair()
  }
}
