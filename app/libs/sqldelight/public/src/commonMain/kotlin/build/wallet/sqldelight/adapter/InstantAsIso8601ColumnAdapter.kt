package build.wallet.sqldelight.adapter

import app.cash.sqldelight.ColumnAdapter
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

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
    return Instant.parse(databaseValue)
  }

  override fun encode(value: Instant): String {
    // Prevent negative numbers from being encoded into database as strings,
    // which can cause sorting errors.
    // This prevents all BC dates from being encoded (including zero)
    // BC dates start at 0, so -1 is actually 2 BC.. don't get me started.
    require(value >= LocalDateTime(1, 1, 1, 0, 0, 0, 0).toInstant(TimeZone.UTC)) {
      "Cannot encode BC dates"
    }
    // Similar to above, prevent 5-digit years from being encoded into database as strings,
    // As these are required in ISO-8601 format to be prefixed by a '+' symbol,
    // which can cause sorting errors.
    require(value <= LocalDateTime(9999, 12, 31, 23, 59, 59, 999999999).toInstant(TimeZone.UTC)) {
      "Cannot encode 5-digit years"
    }
    return value.toString()
  }
}
