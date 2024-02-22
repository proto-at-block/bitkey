package build.wallet.sqldelight.adapter

import app.cash.sqldelight.ColumnAdapter
import com.squareup.wire.Message
import com.squareup.wire.ProtoAdapter
import okio.IOException

/**
 * This is a SqlDelight column adapter for Wire protos. While the adapter persists protos in the
 * database as serialized blobs, this method is not typically recommended due to potential issues
 * with backward compatibility. Migrating serialized proto content in a database can be notably
 * challenging.
 */
class WireColumnAdapter<T : Message<*, *>>(
  private val adapter: ProtoAdapter<T>,
) : ColumnAdapter<T, ByteArray> {
  override fun decode(databaseValue: ByteArray): T {
    try {
      return adapter.decode(databaseValue)
    } catch (e: IOException) {
      throw IllegalStateException(e)
    }
  }

  override fun encode(value: T): ByteArray {
    return adapter.encode(value)
  }
}
