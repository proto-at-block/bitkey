package build.wallet.bitkey.socrec

import build.wallet.bitkey.keys.app.AppKey
import kotlinx.serialization.Serializable

@Serializable(with = ProtectedCustomerIdentityKey.Serializer::class)
data class ProtectedCustomerIdentityKey(
  override val key: AppKey,
) : SocRecKey, AppKey by key {
  internal object Serializer : SocRecPublicKeySerializer<ProtectedCustomerIdentityKey>(
    ::ProtectedCustomerIdentityKey
  )
}
