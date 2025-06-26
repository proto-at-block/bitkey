package build.wallet.emergencyexitkit

import build.wallet.bitkey.app.AppSpendingPrivateKey
import build.wallet.bitkey.spending.SpendingKeyset
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.encrypt.SealedData

sealed interface EmergencyExitKitPayload {
  /**
   * The payload data stored in the Emergency Exit Kit. Converted into a protobuf representation,
   * then encoded in Base58.
   */
  data class EmergencyExitKitPayloadV1(
    val sealedHwEncryptionKey: SealedCsek,
    val sealedActiveSpendingKeys: SealedData,
  ) : EmergencyExitKitPayload
}

sealed interface EmergencyExitKitBackup {
  /**
   * The minimal amount of information need to transfer funds after restoring
   * from the Emergency Exit Kit backup. A number of fields are filled in
   * with dummy information, as this backup only provides the necessary data
   * to transfer funds, not to operate the app fully.
   *
   * See the [Emergency Exit Kit Eng Design](https://docs.google.com/document/d/1frmVxpUnbswWBJRh57u7GiUL_enHUV3wgHckR49q50E/edit#heading=h.j9zdqztixp34)
   */
  data class EmergencyExitKitBackupV1(
    val spendingKeyset: SpendingKeyset,
    val appSpendingKeyXprv: AppSpendingPrivateKey,
  ) : EmergencyExitKitBackup
}
