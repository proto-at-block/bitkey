package build.wallet.cloud.backup.v2

import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.serialization.DelegateSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Serializes and deserializes [AppSpendingPublicKey].
 *
 * Alterations to this class can fail test and break cloud backups,
 * so proceed with caution.
 */
object AppSpendingPublicKeySerializer : DelegateSerializer<String, AppSpendingPublicKey>(
  String.serializer()
) {
  override fun serialize(data: AppSpendingPublicKey): String = data.key.dpub

  override fun deserialize(data: String): AppSpendingPublicKey = AppSpendingPublicKey(data)
}
