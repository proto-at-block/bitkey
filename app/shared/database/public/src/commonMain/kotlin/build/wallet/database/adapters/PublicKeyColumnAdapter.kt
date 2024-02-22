package build.wallet.database.adapters

import app.cash.sqldelight.ColumnAdapter
import build.wallet.crypto.PublicKey

object PublicKeyColumnAdapter : ColumnAdapter<PublicKey, String> {
  override fun decode(databaseValue: String) = PublicKey(databaseValue)

  override fun encode(value: PublicKey) = value.value
}
