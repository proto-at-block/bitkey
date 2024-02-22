package build.wallet.emergencyaccesskit

import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitcoin.keys.ExtendedPrivateKey
import build.wallet.bitkey.app.AppSpendingKeypair
import build.wallet.bitkey.app.AppSpendingPrivateKey
import build.wallet.bitkey.keybox.KeyboxConfig
import build.wallet.catching
import build.wallet.cloud.backup.csek.CsekDao
import build.wallet.emergencyaccesskit.EmergencyAccessPayloadRestorer.AccountRestoration
import build.wallet.emergencyaccesskit.EmergencyAccessPayloadRestorer.EmergencyAccessPayloadRestorerError
import build.wallet.emergencyaccesskit.EmergencyAccessPayloadRestorer.EmergencyAccessPayloadRestorerError.CsekMissing
import build.wallet.emergencyaccesskit.EmergencyAccessPayloadRestorer.EmergencyAccessPayloadRestorerError.DecryptionFailed
import build.wallet.emergencyaccesskit.EmergencyAccessPayloadRestorer.EmergencyAccessPayloadRestorerError.InvalidBackup
import build.wallet.encrypt.SymmetricKeyEncryptor
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.toErrorIfNull

class EmergencyAccessPayloadRestorerImpl(
  private val csekDao: CsekDao,
  private val symmetricKeyEncryptor: SymmetricKeyEncryptor,
  private val appPrivateKeyDao: AppPrivateKeyDao,
) : EmergencyAccessPayloadRestorer {
  override suspend fun restoreFromPayload(
    payload: EmergencyAccessKitPayload,
  ): Result<AccountRestoration, EmergencyAccessPayloadRestorerError> =
    binding {
      val decoder = EmergencyAccessKitPayloadDecoderImpl

      when (payload) {
        is EmergencyAccessKitPayload.EmergencyAccessKitPayloadV1 -> {
          val pkek =
            csekDao.get(payload.hwEncryptionKeyCiphertext)
              .mapError { CsekMissing(cause = it) }
              .toErrorIfNull { CsekMissing() }
              .bind()

          val encodedBackup =
            Result.catching {
              symmetricKeyEncryptor.unseal(
                sealedData = payload.sealedActiveSpendingKeys,
                key = pkek.key
              )
            }
              .mapError { DecryptionFailed(cause = it) }
              .bind()

          val backup =
            decoder.decodeDecryptedBackup(encodedBackup)
              .mapError { InvalidBackup(cause = it) }
              .bind()

          // Load the spending app private key into the DAO.
          appPrivateKeyDao.storeAppSpendingKeyPair(
            AppSpendingKeypair(
              publicKey = backup.spendingKeyset.appKey,
              privateKey =
                AppSpendingPrivateKey(
                  key =
                    ExtendedPrivateKey(
                      xprv = backup.appSpendingKeyXprv,
                      // TODO(BKR-839): Backup and restore the xprv mnemonic if it's needed.
                      mnemonic = "MNEMONIC REMOVED DURING EMERGENCY ACCESS"
                    )
                )
            )
          )
            .mapError {
              EmergencyAccessPayloadRestorerError.AppPrivateKeyStorageFailed(cause = it)
            }
            .bind()

          AccountRestoration(
            activeSpendingKeyset = backup.spendingKeyset,
            keyboxConfig =
              KeyboxConfig(
                networkType = backup.spendingKeyset.networkType,
                f8eEnvironment = F8eEnvironment.ForceOffline,
                isHardwareFake = false,
                isUsingSocRecFakes = false,
                isTestAccount = false
              )
          )
        }
      }
    }
}
