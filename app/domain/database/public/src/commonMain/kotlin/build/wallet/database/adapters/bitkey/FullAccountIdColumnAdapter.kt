package build.wallet.database.adapters.bitkey

import app.cash.sqldelight.ColumnAdapter
import build.wallet.bitkey.f8e.FullAccountId

/**
 * SqlDelight column adapter for [FullAccountId].
 *
 * Encodes as [FullAccountId.serverId].
 */
internal object FullAccountIdColumnAdapter : ColumnAdapter<FullAccountId, String> {
  override fun decode(databaseValue: String): FullAccountId {
    return FullAccountId(serverId = databaseValue)
  }

  override fun encode(value: FullAccountId): String {
    return value.serverId
  }
}
