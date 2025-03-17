package build.wallet.database.adapters.bitkey

import app.cash.sqldelight.ColumnAdapter
import build.wallet.bitkey.f8e.SoftwareAccountId

/**
 * SqlDelight column adapter for [SoftwareAccountId].
 *
 * Encodes as [SoftwareAccountId.serverId].
 */
internal object SoftwareAccountIdColumnAdapter : ColumnAdapter<SoftwareAccountId, String> {
  override fun decode(databaseValue: String): SoftwareAccountId {
    return SoftwareAccountId(serverId = databaseValue)
  }

  override fun encode(value: SoftwareAccountId): String {
    return value.serverId
  }
}
