package foo.pilz.freaklog.data.anodyne.model

import foo.pilz.freaklog.data.substances.AdministrationRoute

data class AnodyneTier(
    val lower: Double,
    val upper: Double,
    val unit: String,
    val entries: Int,
)

data class AnodyneDosing(
    val method: String,
    val light: AnodyneTier? = null,
    val common: AnodyneTier? = null,
    val strong: AnodyneTier? = null,
    val heavy: AnodyneTier? = null,
    val extreme: AnodyneTier? = null,
) {
  val route: AdministrationRoute? = mapRoute(method)

  val unit: String? =
      listOfNotNull(light, common, strong, heavy, extreme)
          .firstOrNull { it.unit.isNotBlank() }
          ?.unit

  val totalEntries: Int = listOfNotNull(light, common, strong, heavy, extreme).sumOf { it.entries }

  companion object {
    fun mapRoute(method: String): AdministrationRoute? {
      val m = method.lowercase()
      return when {
        m.contains("sublingual") -> AdministrationRoute.SUBLINGUAL
        m.contains("buccal") -> AdministrationRoute.BUCCAL
        m.contains("oral") -> AdministrationRoute.ORAL
        m.contains("insufflat") || m.contains("intranasal") -> AdministrationRoute.INSUFFLATED
        m.contains("rectal") -> AdministrationRoute.RECTAL
        m.contains("transdermal") -> AdministrationRoute.TRANSDERMAL
        m.contains("subcutaneous") -> AdministrationRoute.SUBCUTANEOUS
        m.contains("intramuscular") -> AdministrationRoute.INTRAMUSCULAR
        m.contains("intravenous") -> AdministrationRoute.INTRAVENOUS
        m.contains("smok") -> AdministrationRoute.SMOKED
        m.contains("vapori") || m.contains("inhal") -> AdministrationRoute.INHALED
        else -> null
      }
    }
  }
}

data class EffectGroup(val name: String, val effects: List<String>)

data class Schedule(val country: String, val schedule: String)

data class Reagent(val name: String, val colors: List<String>)

data class AnodyneLink(val title: String, val url: String)

data class AnodyneSubstance(
    val title: String,
    val abbreviations: List<String> = emptyList(),
    val aliases: List<String> = emptyList(),
    val slang: List<String> = emptyList(),
    val classes: List<String> = emptyList(),
    val dosing: List<AnodyneDosing> = emptyList(),
    val durationOfAction: String? = null,
    val halfLife: String? = null,
    val interactions: String? = null,
    val mechanismOfAction: String? = null,
    val metabolism: String? = null,
    val healthEffects: String? = null,
    val drugWarnings: String? = null,
    val reportedFatalDose: String? = null,
    val treatment: String? = null,
    val effectGroups: List<EffectGroup> = emptyList(),
    val scheduling: List<Schedule> = emptyList(),
    val reagents: List<Reagent> = emptyList(),
    val erowidReports: List<AnodyneLink> = emptyList(),
    val redditReports: List<AnodyneLink> = emptyList(),
    val references: List<AnodyneLink> = emptyList(),
    val structureSvg: String? = null,
    val formula: String? = null,
    val molarMass: String? = null,
) {
  val commonName: String
    get() {
      if (title.length > MAX_TITLE_LENGTH_FOR_ABBREVIATION) {
        val short = abbreviations.firstOrNull { it.length <= MAX_TITLE_LENGTH_FOR_ABBREVIATION }
        if (short != null) return short
      }
      return title
    }

  companion object {
    private const val MAX_TITLE_LENGTH_FOR_ABBREVIATION = 24
  }
}
