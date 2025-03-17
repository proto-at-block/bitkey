package build.wallet.database.adapters

import app.cash.sqldelight.ColumnAdapter
import build.wallet.encrypt.XCiphertext

object XCiphertextColumnAdapter : ColumnAdapter<XCiphertext, String> {
  override fun decode(databaseValue: String): XCiphertext {
    return XCiphertext(value = databaseValue)
  }

  override fun encode(value: XCiphertext): String {
    return value.value
  }
}
