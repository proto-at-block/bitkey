package build.wallet.database.adapters.bitkey

import app.cash.sqldelight.ColumnAdapter
import build.wallet.f8e.F8eEnvironment

internal object F8eEnvironmentColumnAdapter : ColumnAdapter<F8eEnvironment, String> {
  override fun decode(databaseValue: String): F8eEnvironment =
    F8eEnvironment.parseString(databaseValue)

  override fun encode(value: F8eEnvironment): String = value.asString()
}
