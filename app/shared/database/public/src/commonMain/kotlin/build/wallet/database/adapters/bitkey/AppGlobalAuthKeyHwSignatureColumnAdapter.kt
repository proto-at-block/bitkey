package build.wallet.database.adapters.bitkey

import app.cash.sqldelight.ColumnAdapter
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature

/**
 * SqlDelight column adapter for [AppGlobalAuthKeyHwSignature].
 *
 * Encodes as [AppGlobalAuthKeyHwSignature.value].
 */
internal object AppGlobalAuthKeyHwSignatureColumnAdapter :
  ColumnAdapter<AppGlobalAuthKeyHwSignature, String> {
  override fun decode(databaseValue: String): AppGlobalAuthKeyHwSignature {
    return AppGlobalAuthKeyHwSignature(databaseValue)
  }

  override fun encode(value: AppGlobalAuthKeyHwSignature): String {
    return value.value
  }
}
