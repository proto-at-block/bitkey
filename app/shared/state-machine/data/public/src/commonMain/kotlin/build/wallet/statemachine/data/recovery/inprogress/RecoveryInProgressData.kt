package build.wallet.statemachine.data.recovery.inprogress

import build.wallet.Progress
import build.wallet.bitkey.account.FullAccountConfig
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.bitkey.keybox.Keybox
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.crypto.PublicKey
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.nfc.transaction.NfcTransaction
import build.wallet.nfc.transaction.SealDelegatedDecryptionKey
import build.wallet.nfc.transaction.SignChallengeAndCsek.SignedChallengeAndCsek
import build.wallet.nfc.transaction.UnsealData
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
    val retryCloudRecovery: (() -> Unit)?,
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
    val addHardwareProofOfPossession: (HwFactorProofOfPossession) -> Unit,
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
    val f8eEnvironment: F8eEnvironment,
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
       * Awaiting for hardware to sign app generated challenge and CSEK.
       *
       * @property challengeToCompleteRecovery challenge generated by app to be signed by
       * hardware to later confirm recovery completion.
       * @property csek raw unsealed CSEK generated by app to be sealed by hardware. Will be
       * used to encrypt app backup once recovery is done.
       * @property addSignedChallengeAndCsek accept signed [challengeToCompleteRecovery] and sealed
       * [csek] from hardware. Should move to [RotatingAuthKeysWithF8eData].
       */
      data class AwaitingChallengeAndCsekSignedWithHardwareData(
        val nfcTransaction: NfcTransaction<SignedChallengeAndCsek>,
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
    }

    data class FetchingSealedDelegatedDecryptionKeyStringData(
      val nfcTransaction: NfcTransaction<UnsealData.UnsealedDataResult>,
    ) : RotatingAuthData

    data class SealingDelegatedDecryptionKeyData(
      val nfcTransaction: NfcTransaction<SealDelegatedDecryptionKey.SealedDataResult>,
    ) : RotatingAuthData

    data class DelegatedDecryptionKeyErrorStateData(
      val physicalFactor: PhysicalFactor,
      val cause: Error,
      val onRetry: () -> Unit,
      val onContinue: () -> Unit,
    ) : RotatingAuthData

    /**
     * Indicates that we are the stage where we have completed D&N recovery with f8e and now are
     * creating new spending keys.
     */
    sealed interface CreatingSpendingKeysData : RotatingAuthData {
      /**
       * Awaiting hardware to provide hardware proof of possession (in this case, signed
       * f8e access token).
       *
       * @property addHwFactorProofOfPossession accept hardware proof of possession. Should move
       * to [CreatingSpendingKeysWithF8EData].
       */
      data class AwaitingHardwareProofOfPossessionData(
        val fullAccountId: FullAccountId,
        val fullAccountConfig: FullAccountConfig,
        val appAuthKey: PublicKey<AppGlobalAuthKey>,
        val addHwFactorProofOfPossession: (HwFactorProofOfPossession) -> Unit,
        val rollback: () -> Unit,
      ) : CreatingSpendingKeysData

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
      val onBackupFinished: () -> Unit,
      val onBackupFailed: (Throwable?) -> Unit,
    ) : CompletingRecoveryData

    data class FailedPerformingCloudBackupData(
      val physicalFactor: PhysicalFactor,
      val cause: Throwable?,
      val retry: () -> Unit,
    ) : CompletingRecoveryData

    /**
     * Performing sweep of funds into new spending keyset. Once sweep is done, recovery is fully
     * complete.
     */
    data class PerformingSweepData(
      val physicalFactor: PhysicalFactor,
      val keybox: Keybox,
      val rollback: () -> Unit,
    ) : CompletingRecoveryData

    data class ExitedPerformingSweepData(
      val physicalFactor: PhysicalFactor,
      val retry: () -> Unit,
    ) : CompletingRecoveryData
  }
}
