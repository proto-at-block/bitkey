package build.wallet.database.adapters

import app.cash.sqldelight.ColumnAdapter
import build.wallet.bitcoin.address.BitcoinAddress

/**
 * SqlDelight column adapter for [BitcoinAddress].
 *
 * Encodes as [BitcoinAddress.address].
 */
internal object BitcoinAddressColumnAdapter : ColumnAdapter<BitcoinAddress, String> {
  override fun decode(databaseValue: String): BitcoinAddress {
    return BitcoinAddress(address = databaseValue)
  }

  override fun encode(value: BitcoinAddress): String {
    return value.address
  }
}
