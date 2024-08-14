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
  val appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
)
