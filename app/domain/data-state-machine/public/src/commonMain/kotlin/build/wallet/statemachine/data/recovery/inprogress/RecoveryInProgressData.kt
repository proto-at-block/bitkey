package build.wallet.statemachine.data.recovery.inprogress

import bitkey.account.HardwareType
import build.wallet.Progress
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.keybox.Keybox
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.crypto.PublicKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.nfc.transaction.NfcTransaction
import build.wallet.nfc.transaction.RecoveryNfcSession
import build.wallet.nfc.transaction.SealDelegatedDecryptionKey.SealedDataResult
import build.wallet.time.durationProgress
import build.wallet.time.nonNegativeDurationBetween
import com.github.michaelbull.result.getOrElse
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration

/**
 * Undergoing Delay & Notify recovery for lost physical factor (app or hardware).
 */
sealed interface RecoveryInProgressData {
  /**
   * Indicates that there is an ongoing recovery in progress and we are waiting for delay
   * before completing the recovery.
   *
   * @property factorToRecover physical factor that is being recovered.
   * @property delayPeriodStartTime timestamp of when Delay period has started.
   * @property delayPeriodEndTime timestamp of when Delay period is supposed to end and when we
   * should be ready to complete recovery.
   */
  data class WaitingForRecoveryDelayPeriodData(
    val factorToRecover: PhysicalFactor,
    val delayPeriodStartTime: Instant,
    val delayPeriodEndTime: Instant,
    val cancel: () -> Unit,
  ) : RecoveryInProgressData {
    fun delayPeriodProgress(clock: Clock): Progress =
      durationProgress(
        now = clock.now(),
        startTime = delayPeriodStartTime,
        endTime = delayPeriodEndTime
      ).getOrElse { Progress.Zero }

    fun remainingDelayPeriod(clock: Clock): Duration =
      nonNegativeDurationBetween(
        startTime = clock.now(),
        endTime = delayPeriodEndTime
      )
  }

  data class AwaitingProofOfPossessionForCancellationData(
    val appAuthKey: PublicKey<AppGlobalAuthKey>,
    val hardwareType: HardwareType,
    val addProof: (PrivilegedActionProof) -> Unit,
    val rollback: () -> Unit,
    val fullAccountId: FullAccountId,
  ) : RecoveryInProgressData

  /** Cancelling recovery. */
  data class CancellingData(
    val recoveredFactor: PhysicalFactor,
  ) : RecoveryInProgressData

  /**
   * [AwaitingProofOfPossessionForCancellationData] failed.
   */
  data class FailedToCancelRecoveryData(
    val recoveredFactor: PhysicalFactor,
    val cause: Error,
    val isNetworkError: Boolean,
    val onAcknowledge: () -> Unit,
  ) : RecoveryInProgressData

  /**
   * Indicates that we are in the notification verification flow because additional
   * verification was requested by the server for cancellation.
   */
  data class VerifyingNotificationCommsForCancellationData(
    val lostFactor: PhysicalFactor,
    val fullAccountId: FullAccountId,
    val onRollback: () -> Unit,
    val onComplete: () -> Unit,
  ) : RecoveryInProgressData

  /**
   * Indicates that recovery has been initiated and Delay period has been finished. We are
   * ready to complete recovery.
   */
  sealed interface CompletingRecoveryData : RecoveryInProgressData {
    /**
     * Indicates that we are the stage where we need to complete rotating of authentication keys.
     */
    sealed interface RotatingAuthData : CompletingRecoveryData {
      /**
       * Indicates that delay period has passed, we've loaded all necessary data, and we are now
       * ready to complete recovery with f8e.
       *
       * @property canCancelRecovery indicates if the recovery can be cancelled by customer.
       * Customer can cancel recovery if the recovery process has initiated (delay pending or finished),
       * but the recovery completion has not started. If customer attempts to complete recovery,
       * and some steps of the recovery completion process have executed, the recovery cannot be cancelled.
       * This is to prevent the putting the app into an inconsistent state. A specific scenario that this avoids,
       * is if the app successfully finished auth key rotation during completion, but subsequent completion steps failed.
       * If customer cancels recovery at that point, they will have rotated auth keys that they cannot use.
       * @property startComplete confirm to complete recovery.
       * Should move to [RotatingAuthKeysWithF8eData].
       * @property cancel confirm to cancel recovery.
       */
      data class ReadyToCompleteRecoveryData(
        val canCancelRecovery: Boolean,
        val physicalFactor: PhysicalFactor,
        val startComplete: () -> Unit,
        val cancel: () -> Unit,
      ) : RotatingAuthData

      /**
       * Awaiting for hardware to sign app generated challenge, CSEK, and SSEK.
       */
      data class AwaitingChallengeAndCsekSignedWithHardwareData(
        val nfcSession: RecoveryNfcSession,
      ) : RotatingAuthData

      data class FailedToRotateAuthData(
        val cause: Throwable,
        val factorToRecover: PhysicalFactor,
        val onConfirm: () -> Unit,
      ) : RotatingAuthData

      /**
       * Indicates that we are rotating authentication keys f8e and are completing D&N recovery.
       * Once done, should move to [AwaitingChallengeAndCsekSignedWithHardwareData] as a first
       * step towards creating new spending keys.
       */
      data class RotatingAuthKeysWithF8eData(
        val physicalFactor: PhysicalFactor,
      ) : RotatingAuthData

      /**
       * Indicates that we are checking completion attempt for success or cancellation.
       */
      data class CheckingCompletionAttemptData(
        val physicalFactor: PhysicalFactor,
      ) : RotatingAuthData

      /**
       * Provisioning the new app auth key to hardware via NFC after auth rotation.
       */
      data class ProvisioningAppAuthKeyToHardwareData(
        val nfcTransaction: NfcTransaction<Unit>,
      ) : RotatingAuthData

      /**
       * Indicates that we are removing trusted contacts from the account.
       */
      data class RemovingTrustedContactsData(
        val physicalFactor: PhysicalFactor,
      ) : RotatingAuthData
    }

    data class SealingDelegatedDecryptionKeyData(
      val nfcTransaction: NfcTransaction<SealedDataResult>,
    ) : RotatingAuthData

    data class DelegatedDecryptionKeyErrorStateData(
      val physicalFactor: PhysicalFactor,
      val cause: Error,
      val onRetry: () -> Unit,
      val onContinue: () -> Unit,
    ) : RotatingAuthData

    /** Loading state while preparing for proof-and-key-transfer NFC tap. */
    data class PreparingProofAndKeyTransferData(
      val physicalFactor: PhysicalFactor,
    ) : CompletingRecoveryData

    /** NFC tap 2 (Lost App): PoP + DDK unseal + SSEK unseal. */
    data class AwaitingProofAndKeyTransferLostAppData(
      val nfcSession: RecoveryNfcSession,
    ) : CompletingRecoveryData

    /** NFC tap 2 (Lost HW): PoP + DDK seal. */
    data class AwaitingProofAndKeyTransferLostHwData(
      val nfcSession: RecoveryNfcSession,
    ) : CompletingRecoveryData

    /**
     * Indicates that we are the stage where we have completed D&N recovery with f8e and now are
     * creating new spending keys.
     */
    sealed interface CreatingSpendingKeysData : RotatingAuthData {
      /**
       * Creating new spending keys and waiting for response from f8e. Once created, should move
       * to [PerformingCloudBackupData].
       */
      data class CreatingSpendingKeysWithF8EData(
        val physicalFactor: PhysicalFactor,
      ) : CreatingSpendingKeysData

      data class FailedToCreateSpendingKeysData(
        val physicalFactor: PhysicalFactor,
        val cause: Error,
        val onRetry: () -> Unit,
      ) : CreatingSpendingKeysData
    }

    data class FailedRegeneratingTcCertificatesData(
      val physicalFactor: PhysicalFactor,
      val cause: Error,
      val retry: () -> Unit,
    ) : CompletingRecoveryData

    /**
     * Indicates that we are currently generating new TC
     * certificates using new auth keys, verifying them and
     * uploading them to f8e.
     */
    data object RegeneratingTcCertificatesData : CompletingRecoveryData

    /**
     * Encrypting and backing up new keyset and app private keys. Once backup is finished,
     * should move to [PerformingSweepData].
     */
    data class PerformingDdkBackupData(
      val physicalFactor: PhysicalFactor,
    ) : CompletingRecoveryData

    data class FailedPerformingDdkBackupData(
      val physicalFactor: PhysicalFactor,
      val cause: Throwable?,
      val retry: () -> Unit,
    ) : CompletingRecoveryData

    /**
     * Encrypting and backing up new keyset and app private keys. Once backup is finished,
     * should move to [PerformingSweepData].
     */
    data class PerformingCloudBackupData(
      val sealedCsek: SealedCsek,
      val keybox: Keybox,
      val onBackupFinished: suspend () -> Unit,
      val onBackupFailed: (Throwable?) -> Unit,
    ) : CompletingRecoveryData

    data class FailedPerformingCloudBackupData(
      val keybox: Keybox,
      val physicalFactor: PhysicalFactor,
      val cause: Throwable?,
      val retry: () -> Unit,
    ) : CompletingRecoveryData

    /**
     * Performing sweep of funds into new spending keyset. Once sweep is done, recovery is fully
     * complete.
     */
    data class PerformingSweepData(
      val hasAttemptedSweep: Boolean,
      val physicalFactor: PhysicalFactor,
      val keybox: Keybox,
      val rollback: () -> Unit,
      val onCompletionFailed: (Error) -> Unit,
    ) : CompletingRecoveryData

    data class ExitedPerformingSweepData(
      val physicalFactor: PhysicalFactor,
      val retry: () -> Unit,
    ) : CompletingRecoveryData

    /**
     * Recovery sweep succeeded but saving the new keybox locally failed.
     * Funds have been transferred on-chain but the app hasn't activated the new keyset.
     */
    data class FailedToCompleteRecoveryData(
      val physicalFactor: PhysicalFactor,
      val cause: Throwable,
      val retry: () -> Unit,
    ) : CompletingRecoveryData

    /**
     * Processing descriptor backups for recovery - encryption/decryption and F8e upload.
     */
    sealed interface ProcessingDescriptorBackupsData : CompletingRecoveryData {
      val physicalFactor: PhysicalFactor

      /**
       * Processing (encrypt/decrypt) descriptor backups after CSEK has been unsealed.
       */
      data class HandlingDescriptorEncryption(
        override val physicalFactor: PhysicalFactor,
      ) : ProcessingDescriptorBackupsData

      /**
       * Uploading descriptor backups to F8e.
       */
      data class UploadingDescriptorBackupsData(
        override val physicalFactor: PhysicalFactor,
      ) : ProcessingDescriptorBackupsData

      /**
       * Failed to process descriptor backups via NFC or upload to F8e.
       */
      data class FailedToProcessDescriptorBackupsData(
        override val physicalFactor: PhysicalFactor,
        val cause: Error,
        val onRetry: () -> Unit,
      ) : ProcessingDescriptorBackupsData

      /**
       * Retrieving and decrypting descriptor backups to create complete keybox.
       */
      data class RetrievingDescriptorsForKeyboxData(
        override val physicalFactor: PhysicalFactor,
      ) : ProcessingDescriptorBackupsData
    }

    /**
     * Activating the spending keyset after it has been created and descriptor backups uploaded (if applicable).
     */
    data class ActivatingSpendingKeysetData(
      val physicalFactor: PhysicalFactor,
    ) : CompletingRecoveryData

    /**
     * Building hardware descriptor via NFC.
     */
    data class BuildingHardwareDescriptorData(
      val signedKeysResponse: build.wallet.f8e.recovery.SignedKeysetVerificationResponse,
      val appSpendingKeyXpub: String,
      val serverPrivateWalletRootXpub: String?,
      val networkType: build.wallet.bitcoin.BitcoinNetworkType,
      val f8eEnvironment: F8eEnvironment,
      val accountIndex: UInt = 0u,
      val onSuccess: (AppGlobalAuthKeyHwSignature) -> Unit,
      val onFailure: (Throwable) -> Unit,
    ) : CompletingRecoveryData

    /**
     * Failed to build hardware descriptor.
     */
    data class FailedToBuildHardwareDescriptorData(
      val physicalFactor: PhysicalFactor,
      val cause: Error,
      val onRetry: () -> Unit,
    ) : CompletingRecoveryData

    /**
     * Failed to activate the spending keyset.
     */
    data class FailedToActivateSpendingKeysetData(
      val physicalFactor: PhysicalFactor,
      val cause: Error,
      val onRetry: () -> Unit,
    ) : CompletingRecoveryData
  }
}
