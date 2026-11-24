package foo.pilz.freaklog.data.anodyne.model

data class AnodyneUserNote(
    val user: String,
    val substanceName: String,
    val routes: List<Route> = emptyList(),
    val classes: List<String> = emptyList(),
    val forms: List<String> = emptyList(),
    val salts: List<String> = emptyList(),
    val sources: List<String> = emptyList(),
    val indications: List<String> = emptyList(),
    val effects: List<String> = emptyList(),
    val effectGroups: List<EffectGroup> = emptyList(),
    val adverse: List<String> = emptyList(),
    val afterEffects: List<String> = emptyList(),
    val tolerance: List<String> = emptyList(),
    val odor: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
) {
  data class Route(
      val name: String,
      val dosage: String? = null,
      val onset: String? = null,
      val comeup: String? = null,
      val duration: String? = null,
  )

  data class EffectGroup(
      val heading: String,
      val effects: List<String> = emptyList(),
  )

  val hasDetails: Boolean
    get() =
        routes.isNotEmpty() ||
            classes.isNotEmpty() ||
            forms.isNotEmpty() ||
            salts.isNotEmpty() ||
            sources.isNotEmpty() ||
            indications.isNotEmpty() ||
            effects.isNotEmpty() ||
            effectGroups.isNotEmpty() ||
            adverse.isNotEmpty() ||
            afterEffects.isNotEmpty() ||
            tolerance.isNotEmpty() ||
            odor.isNotEmpty() ||
            notes.isNotEmpty()
}
