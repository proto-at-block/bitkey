package build.wallet.sqldelight.adapter

import app.cash.sqldelight.ColumnAdapter
import kotlinx.datetime.Instant

/**
 * SqlDelight column adapter for encoding [Instant] as ISO-8601 [String].
 *
 * This is preferred over [InstantAsEpochMillisecondsColumnAdapter] for higher precision and
 * more human readable format. This also ensures we do not have any equality discrepancies in our
 * value objects when encoding and decoding instants. This is especially important for testing purposes,
 * and to ensure stability of Composables.
 *
 * Also see https://www.sqlite.org/lang_datefunc.html.
 */
object InstantAsIso8601ColumnAdapter : ColumnAdapter<Instant, String> {
  override fun decode(databaseValue: String): Instant {
    return Instant.parse(isoString = databaseValue)
  }

  override fun encode(value: Instant): String {
    return value.toString()
  }
}
