package build.wallet.database.adapters

import app.cash.sqldelight.ColumnAdapter
import build.wallet.bitcoin.metadata.WalletMetadataAccountId

/**
 * SqlDelight column adapter for [WalletMetadataAccountId].
 *
 * Encodes as [WalletMetadataAccountId.value].
 */
internal object WalletMetadataAccountIdColumnAdapter :
  ColumnAdapter<WalletMetadataAccountId, String> {
  override fun decode(databaseValue: String): WalletMetadataAccountId {
    return WalletMetadataAccountId(value = databaseValue)
  }

  override fun encode(value: WalletMetadataAccountId): String {
    return value.value
  }
}
