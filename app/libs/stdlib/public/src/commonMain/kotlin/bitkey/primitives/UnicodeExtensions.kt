package bitkey.primitives

/*
 * Extension functions for Unicode code point manipulation.
 *
 * Replaces the kotlin-codepoints-deluxe dependency with a minimal implementation
 * tailored to our specific needs (primarily flag emoji generation).
 */

/**
 * Returns a sequence of Unicode code points in this string.
 *
 * This properly handles surrogate pairs for characters outside the Basic Multilingual Plane.
 *
 * Example:
 * ```
 * "US".codePointSequence().forEach { codePoint -> ... }
 * ```
 */
fun String.codePointSequence(): Sequence<Int> =
  sequence {
    var index = 0
    while (index < length) {
      val codePoint = codePointAt(index)
      yield(codePoint)
      index += Character.charCount(codePoint)
    }
  }

/**
 * Returns the Unicode code point at the specified index.
 *
 * Handles surrogate pairs correctly by combining high and low surrogates.
 */
private fun String.codePointAt(index: Int): Int {
  val high = this[index]

  // Check if this is a high surrogate (part of a surrogate pair)
  if (high.isHighSurrogate() && index + 1 < length) {
    val low = this[index + 1]
    if (low.isLowSurrogate()) {
      // Combine high and low surrogates into a single code point
      return Character.toCodePoint(high, low)
    }
  }

  return high.code
}

/**
 * Appends the specified Unicode code point to this StringBuilder.
 *
 * Properly handles code points outside the Basic Multilingual Plane by
 * generating surrogate pairs when necessary.
 *
 * Example:
 * ```
 * StringBuilder().appendCodePoint(0x1F1FA) // Regional indicator U
 * ```
 */
fun StringBuilder.appendCodePoint(codePoint: Int): StringBuilder {
  when {
    // BMP (Basic Multilingual Plane) - single char
    codePoint < Character.MIN_SUPPLEMENTARY_CODE_POINT -> {
      append(codePoint.toChar())
    }
    // Supplementary plane - needs surrogate pair
    Character.isValidCodePoint(codePoint) -> {
      append(Character.highSurrogate(codePoint))
      append(Character.lowSurrogate(codePoint))
    }
    else -> {
      throw IllegalArgumentException("Invalid code point: 0x${codePoint.toString(16)}")
    }
  }
  return this
}

/**
 * Platform-agnostic character utilities.
 */
private object Character {
  const val MIN_SUPPLEMENTARY_CODE_POINT = 0x10000
  const val MIN_HIGH_SURROGATE = 0xD800
  const val MIN_LOW_SURROGATE = 0xDC00
  const val MIN_CODE_POINT = 0x000000
  const val MAX_CODE_POINT = 0x10FFFF

  fun toCodePoint(
    high: Char,
    low: Char,
  ): Int {
    return ((high.code - MIN_HIGH_SURROGATE) shl 10) + (low.code - MIN_LOW_SURROGATE) +
      MIN_SUPPLEMENTARY_CODE_POINT
  }

  fun charCount(codePoint: Int): Int {
    return if (codePoint >= MIN_SUPPLEMENTARY_CODE_POINT) 2 else 1
  }

  fun isValidCodePoint(codePoint: Int): Boolean {
    return codePoint in MIN_CODE_POINT..MAX_CODE_POINT
  }

  fun highSurrogate(codePoint: Int): Char {
    return ((codePoint ushr 10) + (MIN_HIGH_SURROGATE - (MIN_SUPPLEMENTARY_CODE_POINT ushr 10)))
      .toChar()
  }

  fun lowSurrogate(codePoint: Int): Char {
    return ((codePoint and 0x3FF) + MIN_LOW_SURROGATE).toChar()
  }
}
