package build.wallet.cloud.backup.v2

import bitkey.serialization.DelegateSerializer
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import kotlinx.serialization.builtins.serializer

/**
 * Serializes and deserializes [HwSpendingPublicKey].
 *
 * Alterations to this class can fail test and break cloud backups,
 * so proceed with caution.
 */
object HwSpendingPublicKeySerializer : DelegateSerializer<String, HwSpendingPublicKey>(
  String.serializer()
) {
  override fun serialize(data: HwSpendingPublicKey): String = data.key.dpub

  override fun deserialize(data: String): HwSpendingPublicKey = HwSpendingPublicKey(data)
}
