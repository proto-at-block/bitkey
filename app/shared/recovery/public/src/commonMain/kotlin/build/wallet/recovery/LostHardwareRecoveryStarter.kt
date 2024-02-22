package build.wallet.recovery

import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.keybox.Keybox
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.CancelDelayNotifyRecoveryErrorCode
import build.wallet.f8e.error.code.InitiateAccountDelayNotifyErrorCode
import com.github.michaelbull.result.Result

/**
 * Initiates new Delay + Notify recovery for Lost or Stolen hardware.
 */
interface LostHardwareRecoveryStarter {
  /**
   * Initiates delay + notify recovery for lost or stolen hardware, process is initiated
   * through f8e, DN recovery is written into local state.
   *
   * @param activeKeybox currently active local [Keybox].
   * @param destinationAppKeyBundle new App's Key Bundle.
   * @param destinationHardwareKeyBundle new Hardware's Key Bundle.
   */
  suspend fun initiate(
    activeKeybox: Keybox,
    destinationAppKeyBundle: AppKeyBundle,
    destinationHardwareKeyBundle: HwKeyBundle,
  ): Result<Unit, InitiateDelayNotifyHardwareRecoveryError>

  sealed class InitiateDelayNotifyHardwareRecoveryError : Error() {
    /**
     * Indicates that failed to find local account information:
     * - due to usage mistake by calling this API from the wrong state where account hasn't been
     * created yet and persisted in database (e.g. during onboarding).
     * - due to bad local state - account wasn't properly stored during onboarding/ cloud recovery,
     * or local database is corrupted.
     */
    data class LocalAccountMissing(
      override val cause: Error?,
    ) : InitiateDelayNotifyHardwareRecoveryError()

    data class ClearLocalRecoveryError(
      override val cause: Error,
    ) : InitiateDelayNotifyHardwareRecoveryError()

    data class F8eCancelActiveRecoveryError(
      val error: F8eError<CancelDelayNotifyRecoveryErrorCode>,
    ) : InitiateDelayNotifyHardwareRecoveryError() {
      override val cause = error.error
    }

    /**
     * Corresponds to an error when getting account status from f8e:
     * - due to regular networking error (poor connectivity, outages, etc). In this case, we can
     * retry the recovery initiation.
     * - due to some server error. In this case, we are unlikely to be able to start recovery.
     * - due to client error - e.g. bad input or serialization bug.
     */
    data class F8eGetAccountStatusError(
      override val cause: Error,
    ) : InitiateDelayNotifyHardwareRecoveryError()

    data class F8eGetRecoveryStatusError(
      override val cause: Error,
    ) : InitiateDelayNotifyHardwareRecoveryError()

    /**
     * Corresponds to an error when starting D&N recovery with f8e:
     * - due to regular networking error (poor connectivity, outages, etc). In this case, we can
     * retry the recovery initiation.
     * - due to some server error. In this case, we are unlikely to be able to start recovery.
     * - due to client error - e.g. bad input or serialization bug.
     */
    data class F8eInitiateDelayNotifyError(
      val error: F8eError<InitiateAccountDelayNotifyErrorCode>,
    ) : InitiateDelayNotifyHardwareRecoveryError() {
      override val cause = error.error
    }

    /**
     * We successfully initiated recovery with f8e but failed to persist local database state:
     * - due to a corrupted database. To recover from this we need to retrieve D&N status from server
     * and retry persisting locally.
     * - due to some rare I/O error
     */
    data class FailedToPersistRecoveryStateError(
      override val cause: Error,
    ) : InitiateDelayNotifyHardwareRecoveryError()
  }
}
