package build.wallet.statemachine.settings.full.device.fingerprints

import build.wallet.firmware.EnrolledFingerprints
import build.wallet.firmware.FingerprintHandle
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * Allows users to enroll a new fingerprint post onboarding. Distinct from initial onboarding as
 * we do not need to negotiate keys with hardware and therefore can avoid some complexity.
 */
interface EnrollingFingerprintUiStateMachine : StateMachine<EnrollingFingerprintProps, ScreenModel>

data class EnrollingFingerprintProps(
  val onCancel: () -> Unit,
  val onSuccess: suspend (EnrolledFingerprints) -> Unit,
  val fingerprintHandle: FingerprintHandle,
  val enrolledFingerprints: EnrolledFingerprints,
  val context: EnrollmentContext,
)
