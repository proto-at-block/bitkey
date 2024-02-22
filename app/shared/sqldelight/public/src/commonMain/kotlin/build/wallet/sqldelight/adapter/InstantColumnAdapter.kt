package build.wallet.sqldelight.adapter

import app.cash.sqldelight.ColumnAdapter
import kotlinx.datetime.Instant

/**
 * SqlDelight column adapter for [Instant].
 *
 * Encodes/decodes [Instant] as [Long] epoch milliseconds.
 */
object InstantColumnAdapter : ColumnAdapter<Instant, Long> {
  override fun decode(databaseValue: Long): Instant {
    return Instant.fromEpochMilliseconds(databaseValue)
  }

  override fun encode(value: Instant): Long {
    return value.toEpochMilliseconds()
  }
}
