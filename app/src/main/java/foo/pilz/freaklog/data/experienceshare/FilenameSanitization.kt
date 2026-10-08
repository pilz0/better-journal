package foo.pilz.freaklog.data.experienceshare

fun sanitizeForShareFile(input: String): String {
    val cleaned = input.replace(Regex("[^A-Za-z0-9._-]"), "_").take(64)
    val isOnlyFiller = cleaned.isEmpty() || cleaned.all { it == '_' || it == '.' || it == '-' }
    return if (isOnlyFiller) "experience" else cleaned
}
