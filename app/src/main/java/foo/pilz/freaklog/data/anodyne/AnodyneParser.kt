package foo.pilz.freaklog.data.anodyne

import android.util.Base64
import foo.pilz.freaklog.data.anodyne.model.AnodyneDosing
import foo.pilz.freaklog.data.anodyne.model.AnodyneIndexEntry
import foo.pilz.freaklog.data.anodyne.model.AnodyneLink
import foo.pilz.freaklog.data.anodyne.model.AnodyneSubstance
import foo.pilz.freaklog.data.anodyne.model.AnodyneTier
import foo.pilz.freaklog.data.anodyne.model.AnodyneUserNote
import foo.pilz.freaklog.data.anodyne.model.EffectGroup
import foo.pilz.freaklog.data.anodyne.model.Reagent
import foo.pilz.freaklog.data.anodyne.model.Schedule
import org.json.JSONArray
import org.json.JSONObject

object AnodyneParser {

  fun parseSubstance(json: JSONObject): AnodyneSubstance? {
    if (json.optBoolean("NotFound", false)) return null
    val title = json.optString("Title").takeIf { it.isNotBlank() } ?: return null
    val cleanedTitle = AnodyneTextCleaner.clean(title)

    val abbreviations = stringsOf(json.opt("Abbreviation"))
    val aliases = stringsOf(json.opt("Aliases")).filter { !it.contains(", ") }
    val slang = stringsOf(json.opt("Slang"))
    val classes = stringsOf(json.opt("Classes"))

    val dosingList = parseDosingList(json.optJSONArray("Dosing Info"))
    val durationOfAction = textOf(json.opt("DurationOfAction"))
    val halfLife = textOf(json.opt("Biological Half-Life"))
    val interactions = textOf(json.opt("Interactions"))
    val mechanism = textOf(json.opt("Mechanism of Action"))
    val metabolism = textOf(json.opt("Metabolism/Metabolites"))
    val healthEffects = textOf(json.opt("Health Effects"))
    val drugWarnings = textOf(json.opt("Drug Warnings"))
    val fatalDose = textOf(json.opt("Reported Fatal Dose"))
    val treatment = textOf(json.opt("Treatment"))

    val effectGroups = parseSubjectiveEffects(json.optJSONObject("Subjective Effects"))
    val scheduling = parseScheduling(json.optJSONArray("Scheduling"))
    val reagents = parseReagents(json.optJSONArray("Reagents"))
    val erowidReports = parseErowidReports(json.optJSONArray("Erowid Experience Reports"))
    val redditReports = parseRedditReports(json.optJSONArray("Reddit Experience Reports"))
    val references = parseReferences(json.optJSONArray("References"))

    val structureSvg = parseStructureSvg(json)
    val formula =
        json
            .optString("MolecularFormula")
            .takeIf { it.isNotBlank() }
            ?.let { AnodyneTextCleaner.subscript(it) }
    val molarMass = textOf(json.opt("MolecularWeight"))

    return AnodyneSubstance(
        title = cleanedTitle,
        abbreviations = abbreviations,
        aliases = aliases,
        slang = slang,
        classes = classes,
        dosing = dosingList,
        durationOfAction = durationOfAction,
        halfLife = halfLife,
        interactions = interactions,
        mechanismOfAction = mechanism,
        metabolism = metabolism,
        healthEffects = healthEffects,
        drugWarnings = drugWarnings,
        reportedFatalDose = fatalDose,
        treatment = treatment,
        effectGroups = effectGroups,
        scheduling = scheduling,
        reagents = reagents,
        erowidReports = erowidReports,
        redditReports = redditReports,
        references = references,
        structureSvg = structureSvg,
        formula = formula,
        molarMass = molarMass,
    )
  }

  private fun parseDosingList(array: JSONArray?): List<AnodyneDosing> {
    if (array == null) return emptyList()
    return (0 until array.length()).mapNotNull { i ->
      array.optJSONObject(i)?.let { parseDosing(it) }
    }
  }

  private fun parseSubjectiveEffects(subjObj: JSONObject?): List<EffectGroup> {
    if (subjObj == null) return emptyList()
    return subjObj.keys().asSequence().toList().sorted().mapNotNull { key ->
      val effects = stringsOf(subjObj.opt(key))
      if (effects.isNotEmpty()) EffectGroup(key, effects) else null
    }
  }

  private fun parseScheduling(array: JSONArray?): List<Schedule> {
    if (array == null) return emptyList()
    return (0 until array.length()).mapNotNull { i ->
      val sObj = array.optJSONObject(i) ?: return@mapNotNull null
      val country = sObj.optString("gov").takeIf { it.isNotBlank() } ?: return@mapNotNull null
      val rawSched = sObj.optString("schedule").takeIf { it.isNotBlank() } ?: return@mapNotNull null
      val label =
          AnodyneTextCleaner.clean(rawSched.split("|").lastOrNull() ?: rawSched)
              .replace(". substance", ".")
              .trim()
      Schedule(country, label)
    }
  }

  private fun parseReagents(array: JSONArray?): List<Reagent> {
    if (array == null) return emptyList()
    return (0 until array.length()).mapNotNull { i ->
      val rObj = array.optJSONObject(i) ?: return@mapNotNull null
      val name = rObj.optString("Name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
      Reagent(name, stringsOf(rObj.opt("Colors")))
    }
  }

  private fun parseErowidReports(array: JSONArray?): List<AnodyneLink> {
    if (array == null) return emptyList()
    return (0 until array.length()).mapNotNull { i ->
      val eObj = array.optJSONObject(i) ?: return@mapNotNull null
      val id = eObj.optInt("Id", -1)
      val repTitle = eObj.optString("Title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
      if (id > 0) {
        AnodyneLink(
            title = AnodyneTextCleaner.clean(repTitle),
            url = "https://www.erowid.org/experiences/exp.php?ID=$id",
        )
      } else {
        null
      }
    }
  }

  private fun parseRedditReports(array: JSONArray?): List<AnodyneLink> {
    if (array == null) return emptyList()
    return (0 until array.length()).mapNotNull { i ->
      val rObj = array.optJSONObject(i) ?: return@mapNotNull null
      val repTitle = rObj.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
      val url = rObj.optString("url").takeIf { it.isNotBlank() } ?: return@mapNotNull null
      AnodyneLink(AnodyneTextCleaner.clean(repTitle), url)
    }
  }

  private fun parseReferences(array: JSONArray?): List<AnodyneLink> {
    if (array == null) return emptyList()
    return (0 until array.length()).mapNotNull { i ->
      val refObj = array.optJSONObject(i) ?: return@mapNotNull null
      val refName = refObj.optString("Name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
      val urlsArr = refObj.optJSONArray("Urls")
      val link = urlsArr?.optJSONObject(0)?.optString("Link")?.takeIf { it.isNotBlank() }
      if (link != null) AnodyneLink(refName, link) else null
    }
  }

  private fun parseDosing(json: JSONObject): AnodyneDosing? {
    val method = json.optString("Method").takeIf { it.isNotBlank() } ?: return null
    val tiersObj = json.optJSONObject("Tiers") ?: return null

    fun parseTier(name: String): AnodyneTier? {
      val tObj = tiersObj.optJSONObject(name) ?: return null
      val lower = tObj.optDouble("Lower")
      val upper = tObj.optDouble("Upper")
      val unit = tObj.optString("Unit")
      val entries = tObj.optInt("Entries", 0)
      if (lower.isNaN() || upper.isNaN()) return null
      return AnodyneTier(lower = lower, upper = upper, unit = unit, entries = entries)
    }

    val dosing =
        AnodyneDosing(
            method = method,
            light = parseTier("Light"),
            common = parseTier("Common"),
            strong = parseTier("Strong"),
            heavy = parseTier("Heavy"),
            extreme = parseTier("Extreme"),
        )
    return if (dosing.totalEntries > 0) dosing else null
  }

  private fun parseStructureSvg(json: JSONObject): String? {
    val directSvg = json.optString("Structure")
    if (directSvg.contains("<svg")) return directSvg

    val b64 = json.optString("StructureBase64").takeIf { it.isNotBlank() } ?: return null
    return try {
      val decoded = String(Base64.decode(b64, Base64.DEFAULT), Charsets.UTF_8)
      if (decoded.contains("<svg")) decoded else null
    } catch (_: IllegalArgumentException) {
      null
    }
  }

  fun parseIndex(json: JSONObject): List<AnodyneIndexEntry> {
    val array = json.optJSONArray("Entries") ?: return emptyList()
    return (0 until array.length()).mapNotNull { i ->
      val entryObj = array.optJSONObject(i) ?: return@mapNotNull null
      if (entryObj.optBoolean("NoBuild", false)) return@mapNotNull null
      val title = entryObj.optString("Title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
      val abbreviations = stringsOf(entryObj.opt("Abr"))
      val classes = stringsOf(entryObj.opt("Classes"))
      AnodyneIndexEntry(title, abbreviations, classes)
    }
  }

  fun parseUserNotes(userJson: JSONObject): List<AnodyneUserNote> {
    val user = userJson.optString("Title").takeIf { it.isNotBlank() } ?: return emptyList()
    // Most profiles use "TriedSubstances"; some (e.g. the wiki owner's) use "Substances".
    val triedArray =
        userJson.optJSONArray("TriedSubstances")
            ?: userJson.optJSONArray("Substances")
            ?: return emptyList()

    return (0 until triedArray.length()).mapNotNull { i ->
      val entry = triedArray.optJSONObject(i) ?: return@mapNotNull null
      val name = entry.optString("Name").takeIf { it.isNotBlank() } ?: return@mapNotNull null

      fun field(vararg keys: String) = keys.flatMap { stringsOf(entry.opt(it)) }

      val routes =
          parseNoteRoutes(entry.optJSONArray("Routes")) +
              parseNoteRoutes(entry.optJSONArray("Dosage"))
      val doseEffects = entry.optJSONArray("DEffects")
      val effectGroups =
          (0 until (doseEffects?.length() ?: 0)).mapNotNull { j ->
            val group = doseEffects!!.optJSONObject(j) ?: return@mapNotNull null
            stringsOf(group.opt("Effects"))
                .takeIf { it.isNotEmpty() }
                ?.let {
                  AnodyneUserNote.EffectGroup(textOf(group.opt("Dosage")).orEmpty(), it)
                }
          }

      val note =
          AnodyneUserNote(
              user = user,
              substanceName = AnodyneTextCleaner.clean(name),
              routes = routes,
              classes = field("Classes"),
              forms = field("Forms"),
              salts = field("Salts"),
              sources = field("Sources", "Source"),
              indications = field("Indication", "Indications"),
              effects = field("Effects"),
              effectGroups = effectGroups,
              adverse = field("Adverse"),
              afterEffects = field("After", "After Effects"),
              tolerance = field("Tolerance"),
              odor = field("Odor"),
              notes = field("Notes", "Note"),
          )
      if (note.hasDetails) note else null
    }
  }

  private fun parseNoteRoutes(array: JSONArray?): List<AnodyneUserNote.Route> =
      (0 until (array?.length() ?: 0)).mapNotNull { j ->
        val r = array!!.optJSONObject(j) ?: return@mapNotNull null
        val name = textOf(r.opt("Name")) ?: return@mapNotNull null
        AnodyneUserNote.Route(
            name = name,
            dosage = textOf(r.opt("Dosage")) ?: textOf(r.opt("Dosages")) ?: textOf(r.opt("Dose")),
            onset = textOf(r.opt("Onset")),
            comeup = textOf(r.opt("Comeup")),
            duration = textOf(r.opt("Duration")) ?: textOf(r.opt("Active")),
        )
      }

  // Keeps the wiki markup, so the note screen can link it (AnodyneTextCleaner.spans); a value that
  // only holds markup without text, e.g. "{br}", is dropped.
  private fun stringsOf(value: Any?): List<String> =
      when (value) {
        is String ->
            listOfNotNull(value.trim().takeIf { AnodyneTextCleaner.clean(it).isNotEmpty() })
        is JSONArray ->
            (0 until value.length()).mapNotNull { i ->
              value.optString(i).trim().takeIf { AnodyneTextCleaner.clean(it).isNotEmpty() }
            }
        else -> emptyList()
      }

  private fun textOf(value: Any?): String? =
      (value as? String)?.takeIf { it.isNotBlank() }?.let { AnodyneTextCleaner.clean(it) }
}
