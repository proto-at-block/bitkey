package build.wallet.recovery

import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.crypto.PublicKey
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

/**
 * A challenge to be signed by app and hardware as a way to ensure that app knows what it's doing.
 *
 * @property bytes utf-8 encoding of a text representation of challenge, which uses format:
 * ```
 * "CompleteDelayNotify"
 *  + destinationHardwareAuthPubKey
 *  + destinationAppGlobalAuthPubKey
 *  + destinationAppRecoveryAuthPubKey
 * ```
 */
data class ChallengeToCompleteRecovery(
  val bytes: ByteString,
) {
  constructor(
    app: PublicKey<AppGlobalAuthKey>,
    recovery: PublicKey<AppRecoveryAuthKey>,
    hw: HwAuthPublicKey,
  ) : this(
    bytes = "CompleteDelayNotify${hw.pubKey.value}${app.value}${recovery.value ?: ""}".encodeUtf8()
  )
}

/**
 * [ChallengeToCompleteRecovery] signed with [signingFactor]'s auth private key.
 */
data class SignedChallengeToCompleteRecovery(
  val signature: String,
  val signingFactor: PhysicalFactor,
)
