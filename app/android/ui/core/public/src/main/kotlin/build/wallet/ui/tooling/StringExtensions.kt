package build.wallet.ui.tooling

import java.util.Locale

/**
 * Returns a human readable and titlecase name of an enum.
 *
 * Example:
 * ```
 * enum class Foo {
 *   BITCOIN_TIME,
 * }
 *
 * Foo.BITCOIN_TIME.titlecaseName() // "Bitcoin Time"
 * ```
 */
internal fun Enum<*>.titlecaseName(): String =
  name.replace('_', ' ').titlecaseSentence(locale = Locale.ENGLISH)

/**
 * Returns a new string where each word separated by [delimiter] is a [titlecase].
 *
 * Example:
 * ```
 * "bitcoin time".titlecaseSentence() // "Bitcoin Time"
 * ```
 */
internal fun String.titlecaseSentence(
  locale: Locale = Locale.ENGLISH,
  delimiter: String = " ",
): String =
  lowercase(locale)
    .split(delimiter)
    .joinToString(separator = " ") { word -> word.titlecase(locale) }

/**
 * Returns a new string where first character is uppercase and remaining characters are lowercase.
 *
 * Example:
 * ```
 * "bitCOIN".titlecase() # "Bitcoin"
 * ```
 */
private fun String.titlecase(locale: Locale = Locale.ENGLISH): String {
  return replaceFirstChar {
    when {
      it.isLowerCase() -> it.titlecase(locale)
      else -> it.toString()
    }
  }
}
