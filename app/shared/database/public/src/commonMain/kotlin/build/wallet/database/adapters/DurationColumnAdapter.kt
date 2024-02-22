package build.wallet.database.adapters

import app.cash.sqldelight.ColumnAdapter
import kotlin.time.Duration

internal object DurationColumnAdapter : ColumnAdapter<Duration, String> {
  override fun decode(databaseValue: String): Duration {
    return Duration.parseIsoString(databaseValue)
  }

  override fun encode(value: Duration): String {
    return value.toIsoString()
  }
}
