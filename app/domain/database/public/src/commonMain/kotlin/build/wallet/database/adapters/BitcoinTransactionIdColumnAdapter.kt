package build.wallet.database.adapters

import app.cash.sqldelight.ColumnAdapter
import build.wallet.bitcoin.transactions.BitcoinTransactionId

/**
 * SqlDelight column adapter for [BitcoinTransactionId].
 *
 * Encodes as [BitcoinTransactionId.value].
 */
internal object BitcoinTransactionIdColumnAdapter : ColumnAdapter<BitcoinTransactionId, String> {
  override fun decode(databaseValue: String): BitcoinTransactionId {
    return BitcoinTransactionId(value = databaseValue)
  }

  override fun encode(value: BitcoinTransactionId): String {
    return value.value
  }
}
