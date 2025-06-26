package build.wallet.emergencyexitkit

import bitkey.account.FullAccountConfig
import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitkey.app.AppSpendingKeypair
import build.wallet.catchingResult
import build.wallet.cloud.backup.csek.CsekDao
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.emergencyexitkit.EmergencyExitKitBackup.EmergencyExitKitBackupV1
import build.wallet.emergencyexitkit.EmergencyExitPayloadRestorer.AccountRestoration
import build.wallet.emergencyexitkit.EmergencyExitPayloadRestorer.EmergencyExitPayloadRestorerError
import build.wallet.emergencyexitkit.EmergencyExitPayloadRestorer.EmergencyExitPayloadRestorerError.*
import build.wallet.encrypt.SymmetricKeyEncryptor
import build.wallet.ensure
import build.wallet.f8e.F8eEnvironment
import build.wallet.logging.logFailure
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.toErrorIfNull

@BitkeyInject(AppScope::class)
class EmergencyExitPayloadRestorerImpl(
  private val csekDao: CsekDao,
  private val symmetricKeyEncryptor: SymmetricKeyEncryptor,
  private val appPrivateKeyDao: AppPrivateKeyDao,
  private val emergencyExitKitPayloadDecoder: EmergencyExitKitPayloadDecoder,
) : EmergencyExitPayloadRestorer {
  override suspend fun restoreFromPayload(
    payload: EmergencyExitKitPayload,
  ): Result<AccountRestoration, EmergencyExitPayloadRestorerError> =
    coroutineBinding {
      when (payload) {
        is EmergencyExitKitPayload.EmergencyExitKitPayloadV1 -> {
          val pkek =
            csekDao.get(payload.sealedHwEncryptionKey)
              .mapError { CsekMissing(cause = it) }
              .toErrorIfNull { CsekMissing() }
              .bind()

          val encodedBackup =
            catchingResult {
              symmetricKeyEncryptor.unseal(
                sealedData = payload.sealedActiveSpendingKeys,
                key = pkek.key
              )
            }
              .mapError { DecryptionFailed(cause = it) }
              .bind()

          val backup =
            emergencyExitKitPayloadDecoder.decodeDecryptedBackup(encodedBackup)
              .mapError { InvalidBackup(cause = it) }
              .bind() as EmergencyExitKitBackupV1

          ensure(backup is EmergencyExitKitBackupV1) {
            InvalidBackup(Error("Expected backup to be of type EmergencyExitKitBackupV1."))
          }

          // Load the spending app private key into the DAO.
          appPrivateKeyDao.storeAppSpendingKeyPair(
            AppSpendingKeypair(
              publicKey = backup.spendingKeyset.appKey,
              privateKey = backup.appSpendingKeyXprv
            )
          )
            .mapError {
              AppPrivateKeyStorageFailed(cause = it)
            }
            .bind()

          AccountRestoration(
            activeSpendingKeyset = backup.spendingKeyset,
            fullAccountConfig =
              FullAccountConfig(
                bitcoinNetworkType = backup.spendingKeyset.networkType,
                f8eEnvironment = F8eEnvironment.ForceOffline,
                isHardwareFake = false,
                isUsingSocRecFakes = false,
                isTestAccount = false
              )
          )
        }
      }
    }.logFailure { "EEK payload failed to decrypt" }
}
