package build.wallet.database.adapters

import app.cash.sqldelight.ColumnAdapter
import build.wallet.bitkey.relationships.TrustedContactAlias

/** SQLDelight column adapter for [TrustedContactAlias]. */
internal object TrustedContactAliasColumnAdapter : ColumnAdapter<TrustedContactAlias, String> {
  override fun decode(databaseValue: String) = TrustedContactAlias(databaseValue)

  override fun encode(value: TrustedContactAlias) = value.alias
}
