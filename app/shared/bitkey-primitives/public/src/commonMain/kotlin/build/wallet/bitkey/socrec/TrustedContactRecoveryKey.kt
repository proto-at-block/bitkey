package build.wallet.bitkey.socrec

import build.wallet.bitkey.keys.app.AppKey
import kotlinx.serialization.Serializable

/**
 * PAKE key used during the Social Recovery recovery process to perform key confirmation and
 * establish a secure channel with the Protected Customer. This key is owned by the Trusted Contact
 * and exists only ephemerally for the duration of the exchange. It is not persisted.
 */
@Serializable(with = TrustedContactRecoveryKey.Serializer::class)
data class TrustedContactRecoveryKey(
  override val key: AppKey,
) : SocRecKey, AppKey by key {
  internal object Serializer : SocRecPublicKeySerializer<TrustedContactRecoveryKey>(
    ::TrustedContactRecoveryKey
  )
}
