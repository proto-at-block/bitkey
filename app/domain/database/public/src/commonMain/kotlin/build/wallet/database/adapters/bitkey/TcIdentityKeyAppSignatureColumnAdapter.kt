package build.wallet.database.adapters.bitkey

import app.cash.sqldelight.ColumnAdapter
import build.wallet.bitkey.relationships.TcIdentityKeyAppSignature

/**
 * SqlDelight column adapter for [TcIdentityKeyAppSignature].
 *
 * Encodes as [TcIdentityKeyAppSignature.value].
 */
internal object TcIdentityKeyAppSignatureColumnAdapter :
  ColumnAdapter<TcIdentityKeyAppSignature, String> {
  override fun decode(databaseValue: String): TcIdentityKeyAppSignature {
    return TcIdentityKeyAppSignature(databaseValue)
  }

  override fun encode(value: TcIdentityKeyAppSignature): String {
    return value.value
  }
}
