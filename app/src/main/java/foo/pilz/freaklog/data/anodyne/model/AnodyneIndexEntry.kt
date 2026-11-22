package foo.pilz.freaklog.data.anodyne.model

data class AnodyneIndexEntry(
    val title: String,
    val abbreviations: List<String> = emptyList(),
    val classes: List<String> = emptyList(),
) {
  fun matches(query: String): Boolean {
    val q = query.trim()
    return title.equals(q, ignoreCase = true) ||
        abbreviations.any { it.equals(q, ignoreCase = true) }
  }
}
