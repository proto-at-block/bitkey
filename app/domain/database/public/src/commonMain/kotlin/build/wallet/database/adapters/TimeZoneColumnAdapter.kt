package build.wallet.database.adapters

import app.cash.sqldelight.ColumnAdapter
import build.wallet.email.Email
import kotlinx.datetime.TimeZone

/**
 * SqlDelight column adapter for [Email].
 *
 * Encodes as [TimeZone.id].
 */
internal object TimeZoneColumnAdapter : ColumnAdapter<TimeZone, String> {
  override fun decode(databaseValue: String): TimeZone {
    return TimeZone.of(databaseValue)
  }

  override fun encode(value: TimeZone): String {
    return value.id
  }
}
