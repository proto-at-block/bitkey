package build.wallet.sqldelight.adapter

import app.cash.sqldelight.ColumnAdapter
import kotlinx.datetime.Instant

/**
 * SqlDelight column adapter for [Instant] as [Long] epoch milliseconds.
 *
 * Use [InstantAsIso8601ColumnAdapter] instead. See `20.sqm` database migration and relevant
 * migration tests as an example.
 */
@Deprecated("Use InstantAsIso8601ColumnAdapter")
object InstantAsEpochMillisecondsColumnAdapter : ColumnAdapter<Instant, Long> {
  override fun decode(databaseValue: Long): Instant {
    return Instant.fromEpochMilliseconds(databaseValue)
  }

  override fun encode(value: Instant): Long {
    return value.toEpochMilliseconds()
  }
}
