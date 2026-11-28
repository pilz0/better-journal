package com.ndm4.freakquery

import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object Aliases {
  private const val NORM_CACHE_MAX = 4096
  private val normCache = ConcurrentHashMap<String, String>()

  private fun isSeparatorOrSpace(ch: Char): Boolean =
      when (ch) {
        '_',
        '-',
        '.',
        ',',
        ';',
        ':',
        '/',
        '\\',
        '|',
        '(',
        ')',
        '[',
        ']',
        '{',
        '}' -> true
        else -> ch <= ' ' || Character.isWhitespace(ch)
      }

  private fun appendCollapsed(source: CharSequence, skipNonSpacingMarks: Boolean): String {
    val sb = StringBuilder(source.length)
    var needSpace = false
    for (i in 0 until source.length) {
      val ch = source[i]
      if (skipNonSpacingMarks && Character.getType(ch) == Character.NON_SPACING_MARK.toInt()) {
        continue
      }
      if (isSeparatorOrSpace(ch)) {
        if (sb.isNotEmpty()) needSpace = true
      } else {
        if (needSpace) {
          sb.append(' ')
          needSpace = false
        }
        sb.append(ch)
      }
    }
    return sb.toString()
  }

  private fun computeNorm(raw: String): String {
    var allAscii = true
    for (i in raw.indices) {
      if (raw[i].code >= 128) {
        allAscii = false
        break
      }
    }
    if (allAscii) {
      val sb = StringBuilder(raw.length)
      var needSpace = false
      for (i in raw.indices) {
        var ch = raw[i]
        if (ch in 'A'..'Z') ch = (ch.code + 32).toChar()
        if (isSeparatorOrSpace(ch)) {
          if (sb.isNotEmpty()) needSpace = true
        } else {
          if (needSpace) {
            sb.append(' ')
            needSpace = false
          }
          sb.append(ch)
        }
      }
      return sb.toString()
    }
    val lowered = raw.trim().lowercase(Locale.ROOT)
    val nfkd = Normalizer.normalize(lowered, Normalizer.Form.NFKD)
    return appendCollapsed(nfkd, skipNonSpacingMarks = true)
  }

  fun norm(value: Any?): String {
    if (value == null) return ""
    val raw = value.toString()
    if (raw.isEmpty()) return ""
    normCache[raw]?.let {
      return it
    }
    val normalized = computeNorm(raw)
    if (normCache.size >= NORM_CACHE_MAX) normCache.clear()
    normCache.putIfAbsent(raw, normalized)
    return normalized
  }

  fun fieldKeys(config: FreakQueryConfig, key: String): List<String> {
    val wanted = norm(key)
    for ((canonical, values) in config.fieldNames) {
      val names = if (canonical in values) values else listOf(canonical) + values
      if (names.any { norm(it) == wanted }) return names
    }
    return listOf(key)
  }

  fun aliasMap(config: FreakQueryConfig, field: String): Map<String, String> =
      config.aliases[norm(field)].orEmpty()

  fun canonicalValue(config: FreakQueryConfig, field: String, value: Any?): String {
    val raw = value?.toString()?.trim().orEmpty()
    if (raw.isEmpty()) return raw
    return aliasMap(config, field)[norm(raw)] ?: raw
  }

  fun sameValue(config: FreakQueryConfig, field: String, a: Any?, b: Any?): Boolean =
      canonicalValue(config, field, a) == canonicalValue(config, field, b)

  fun displayValue(config: FreakQueryConfig, field: String, value: Any?): String =
      canonicalValue(config, field, value)

  fun rowGet(config: FreakQueryConfig, row: Row, key: String): Any? {
    val keys = fieldKeys(config, key)
    for (wanted in keys) {
      if (row.containsKey(wanted)) return row[wanted]
    }
    for (wanted in keys) {
      val nw = norm(wanted)
      for (real in row.keys) {
        if (norm(real) == nw) return row[real]
      }
    }
    return null
  }
}
