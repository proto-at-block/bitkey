package build.wallet.database.adapters.bitkey

import app.cash.sqldelight.ColumnAdapter
import build.wallet.bitkey.app.AppGlobalAuthPublicKey
import build.wallet.bitkey.app.AppRecoveryAuthPublicKey
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.encrypt.Secp256k1PublicKey

/**
 * SqlDelight column adapter for [AppGlobalAuthPublicKey].
 *
 * Encodes as [AppGlobalAuthPublicKey.pubKey].
 */
internal object AppGlobalAuthPublicKeyColumnAdapter :
  ColumnAdapter<AppGlobalAuthPublicKey, String> {
  override fun decode(databaseValue: String): AppGlobalAuthPublicKey {
    return AppGlobalAuthPublicKey(Secp256k1PublicKey(databaseValue))
  }

  override fun encode(value: AppGlobalAuthPublicKey): String {
    return value.pubKey.value
  }
}

/**
 * SqlDelight column adapter for [AppRecoveryAuthPublicKey].
 *
 * Encodes as [AppRecoveryAuthPublicKey.pubKey].
 */
internal object AppRecoveryAuthPublicKeyColumnAdapter :
  ColumnAdapter<AppRecoveryAuthPublicKey, String> {
  override fun decode(databaseValue: String): AppRecoveryAuthPublicKey {
    return AppRecoveryAuthPublicKey(Secp256k1PublicKey(databaseValue))
  }

  override fun encode(value: AppRecoveryAuthPublicKey): String {
    return value.pubKey.value
  }
}

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
