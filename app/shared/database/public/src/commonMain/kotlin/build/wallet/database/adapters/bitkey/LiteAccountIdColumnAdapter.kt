package build.wallet.database.adapters.bitkey

import app.cash.sqldelight.ColumnAdapter
import build.wallet.bitkey.f8e.LiteAccountId

/**
 * SqlDelight column adapter for [LiteAccountId].
 *
 * Encodes as [LiteAccountId.serverId].
 */
internal object LiteAccountIdColumnAdapter : ColumnAdapter<LiteAccountId, String> {
  override fun decode(databaseValue: String): LiteAccountId {
    return LiteAccountId(serverId = databaseValue)
  }

  override fun encode(value: LiteAccountId): String {
    return value.serverId
  }
}
