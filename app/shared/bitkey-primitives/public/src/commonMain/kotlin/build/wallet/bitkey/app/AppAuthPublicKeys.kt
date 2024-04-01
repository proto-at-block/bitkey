package build.wallet.bitkey.app

import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.crypto.PublicKey

/**
 * @param appGlobalAuthKeyHwSignature [appGlobalAuthPublicKey] signed with hardware's auth private
 * key.
 */
data class AppAuthPublicKeys(
  val appGlobalAuthPublicKey: PublicKey<AppGlobalAuthKey>,
  val appRecoveryAuthPublicKey: PublicKey<AppRecoveryAuthKey>,
  // TODO: make this non-nullable.
  val appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature?,
) {
  fun requireAppGlobalAuthKeyHwSignature(): AppGlobalAuthKeyHwSignature =
    requireNotNull(appGlobalAuthKeyHwSignature) {
      "Expected appGlobalAuthKeyHwSignature to be non-null."
    }
}
