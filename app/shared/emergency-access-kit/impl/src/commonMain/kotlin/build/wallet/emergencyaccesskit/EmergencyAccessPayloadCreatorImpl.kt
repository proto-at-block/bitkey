package build.wallet.emergencyaccesskit

import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitkey.keybox.Keybox
import build.wallet.cloud.backup.csek.CsekDao
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.emergencyaccesskit.EmergencyAccessPayloadCreator.EmergencyAccessPayloadCreatorError
import build.wallet.encrypt.SymmetricKeyEncryptor
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.toErrorIfNull

class EmergencyAccessPayloadCreatorImpl(
  private val csekDao: CsekDao,
  private val symmetricKeyEncryptor: SymmetricKeyEncryptor,
  private val appPrivateKeyDao: AppPrivateKeyDao,
  private val emergencyAccessKitPayloadDecoder: EmergencyAccessKitPayloadDecoder,
) : EmergencyAccessPayloadCreator {
  override suspend fun create(
    keybox: Keybox,
    sealedCsek: SealedCsek,
  ): Result<EmergencyAccessKitPayload, EmergencyAccessPayloadCreatorError> =
    coroutineBinding {
      val xprv =
        appPrivateKeyDao
          .getAppSpendingPrivateKey(keybox.activeSpendingKeyset.appKey)
          .mapError { EmergencyAccessPayloadCreatorError.AppPrivateKeyMissing(cause = it) }
          .toErrorIfNull { EmergencyAccessPayloadCreatorError.AppPrivateKeyMissing() }
          .bind()

      val backup =
        EmergencyAccessKitBackup.EmergencyAccessKitBackupV1(
          spendingKeyset = keybox.activeSpendingKeyset,
          appSpendingKeyXprv = xprv
        )

      val pkek =
        csekDao
          .get(sealedCsek)
          .mapError { EmergencyAccessPayloadCreatorError.CsekMissing(cause = it) }
          .toErrorIfNull { EmergencyAccessPayloadCreatorError.CsekMissing() }
          .bind()

      val encodedBackup = emergencyAccessKitPayloadDecoder.encodeBackup(backup)

      val sealedData =
        symmetricKeyEncryptor.seal(
          unsealedData = encodedBackup,
          key = pkek.key
        )

      EmergencyAccessKitPayload.EmergencyAccessKitPayloadV1(
        sealedHwEncryptionKey = sealedCsek,
        sealedActiveSpendingKeys = sealedData
      )
    }
}
