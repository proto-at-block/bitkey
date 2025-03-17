package build.wallet.emergencyaccesskit

import build.wallet.bitkey.app.AppSpendingPrivateKey
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.encrypt.SealedData

sealed interface EmergencyAccessKitPayload {
  /**
   * The payload data stored in the Emergency Access Kit. Converted into a protobuf representation,
   * then encoded in Base58.
   */
  data class EmergencyAccessKitPayloadV1(
    val sealedHwEncryptionKey: SealedCsek,
    val sealedActiveSpendingKeys: SealedData,
  ) : EmergencyAccessKitPayload
}

sealed interface EmergencyAccessKitBackup {
  /**
   * The minimal amount of information need to transfer funds after restoring
   * from the Emergency Access Kit backup. A number of fields are filled in
   * with dummy information, as this backup only provides the necessary data
   * to transfer funds, not to operate the app fully.
   *
   * See the [Emergency Access Eng Design](https://docs.google.com/document/d/1frmVxpUnbswWBJRh57u7GiUL_enHUV3wgHckR49q50E/edit#heading=h.j9zdqztixp34)
   */
  data class EmergencyAccessKitBackupV1(
    val spendingKeyset: SpendingKeyset,
    val appSpendingKeyXprv: AppSpendingPrivateKey,
  ) : EmergencyAccessKitBackup
}
