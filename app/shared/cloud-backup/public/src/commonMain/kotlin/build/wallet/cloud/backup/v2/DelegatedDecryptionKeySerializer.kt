package build.wallet.cloud.backup.v2

import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.socrec.DelegatedDecryptionKey
import build.wallet.serialization.DelegateSerializer

/**
 * Delegates serialization of [DelegatedDecryptionKey] to [AppKeySerializer] so that the private
 * key is stored.
 */
class DelegatedDecryptionKeySerializer : DelegateSerializer<AppKey, DelegatedDecryptionKey>(
  AppKeySerializer
) {
  override fun serialize(data: DelegatedDecryptionKey): AppKey = data.key

  override fun deserialize(data: AppKey): DelegatedDecryptionKey =
    DelegatedDecryptionKey(
      data
    )
}
