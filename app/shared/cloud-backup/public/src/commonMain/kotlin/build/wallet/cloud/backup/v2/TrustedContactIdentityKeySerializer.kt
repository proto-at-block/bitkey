package build.wallet.cloud.backup.v2

import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.socrec.TrustedContactIdentityKey
import build.wallet.serialization.DelegateSerializer

/**
 * Delegates serialization of [TrustedContactIdentityKey] to [AppKeySerializer] so that the private
 * key is stored.
 */
class TrustedContactIdentityKeySerializer : DelegateSerializer<AppKey, TrustedContactIdentityKey>(
  AppKeySerializer
) {
  override fun serialize(data: TrustedContactIdentityKey): AppKey = data.key

  override fun deserialize(data: AppKey): TrustedContactIdentityKey =
    TrustedContactIdentityKey(
      data
    )
}
