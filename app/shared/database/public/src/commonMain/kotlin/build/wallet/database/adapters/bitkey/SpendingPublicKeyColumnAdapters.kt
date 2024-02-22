package build.wallet.database.adapters.bitkey

import app.cash.sqldelight.ColumnAdapter
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingPublicKey
import build.wallet.bitkey.hardware.HwSpendingPublicKey

/**
 * SqlDelight column adapter for [AppSpendingPublicKey].
 *
 * Encoded/decode as raw [AppSpendingPublicKey.key.dpub].
 */
internal object AppSpendingPublicKeyColumnAdapter : ColumnAdapter<AppSpendingPublicKey, String> {
  override fun decode(databaseValue: String): AppSpendingPublicKey {
    return AppSpendingPublicKey(dpub = databaseValue)
  }

  override fun encode(value: AppSpendingPublicKey): String {
    return value.key.dpub
  }
}

/**
 * SqlDelight column adapter for [F8eSpendingPublicKey].
 *
 * Encoded/decode as raw [F8eSpendingPublicKey.key.dpub].
 */
internal object F8eSpendingPublicKeyColumnAdapter : ColumnAdapter<F8eSpendingPublicKey, String> {
  override fun decode(databaseValue: String): F8eSpendingPublicKey {
    return F8eSpendingPublicKey(dpub = databaseValue)
  }

  override fun encode(value: F8eSpendingPublicKey): String {
    return value.key.dpub
  }
}

/**
 * SqlDelight column adapter for [HwSpendingPublicKey].
 *
 * Encoded/decode as raw [HwSpendingPublicKey.key.dpub].
 */
internal object HwSpendingPublicKeyColumnAdapter : ColumnAdapter<HwSpendingPublicKey, String> {
  override fun decode(databaseValue: String): HwSpendingPublicKey {
    return HwSpendingPublicKey(dpub = databaseValue)
  }

  override fun encode(value: HwSpendingPublicKey): String {
    return value.key.dpub
  }
}
