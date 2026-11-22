package foo.pilz.freaklog.data.anodyne

import foo.pilz.freaklog.data.anodyne.model.AnodyneDosing
import foo.pilz.freaklog.data.anodyne.model.AnodyneSubstance
import foo.pilz.freaklog.data.anodyne.model.AnodyneUserNote
import foo.pilz.freaklog.data.substances.AdministrationRoute
import foo.pilz.freaklog.data.substances.classes.roa.RoaDose
import foo.pilz.freaklog.data.substances.classes.roa.RoaDuration
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnodyneFallbackStore @Inject constructor() {
  private val titlesByName = mutableMapOf<String, String>()
  private val totalsByTitle = mutableMapOf<String, RoaDuration>()
  private val routeDurationsByTitle =
      mutableMapOf<String, MutableMap<AdministrationRoute, RoaDuration>>()
  private val dosesByTitle = mutableMapOf<String, MutableMap<AdministrationRoute, RoaDose>>()

  @Synchronized
  fun getDose(substanceName: String, route: AdministrationRoute): RoaDose? {
    val title = titleFor(substanceName)
    return dosesByTitle[title]?.get(route)
  }

  @Synchronized
  fun getDuration(substanceName: String, route: AdministrationRoute): RoaDuration? {
    val title = titleFor(substanceName)
    return routeDurationsByTitle[title]?.get(route) ?: totalsByTitle[title]
  }

  @Synchronized
  fun getRoutesWithData(substanceName: String): List<AdministrationRoute> {
    val title = titleFor(substanceName)
    val doses = dosesByTitle[title]
    val durations = routeDurationsByTitle[title]
    return AdministrationRoute.entries.filter { route ->
      doses?.containsKey(route) == true || durations?.containsKey(route) == true
    }
  }

  @Synchronized
  fun getRoutesWithTimings(substanceName: String): List<AdministrationRoute> {
    val title = titleFor(substanceName)
    val durations = routeDurationsByTitle[title]
    return AdministrationRoute.entries.filter { route ->
      durations?.containsKey(route) == true
    }
  }

  @Synchronized
  fun record(substance: AnodyneSubstance, lookedUpAs: String = substance.title) {
    val title = substance.title.lowercase()
    for (alias in
        listOf(lookedUpAs, substance.title) + substance.abbreviations + substance.aliases) {
      titlesByName[alias.lowercase()] = title
    }
    substance.durationOfAction
        ?.let { AnodyneTiming.rangeIn(it) }
        ?.let { totalRange ->
          totalsByTitle[title] =
              RoaDuration(
                  onset = null,
                  comeup = null,
                  peak = null,
                  offset = null,
                  total = totalRange,
                  afterglow = null,
              )
        }

    val routeDoses = mutableMapOf<AdministrationRoute, RoaDose>()
    for (dosing in substance.dosing) {
      val route = dosing.route ?: continue
      if (route !in routeDoses) {
        val unit = dosing.unit ?: "mg"
        if (dosing.common != null || dosing.strong != null || dosing.heavy != null) {
          routeDoses[route] =
              RoaDose(
                  units = unit,
                  lightMin = null,
                  commonMin = dosing.common?.lower,
                  strongMin = dosing.strong?.lower,
                  heavyMin = dosing.heavy?.lower,
              )
        }
      }
    }
    dosesByTitle[title] = routeDoses
  }

  @Synchronized
  fun record(notes: List<AnodyneUserNote>) {
    val routesBySubstance =
        mutableMapOf<String, MutableMap<AdministrationRoute, MutableList<AnodyneUserNote.Route>>>()
    for (note in notes) {
      val title = titleFor(note.substanceName)
      for (r in note.routes) {
        val adminRoute = AnodyneDosing.mapRoute(r.name) ?: continue
        routesBySubstance
            .getOrPut(title) { mutableMapOf() }
            .getOrPut(adminRoute) { mutableListOf() }
            .add(r)
      }
    }

    for ((subTitle, routeMap) in routesBySubstance) {
      val target = routeDurationsByTitle.getOrPut(subTitle) { mutableMapOf() }
      for ((route, list) in routeMap) {
        AnodyneTiming.combinedDuration(list)?.let {
          target[route] = it
        }
      }
    }
  }

  @Synchronized
  fun clear() {
    titlesByName.clear()
    totalsByTitle.clear()
    routeDurationsByTitle.clear()
    dosesByTitle.clear()
  }

  private fun titleFor(name: String): String = titlesByName[name.lowercase()] ?: name.lowercase()
}
