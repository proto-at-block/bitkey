package build.wallet.statemachine.settings.full.device.fingerprints

import bitkey.privilegedactions.FingerprintResetService
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.ensure
import build.wallet.firmware.EnrolledFingerprints
import build.wallet.firmware.EnrolledFingerprints.Companion.FIRST_FINGERPRINT_INDEX
import build.wallet.grants.Grant
import build.wallet.logging.logError
import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.NfcCommands
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset.FingerprintResetGrantProvisionResult
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.fold

data class AppSignedGrant(
  val version: Byte,
  val serializedRequest: ByteArray,
  val appSignature: ByteArray,
  val wsmSignature: ByteArray,
)

/**
 * Shared handler for common NFC grant provisioning logic used by both
 * FingerprintResetUiStateMachine and EnrollingFingerprintUiStateMachine.
 */
@BitkeyInject(ActivityScope::class)
class FingerprintResetGrantNfcHandler(
  private val fingerprintResetService: FingerprintResetService,
) {
  /**
   * Creates NfcSessionUIStateMachineProps for grant provisioning with common logic.
   */
  fun createGrantProvisionProps(
    grant: Grant,
    onSuccess: (FingerprintResetGrantProvisionResult) -> Unit,
    onCancel: () -> Unit = {},
    onError: (Throwable) -> Boolean = { false },
    eventTrackerContext: NfcEventTrackerScreenIdContext,
  ): NfcSessionUIStateMachineProps<FingerprintResetGrantProvisionResult> {
    return NfcSessionUIStateMachineProps(
      session = { session, commands ->
        val grantDelivered = fingerprintResetService.isGrantDelivered()

        if (commands.provideGrant(session, grant)) {
          // Happy path: grant successfully delivered
          beginFingerprintEnrollment(session, commands)
        } else if (grantDelivered) {
          // Delivery failed but previously delivered: assume consumed,
          // finish flow and show success.
          // This happens when the grant has been delivered & the user finished enrolling a new fingerprint
          val enrolledFingerprints = commands.getEnrolledFingerprints(session)
          val newFingerprints = cleanupFingerprintsAndGrant(session, commands, enrolledFingerprints)

          FingerprintResetGrantProvisionResult.FingerprintResetComplete(newFingerprints)
        } else {
          // Genuine failure: show retryable error
          FingerprintResetGrantProvisionResult.ProvideGrantFailed
        }
      },
      onSuccess = onSuccess,
      onCancel = onCancel,
      onError = onError,
      screenPresentationStyle = ScreenPresentationStyle.Modal,
      eventTrackerContext = eventTrackerContext,
      needsAuthentication = false,
      shouldLock = false,
      hardwareVerification = NfcSessionUIStateMachineProps.HardwareVerification.NotRequired
    )
  }

  /**
   * Removes all fingerprints except the first fingerprint index, deletes the grant, and returns updated fingerprints.
   */
  private suspend fun cleanupFingerprintsAndGrant(
    session: NfcSession,
    commands: NfcCommands,
    enrolledFingerprints: EnrolledFingerprints,
  ): EnrolledFingerprints {
    enrolledFingerprints
      .fingerprintHandles.filter { it.index != FIRST_FINGERPRINT_INDEX }
      .forEach { commands.deleteFingerprint(session, it.index) }
    fingerprintResetService.deleteFingerprintResetGrant()
    // Return only the fingerprint at the first index, since we deleted the others
    return enrolledFingerprints.copy(
      fingerprintHandles = enrolledFingerprints.fingerprintHandles.filter {
        it.index == FIRST_FINGERPRINT_INDEX
      }
    )
  }

  /**
   * Begins fingerprint enrollment after grant provision.
   * This is the common logic shared between both state machines.
   */
  suspend fun beginFingerprintEnrollment(
    session: NfcSession,
    commands: NfcCommands,
  ): FingerprintResetGrantProvisionResult =
    coroutineBinding {
      fingerprintResetService.markGrantAsDelivered().bind()
      fingerprintResetService.clearEnrolledFingerprints().bind()
      ensure(commands.startFingerprintEnrollment(session)) {
        IllegalStateException("Failed to start fingerprint enrollment")
      }
    }.fold(
      success = {
        FingerprintResetGrantProvisionResult.ProvideGrantSuccess
      },
      failure = { error ->
        logError(throwable = error) { "Failed granting fingerprint reset" }
        FingerprintResetGrantProvisionResult.ProvideGrantFailed
      }
    )
}
