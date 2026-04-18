/*
 * Copyright (c) 2022. Isaak Hanimann.
 * This file is part of PsychonautWiki Journal.
 *
 * PsychonautWiki Journal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * PsychonautWiki Journal is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PsychonautWiki Journal.  If not, see https://www.gnu.org/licenses/gpl-3.0.en.html.
 */

package foo.pilz.freaklog.data.room.experiences.entities

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.KSerializer

/**
 * Represents a color that can be associated with a substance, ingestion or timed note.
 *
 * Has a fixed list of named presets (see [entries]), plus a [Custom] variant that wraps an
 * arbitrary 32-bit ARGB color value chosen by the user via the color picker.
 *
 * Backwards compatibility: presets serialize to / from their [name] (e.g. "RED"). [Custom]
 * values serialize as `CUSTOM_AARRGGBB` (8 hex digits, upper-case). This format is used both
 * for Room storage (via [AdaptiveColorConverter]) and for kotlinx.serialization JSON
 * export/import (via [AdaptiveColorSerializer]).
 */
@Serializable(with = AdaptiveColorSerializer::class)
sealed interface AdaptiveColor {

    /** Stable identifier used for storage and serialization. */
    val name: String

    /** Whether this color is in the short list of preferred presets shown first. */
    val isPreferred: Boolean

    fun getComposeColor(isDarkTheme: Boolean): Color

    data object RED : AdaptiveColor {
        override val name = "RED"
        override val isPreferred = true
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(red = 255, green = 69, blue = 58)
            } else {
                Color(red = 255, green = 59, blue = 48)
            }
        }
    }

    data object ORANGE : AdaptiveColor {
        override val name = "ORANGE"
        override val isPreferred = true
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(red = 255, green = 159, blue = 10)
            } else {
                Color(red = 255, green = 149, blue = 0)
            }
        }
    }

    data object YELLOW : AdaptiveColor {
        override val name = "YELLOW"
        override val isPreferred = true
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(red = 255, green = 214, blue = 10)
            } else {
                Color(red = 255, green = 204, blue = 0)
            }
        }
    }

    data object GREEN : AdaptiveColor {
        override val name = "GREEN"
        override val isPreferred = true
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(red = 48, green = 209, blue = 88)
            } else {
                Color(red = 52, green = 199, blue = 89)
            }
        }
    }

    data object MINT : AdaptiveColor {
        override val name = "MINT"
        override val isPreferred = true
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(red = 102, green = 212, blue = 207)
            } else {
                Color(red = 0, green = 199, blue = 190)
            }
        }
    }

    data object TEAL : AdaptiveColor {
        override val name = "TEAL"
        override val isPreferred = true
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(red = 64, green = 200, blue = 224)
            } else {
                Color(red = 48, green = 176, blue = 199)
            }
        }
    }

    data object CYAN : AdaptiveColor {
        override val name = "CYAN"
        override val isPreferred = true
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(red = 100, green = 210, blue = 255)
            } else {
                Color(red = 50, green = 173, blue = 230)
            }
        }
    }

    data object BLUE : AdaptiveColor {
        override val name = "BLUE"
        override val isPreferred = true
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(red = 10, green = 132, blue = 255)
            } else {
                Color(red = 0, green = 122, blue = 255)
            }
        }
    }

    data object INDIGO : AdaptiveColor {
        override val name = "INDIGO"
        override val isPreferred = true
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(red = 94, green = 92, blue = 230)
            } else {
                Color(red = 88, green = 86, blue = 214)
            }
        }
    }

    data object PURPLE : AdaptiveColor {
        override val name = "PURPLE"
        override val isPreferred = true
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(red = 191, green = 90, blue = 242)
            } else {
                Color(red = 175, green = 82, blue = 222)
            }
        }
    }

    data object PINK : AdaptiveColor {
        override val name = "PINK"
        override val isPreferred = true
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(red = 255, green = 55, blue = 95)
            } else {
                Color(red = 255, green = 45, blue = 85)
            }
        }
    }

    data object BROWN : AdaptiveColor {
        override val name = "BROWN"
        override val isPreferred = true
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(red = 172, green = 142, blue = 104)
            } else {
                Color(red = 162, green = 132, blue = 94)
            }
        }
    }

    data object FIRE_ENGINE_RED : AdaptiveColor {
        override val name = "FIRE_ENGINE_RED"
        override val isPreferred = false
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(red = 237, green = 43, blue = 42)
            } else {
                Color(red = 237, green = 14, blue = 6)
            }
        }
    }

    data object CORAL : AdaptiveColor {
        override val name = "CORAL"
        override val isPreferred = false
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(red = 255, green = 131, blue = 121)
            } else {
                Color(red = 180, green = 92, blue = 85)
            }
        }
    }

    data object TOMATO : AdaptiveColor {
        override val name = "TOMATO"
        override val isPreferred = false
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(red = 255, green = 99, blue = 71)
            } else {
                Color(red = 180, green = 69, blue = 50)
            }
        }
    }

    data object CINNABAR : AdaptiveColor {
        override val name = "CINNABAR"
        override val isPreferred = false
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return Color(red = 227, green = 36, blue = 0)
        }
    }

    data object RUST : AdaptiveColor {
        override val name = "RUST"
        override val isPreferred = false
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return Color(red = 199, green = 81, blue = 58)
        }
    }

    data object ORANGE_RED : AdaptiveColor {
        override val name = "ORANGE_RED"
        override val isPreferred = false
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(red = 255, green = 69, blue = 0)
            } else {
                Color(red = 205, green = 55, blue = 0)
            }
        }
    }

    data object AUBURN : AdaptiveColor {
        override val name = "AUBURN"
        override val isPreferred = false
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(red = 217, green = 80, blue = 0)
            } else {
                Color(red = 173, green = 62, blue = 0)
            }
        }
    }

    data object SADDLE_BROWN : AdaptiveColor {
        override val name = "SADDLE_BROWN"
        override val isPreferred = false
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(red = 191, green = 95, blue = 25)
            } else {
                Color(red = 139, green = 69, blue = 19)
            }
        }
    }

    data object DARK_ORANGE : AdaptiveColor {
        override val name = "DARK_ORANGE"
        override val isPreferred = false
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(red = 255, green = 140, blue = 0)
            } else {
                Color(red = 155, green = 84, blue = 0)
            }
        }
    }

    data object DARK_GOLD : AdaptiveColor {
        override val name = "DARK_GOLD"
        override val isPreferred = false
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return Color(red = 169, green = 104, blue = 0)
        }
    }

    data object KHAKI : AdaptiveColor {
        override val name = "KHAKI"
        override val isPreferred = false
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(red = 203, green = 183, blue = 137)
            } else {
                Color(red = 128, green = 114, blue = 86)
            }
        }
    }

    data object BRONZE : AdaptiveColor {
        override val name = "BRONZE"
        override val isPreferred = false
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(red = 167, green = 123, blue = 0)
            } else {
                Color(red = 120, green = 87, blue = 0)
            }
        }
    }

    data object GOLD : AdaptiveColor {
        override val name = "GOLD"
        override val isPreferred = false
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(red = 255, green = 215, blue = 0)
            } else {
                Color(red = 130, green = 109, blue = 0)
            }
        }
    }

    data object OLIVE : AdaptiveColor {
        override val name = "OLIVE"
        override val isPreferred = false
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(red = 141, green = 134, blue = 0)
            } else {
                Color(red = 102, green = 97, blue = 0)
            }
        }
    }

    data object OLIVE_DRAB : AdaptiveColor {
        override val name = "OLIVE_DRAB"
        override val isPreferred = false
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(red = 154, green = 166, blue = 14)
            } else {
                Color(red = 111, green = 118, blue = 8)
            }
        }
    }

    data object DARK_OLIVE_GREEN : AdaptiveColor {
        override val name = "DARK_OLIVE_GREEN"
        override val isPreferred = false
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(red = 105, green = 133, blue = 58)
            } else {
                Color(red = 85, green = 107, blue = 47)
            }
        }
    }

    data object MOSS_GREEN : AdaptiveColor {
        override val name = "MOSS_GREEN"
        override val isPreferred = false
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(red = 102, green = 156, blue = 53)
            } else {
                Color(red = 79, green = 122, blue = 40)
            }
        }
    }

    data object LIME_GREEN : AdaptiveColor {
        override val name = "LIME_GREEN"
        override val isPreferred = false
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(red = 0, green = 255, blue = 0)
            } else {
                Color(red = 0, green = 130, blue = 0)
            }
        }
    }

    data object LIME : AdaptiveColor {
        override val name = "LIME"
        override val isPreferred = false
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(red = 50, green = 205, blue = 50)
            } else {
                Color(red = 32, green = 130, blue = 32)
            }
        }
    }

    data object FOREST_GREEN : AdaptiveColor {
        override val name = "FOREST_GREEN"
        override val isPreferred = false
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(red = 34, green = 139, blue = 34)
            } else {
                Color(red = 28, green = 114, blue = 28)
            }
        }
    }

    data object SEA_GREEN : AdaptiveColor {
        override val name = "SEA_GREEN"
        override val isPreferred = false
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(red = 46, green = 139, blue = 87)
            } else {
                Color(red = 38, green = 114, blue = 71)
            }
        }
    }

    data object JUNGLE_GREEN : AdaptiveColor {
        override val name = "JUNGLE_GREEN"
        override val isPreferred = false
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return Color(red = 3, green = 136, blue = 88)
        }
    }

    data object LIGHT_SEA_GREEN : AdaptiveColor {
        override val name = "LIGHT_SEA_GREEN"
        override val isPreferred = false
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(red = 32, green = 178, blue = 170)
            } else {
                Color(red = 22, green = 128, blue = 122)
            }
        }
    }

    data object DARK_TURQUOISE : AdaptiveColor {
        override val name = "DARK_TURQUOISE"
        override val isPreferred = false
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(red = 0, green = 206, blue = 209)
            } else {
                Color(red = 0, green = 131, blue = 134)
            }
        }
    }

    data object DODGER_BLUE : AdaptiveColor {
        override val name = "DODGER_BLUE"
        override val isPreferred = false
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(red = 30, green = 144, blue = 255)
            } else {
                Color(red = 24, green = 116, blue = 205)
            }
        }
    }

    data object ROYAL_BLUE : AdaptiveColor {
        override val name = "ROYAL_BLUE"
        override val isPreferred = false
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(red = 72, green = 117, blue = 251)
            } else {
                Color(red = 65, green = 105, blue = 225)
            }
        }
    }

    data object DEEP_LAVENDER : AdaptiveColor {
        override val name = "DEEP_LAVENDER"
        override val isPreferred = false
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return Color(red = 135, green = 78, blue = 254)
        }
    }

    data object BLUE_VIOLET : AdaptiveColor {
        override val name = "BLUE_VIOLET"
        override val isPreferred = false
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(red = 166, green = 73, blue = 252)
            } else {
                Color(red = 138, green = 43, blue = 226)
            }
        }
    }

    data object DARK_VIOLET : AdaptiveColor {
        override val name = "DARK_VIOLET"
        override val isPreferred = false
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(red = 162, green = 76, blue = 210)
            } else {
                Color(red = 148, green = 0, blue = 211)
            }
        }
    }

    data object HELIOTROPE : AdaptiveColor {
        override val name = "HELIOTROPE"
        override val isPreferred = false
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return Color(red = 151, green = 93, blue = 175)
        }
    }

    data object BYZANTIUM : AdaptiveColor {
        override val name = "BYZANTIUM"
        override val isPreferred = false
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(red = 190, green = 56, blue = 243)
            } else {
                Color(red = 153, green = 41, blue = 189)
            }
        }
    }

    data object MAGENTA : AdaptiveColor {
        override val name = "MAGENTA"
        override val isPreferred = false
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(red = 255, green = 0, blue = 255)
            } else {
                Color(red = 205, green = 0, blue = 205)
            }
        }
    }

    data object DARK_MAGENTA : AdaptiveColor {
        override val name = "DARK_MAGENTA"
        override val isPreferred = false
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(red = 217, green = 0, blue = 217)
            } else {
                Color(red = 139, green = 0, blue = 139)
            }
        }
    }

    data object FUCHSIA : AdaptiveColor {
        override val name = "FUCHSIA"
        override val isPreferred = false
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(red = 214, green = 68, blue = 146)
            } else {
                Color(red = 189, green = 60, blue = 129)
            }
        }
    }

    data object DEEP_PINK : AdaptiveColor {
        override val name = "DEEP_PINK"
        override val isPreferred = false
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(red = 255, green = 20, blue = 147)
            } else {
                Color(red = 205, green = 16, blue = 117)
            }
        }
    }

    data object GRAYISH_MAGENTA : AdaptiveColor {
        override val name = "GRAYISH_MAGENTA"
        override val isPreferred = false
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return Color(red = 161, green = 96, blue = 128)
        }
    }

    data object HOT_PINK : AdaptiveColor {
        override val name = "HOT_PINK"
        override val isPreferred = false
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(red = 255, green = 105, blue = 180)
            } else {
                Color(red = 180, green = 74, blue = 126)
            }
        }
    }

    data object JAZZBERRY_JAM : AdaptiveColor {
        override val name = "JAZZBERRY_JAM"
        override val isPreferred = false
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(red = 230, green = 59, blue = 122)
            } else {
                Color(red = 185, green = 45, blue = 93)
            }
        }
    }

    data object MAROON : AdaptiveColor {
        override val name = "MAROON"
        override val isPreferred = false
        override fun getComposeColor(isDarkTheme: Boolean): Color {
            return if (isDarkTheme) {
                Color(red = 187, green = 82, blue = 99)
            } else {
                Color(red = 190, green = 49, blue = 68)
            }
        }
    }

    /**
     * A user-picked color, stored as a 32-bit ARGB value packed in an [Int].
     *
     * Renders identically in light and dark themes (no automatic adaptation).
     */
    data class Custom(val argb: Int) : AdaptiveColor {
        override val name: String get() = "$CUSTOM_PREFIX${"%08X".format(argb)}"
        override val isPreferred: Boolean = false
        override fun getComposeColor(isDarkTheme: Boolean): Color = Color(argb)
    }

    companion object {
        internal const val CUSTOM_PREFIX = "CUSTOM_"

        /**
         * All preset colors, in display order. Does not include [Custom] values.
         */
        val entries: List<AdaptiveColor> = listOf(
            RED, ORANGE, YELLOW, GREEN, MINT, TEAL, CYAN, BLUE, INDIGO, PURPLE, PINK, BROWN,
            FIRE_ENGINE_RED, CORAL, TOMATO, CINNABAR, RUST, ORANGE_RED, AUBURN, SADDLE_BROWN,
            DARK_ORANGE, DARK_GOLD, KHAKI, BRONZE, GOLD, OLIVE, OLIVE_DRAB, DARK_OLIVE_GREEN,
            MOSS_GREEN, LIME_GREEN, LIME, FOREST_GREEN, SEA_GREEN, JUNGLE_GREEN, LIGHT_SEA_GREEN,
            DARK_TURQUOISE, DODGER_BLUE, ROYAL_BLUE, DEEP_LAVENDER, BLUE_VIOLET, DARK_VIOLET,
            HELIOTROPE, BYZANTIUM, MAGENTA, DARK_MAGENTA, FUCHSIA, DEEP_PINK, GRAYISH_MAGENTA,
            HOT_PINK, JAZZBERRY_JAM, MAROON
        )

        /**
         * Resolve a preset by [name] or parse a [Custom] color from the `CUSTOM_AARRGGBB`
         * encoding. Throws [IllegalArgumentException] on unknown / malformed input, mirroring
         * the previous enum `valueOf` behavior.
         */
        fun valueOf(name: String): AdaptiveColor {
            if (name.startsWith(CUSTOM_PREFIX)) {
                val hex = name.removePrefix(CUSTOM_PREFIX)
                if (hex.length != 8 || hex.any { it !in '0'..'9' && it !in 'A'..'F' && it !in 'a'..'f' }) {
                    throw IllegalArgumentException("Malformed custom AdaptiveColor: $name")
                }
                return try {
                    Custom(hex.toLong(16).toInt())
                } catch (e: NumberFormatException) {
                    throw IllegalArgumentException("Malformed custom AdaptiveColor: $name", e)
                }
            }
            return entries.firstOrNull { it.name == name }
                ?: throw IllegalArgumentException("Unknown AdaptiveColor: $name")
        }
    }
}

object AdaptiveColorSerializer : KSerializer<AdaptiveColor> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("AdaptiveColor", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: AdaptiveColor) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): AdaptiveColor {
        return AdaptiveColor.valueOf(decoder.decodeString())
    }
}
