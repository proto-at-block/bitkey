package build.wallet.bitkey.app

import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature

/**
 * @param appGlobalAuthKeyHwSignature [appGlobalAuthPublicKey] signed with hardware's auth private
 * key.
 */
data class AppAuthPublicKeys(
  val appGlobalAuthPublicKey: AppGlobalAuthPublicKey,
  val appRecoveryAuthPublicKey: AppRecoveryAuthPublicKey,
  // TODO: make this non-nullable.
  val appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature?,
) {
  fun requireAppGlobalAuthKeyHwSignature(): AppGlobalAuthKeyHwSignature =
    requireNotNull(appGlobalAuthKeyHwSignature) {
      "Expected appGlobalAuthKeyHwSignature to be non-null."
    }
}
