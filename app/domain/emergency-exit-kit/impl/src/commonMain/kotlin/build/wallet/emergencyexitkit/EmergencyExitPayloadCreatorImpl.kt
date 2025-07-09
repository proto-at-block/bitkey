package build.wallet.emergencyexitkit

import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitkey.keybox.Keybox
import build.wallet.cloud.backup.csek.CsekDao
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.emergencyexitkit.EmergencyExitPayloadCreator.EmergencyExitPayloadCreatorError
import build.wallet.encrypt.SymmetricKeyEncryptor
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.toErrorIfNull

@BitkeyInject(AppScope::class)
class EmergencyExitPayloadCreatorImpl(
  private val csekDao: CsekDao,
  private val symmetricKeyEncryptor: SymmetricKeyEncryptor,
  private val appPrivateKeyDao: AppPrivateKeyDao,
  private val emergencyExitKitPayloadDecoder: EmergencyExitKitPayloadDecoder,
) : EmergencyExitPayloadCreator {
  override suspend fun create(
    keybox: Keybox,
    sealedCsek: SealedCsek,
  ): Result<EmergencyExitKitPayload, EmergencyExitPayloadCreatorError> =
    coroutineBinding {
      val xprv =
        appPrivateKeyDao
          .getAppSpendingPrivateKey(keybox.activeSpendingKeyset.appKey)
          .mapError { EmergencyExitPayloadCreatorError.AppPrivateKeyMissing(cause = it) }
          .toErrorIfNull { EmergencyExitPayloadCreatorError.AppPrivateKeyMissing() }
          .bind()

      val backup =
        EmergencyExitKitBackup.EmergencyExitKitBackupV1(
          spendingKeyset = keybox.activeSpendingKeyset,
          appSpendingKeyXprv = xprv
        )

      val pkek =
        csekDao
          .get(sealedCsek)
          .mapError { EmergencyExitPayloadCreatorError.CsekMissing(cause = it) }
          .toErrorIfNull { EmergencyExitPayloadCreatorError.CsekMissing() }
          .bind()

      val encodedBackup = emergencyExitKitPayloadDecoder.encodeBackup(backup)

      val sealedData =
        symmetricKeyEncryptor.sealNoMetadata(
          unsealedData = encodedBackup,
          key = pkek.key
        )

      EmergencyExitKitPayload.EmergencyExitKitPayloadV1(
        sealedHwEncryptionKey = sealedCsek,
        sealedActiveSpendingKeys = sealedData
      )
    }
}
