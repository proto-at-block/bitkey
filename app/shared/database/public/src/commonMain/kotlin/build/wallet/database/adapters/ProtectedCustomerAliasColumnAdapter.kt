package build.wallet.database.adapters

import app.cash.sqldelight.ColumnAdapter
import build.wallet.bitkey.socrec.ProtectedCustomerAlias

/**
 * SQLDelight column adapter for [ProtectedCustomerAlias].
 */
internal object ProtectedCustomerAliasColumnAdapter : ColumnAdapter<ProtectedCustomerAlias, String> {
  override fun decode(databaseValue: String) = ProtectedCustomerAlias(databaseValue)

  override fun encode(value: ProtectedCustomerAlias) = value.alias
}
