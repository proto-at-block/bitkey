package build.wallet.bitkey.socrec

import build.wallet.bitkey.keys.app.AppKey
import kotlinx.serialization.Serializable

/**
 * Trusted contact public key if you're protecting other wallet, stored in the cloud backup
 * starting with V2.
 */
@Serializable(with = TrustedContactIdentityKey.Serializer::class)
data class TrustedContactIdentityKey(
  override val key: AppKey,
) : SocRecKey, AppKey by key {
  internal object Serializer : SocRecPublicKeySerializer<TrustedContactIdentityKey>(
    ::TrustedContactIdentityKey
  )
}
