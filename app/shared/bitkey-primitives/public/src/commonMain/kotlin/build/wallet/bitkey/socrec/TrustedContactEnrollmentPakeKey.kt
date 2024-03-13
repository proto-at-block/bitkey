package build.wallet.bitkey.socrec

import build.wallet.bitkey.keys.app.AppKey
import kotlinx.serialization.Serializable

/**
 * PAKE key used during the Social Recovery enrollment process to perform key confirmation and
 * establish a secure channel with the Protected Customer. This key is owned by the Trusted Contact
 * and exists only ephemerally for the duration of the exchange. It is not persisted.
 */
@Serializable(with = TrustedContactEnrollmentPakeKey.Serializer::class)
data class TrustedContactEnrollmentPakeKey(
  override val key: AppKey,
) : SocRecKey, AppKey by key {
  internal object Serializer : SocRecPublicKeySerializer<TrustedContactEnrollmentPakeKey>(
    ::TrustedContactEnrollmentPakeKey
  )
}
