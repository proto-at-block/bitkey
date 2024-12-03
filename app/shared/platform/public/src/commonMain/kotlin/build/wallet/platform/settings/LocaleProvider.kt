package build.wallet.platform.settings

import kotlin.jvm.JvmInline

fun interface LocaleProvider {
  /**
   * Provides an identifier/code for the current device's locale. See [Locale].
   */
  fun currentLocale(): Locale
}

/**
 * The locale identifier is based on the BCP 47 Language Tag standard.
 * The standard most commonly comprises of two tags, one for language (a ISO 639-1 language code)
 * and one for region (a ISO 3166 alpha-2 county code)
 *
 * Examples of locale identifiers on iOS include "en", "en_GB", "es_419"
 * Examples of locale identifiers (language tags) on Android include "en", "en-GB", "es-419"
 *
 * Note: iOS uses '_' and Android uses '-' formatting, but [LocaleProvider] is currently only used to
 * translate from a specific platform back to that specific platform, so the discrepancies
 * are OK. If this is needed to be used in another context (i.e. on the server), these
 * should be first normalized.
 */
@JvmInline
value class Locale(val value: String) {
  companion object // for companion extensions.
}
