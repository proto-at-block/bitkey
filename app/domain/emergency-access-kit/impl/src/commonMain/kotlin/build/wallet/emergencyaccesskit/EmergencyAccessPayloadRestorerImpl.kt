package build.wallet.emergencyaccesskit

import bitkey.account.FullAccountConfig
import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitkey.app.AppSpendingKeypair
import build.wallet.catchingResult
import build.wallet.cloud.backup.csek.CsekDao
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.emergencyaccesskit.EmergencyAccessKitBackup.EmergencyAccessKitBackupV1
import build.wallet.emergencyaccesskit.EmergencyAccessPayloadRestorer.AccountRestoration
import build.wallet.emergencyaccesskit.EmergencyAccessPayloadRestorer.EmergencyAccessPayloadRestorerError
import build.wallet.emergencyaccesskit.EmergencyAccessPayloadRestorer.EmergencyAccessPayloadRestorerError.*
import build.wallet.encrypt.SymmetricKeyEncryptor
import build.wallet.ensure
import build.wallet.f8e.F8eEnvironment
import build.wallet.logging.logFailure
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.toErrorIfNull

@BitkeyInject(AppScope::class)
class EmergencyAccessPayloadRestorerImpl(
  private val csekDao: CsekDao,
  private val symmetricKeyEncryptor: SymmetricKeyEncryptor,
  private val appPrivateKeyDao: AppPrivateKeyDao,
  private val emergencyAccessKitPayloadDecoder: EmergencyAccessKitPayloadDecoder,
) : EmergencyAccessPayloadRestorer {
  override suspend fun restoreFromPayload(
    payload: EmergencyAccessKitPayload,
  ): Result<AccountRestoration, EmergencyAccessPayloadRestorerError> =
    coroutineBinding {
      when (payload) {
        is EmergencyAccessKitPayload.EmergencyAccessKitPayloadV1 -> {
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
            emergencyAccessKitPayloadDecoder.decodeDecryptedBackup(encodedBackup)
              .mapError { InvalidBackup(cause = it) }
              .bind() as EmergencyAccessKitBackupV1

          ensure(backup is EmergencyAccessKitBackupV1) {
            InvalidBackup(Error("Expected backup to be of type EmergencyAccessKitBackupV1."))
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
