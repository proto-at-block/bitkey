package build.wallet.bitkey.socrec

import build.wallet.bitkey.keys.app.AppKey
import kotlinx.serialization.Serializable

/**
 * PAKE key used during the Social Recovery process to perform key confirmation and
 * establish a secure channel with the Trusted Contact. This key is owned by the Protected
 * Customer and persisted for the duration of the exchange. A new key is created per
 * Trusted Contact.
 */
@Serializable(with = ProtectedCustomerRecoveryPakeKey.Serializer::class)
data class ProtectedCustomerRecoveryPakeKey(
  override val key: AppKey,
) : SocRecKey, AppKey by key {
  internal object Serializer : SocRecPublicKeySerializer<ProtectedCustomerRecoveryPakeKey>(
    ::ProtectedCustomerRecoveryPakeKey
  )
}
