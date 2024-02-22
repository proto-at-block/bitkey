package build.wallet.bitkey.socrec

import build.wallet.bitkey.keys.app.AppKey
import kotlinx.serialization.Serializable

@Serializable(with = ProtectedCustomerEphemeralKey.Serializer::class)
data class ProtectedCustomerEphemeralKey(
  override val key: AppKey,
) : SocRecKey, AppKey by key {
  internal object Serializer : SocRecPublicKeySerializer<ProtectedCustomerEphemeralKey>(
    ::ProtectedCustomerEphemeralKey
  )
}
