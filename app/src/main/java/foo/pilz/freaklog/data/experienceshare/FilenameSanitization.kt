package foo.pilz.freaklog.data.experienceshare

private val SANITIZE_SHARE_FILE_REGEX = Regex("[^A-Za-z0-9._-]")

fun sanitizeForShareFile(input: String): String {
    val cleaned = input.replace(SANITIZE_SHARE_FILE_REGEX, "_").take(64)
    val isOnlyFiller = cleaned.isEmpty() || cleaned.all { it == '_' || it == '.' || it == '-' }
    return if (isOnlyFiller) "experience" else cleaned
}
