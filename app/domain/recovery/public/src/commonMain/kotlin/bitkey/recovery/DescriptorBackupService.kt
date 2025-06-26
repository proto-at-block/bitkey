package bitkey.recovery

import bitkey.backup.DescriptorBackup
import build.wallet.bitcoin.descriptor.BitcoinDescriptor
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.cloud.backup.csek.SealedCsek
import com.github.michaelbull.result.Result

/**
 * Service responsible for encryption, decryption, and upload of descriptor backups during account recovery processes.
 */
interface DescriptorBackupService {
  /**
   * Prepares descriptor backup data for the recovery process.
   *
   * This method gathers and organizes the necessary descriptor backup information
   * required for account recovery operations. It identifies which descriptors need
   * to be decrypted and which need to be encrypted, and determines the current
   * state of the SSEK (Server-Side Encryption Key).
   *
   * @param accountId The full account identifier for the account being recovered
   * @param factorToRecover The physical factor (hardware or app) that is being recovered
   * @param f8eSpendingKeyset The F8e spending keyset associated with the account
   * @param appSpendingKey The app's spending public key
   * @param hwSpendingKey The hardware wallet's spending public key
   *
   * @return [Result] containing [DescriptorBackupPreparedData] with organized backup information,
   *         or [Error] if preparation fails
   */
  suspend fun prepareDescriptorBackupsForRecovery(
    accountId: FullAccountId,
    factorToRecover: PhysicalFactor,
    f8eSpendingKeyset: F8eSpendingKeyset,
    appSpendingKey: AppSpendingPublicKey,
    hwSpendingKey: HwSpendingPublicKey,
  ): Result<DescriptorBackupPreparedData, Error>
}

/**
 * Data prepared for NFC transaction to process descriptor backups.
 * Contains descriptors to decrypt and descriptors to encrypt, plus the sealed CSEK for encryption.
 */
data class DescriptorBackupPreparedData(
  // All descriptorsToDecrypt should also be re-encrypted with the SSEK
  val descriptorsToDecrypt: List<DescriptorBackup>,
  // Descriptors keyed by the f8e spending keyset id
  val descriptorsToEncrypt: Map<String, BitcoinDescriptor>,
  val ssekState: SsekState,
)

sealed interface SsekState {
  /** We need to generate a new ssek and seal it with hardware. */
  data object RequiresNewSsek : SsekState

  /** We have an existing ssek but it needs to be unsealed by hardware and placed in the CsekDao. */
  data class NeedsUnsealed(val sealedSsek: SealedCsek) : SsekState

  /** We have an existing ssek and it is available in the CsekDao. */
  data class Available(val sealedSsek: SealedCsek) : SsekState
}
