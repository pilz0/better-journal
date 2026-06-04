package foo.pilz.freaklog.provider

object ProviderQueryParams {
    const val UNLIMITED = -1

    fun parseSince(raw: String?): Int = raw?.toIntOrNull()?.takeIf { it >= 0 } ?: 0

    fun parseSinceLong(raw: String?): Long = raw?.toLongOrNull()?.takeIf { it >= 0 } ?: 0L

    fun parseLimit(raw: String?): Int {
        val n = raw?.toIntOrNull() ?: return UNLIMITED
        return if (n > 0) n else UNLIMITED
    }
}
