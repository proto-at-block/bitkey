package build.wallet.sqldelight.adapter

import app.cash.sqldelight.ColumnAdapter
import okio.ByteString
import okio.ByteString.Companion.toByteString

/**
 * SqlDelight column adapter for [ByteString].
 */
object ByteStringColumnAdapter : ColumnAdapter<ByteString, ByteArray> {
  override fun decode(databaseValue: ByteArray): ByteString {
    return databaseValue.toByteString()
  }

  override fun encode(value: ByteString): ByteArray {
    return value.toByteArray()
  }
}
