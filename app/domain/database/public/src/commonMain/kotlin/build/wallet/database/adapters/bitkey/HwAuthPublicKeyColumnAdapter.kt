package build.wallet.database.adapters.bitkey

import app.cash.sqldelight.ColumnAdapter
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.encrypt.Secp256k1PublicKey

/**
 * SqlDelight column adapter for [HwAuthPublicKey].
 *
 * Encodes as [HwAuthPublicKey.pubKey].
 */
internal object HwAuthPublicKeyColumnAdapter : ColumnAdapter<HwAuthPublicKey, String> {
  override fun decode(databaseValue: String): HwAuthPublicKey {
    return HwAuthPublicKey(Secp256k1PublicKey(databaseValue))
  }

  override fun encode(value: HwAuthPublicKey): String {
    return value.pubKey.value
  }
}
