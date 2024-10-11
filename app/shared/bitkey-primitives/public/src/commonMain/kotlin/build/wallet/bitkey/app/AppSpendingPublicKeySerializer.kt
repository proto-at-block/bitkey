package build.wallet.bitkey.app

import build.wallet.serialization.DelegateSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Serializes and deserializes [AppSpendingPublicKey].
 *
 * Alterations to this class can fail test and break cloud backups,
 * so proceed with caution.
 */
internal object AppSpendingPublicKeySerializer : DelegateSerializer<String, AppSpendingPublicKey>(
  String.serializer()
) {
  override fun serialize(data: AppSpendingPublicKey): String = data.key.dpub

  override fun deserialize(data: String): AppSpendingPublicKey = AppSpendingPublicKey(data)
}
