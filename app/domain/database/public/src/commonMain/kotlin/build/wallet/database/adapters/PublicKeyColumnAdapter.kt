package build.wallet.database.adapters

import app.cash.sqldelight.ColumnAdapter
import build.wallet.crypto.KeyPurpose
import build.wallet.crypto.PublicKey

class PublicKeyColumnAdapter<T : KeyPurpose> : ColumnAdapter<PublicKey<T>, String> {
  override fun decode(databaseValue: String) = PublicKey<T>(databaseValue)

  override fun encode(value: PublicKey<T>) = value.value
}
