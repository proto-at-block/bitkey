package build.wallet.statemachine.nfc

import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.statemachine.core.AppSegment
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.StateMachine

/**
 * A reusable state machine that proves possession of the paired hardware device
 * via a single NFC tap.
 *
 * Uses Option 3 from the Single-Tap Proof of Possession design:
 * 1. NFC tap
 * 2. [queryAuthentication] must report unlocked
 * 3. [getDeviceInfo().serial] must match expected serial from [FirmwareDeviceInfoDao]
 *
 * This is intended for protecting app-controlled security configuration changes
 * (e.g. biometric app lock) without the broader risks of generic auth-key challenge signing.
 */
interface HardwarePresenceUiStateMachine : StateMachine<HardwarePresenceProps, ScreenModel>

/**
 * Props for [HardwarePresenceUiStateMachine].
 *
 * @param onSuccess Called when proof of possession succeeds (device is authenticated and
 *   serial matches the paired hardware). If the tapped device is locked, the NFC
 *   transaction fails with [build.wallet.nfc.NfcException.CommandErrorUnauthenticated]
 *   and the standard NFC error screen ("Device Locked") is shown instead.
 * @param onCancel Called when the user cancels the NFC session or dismisses the error screen.
 * @param screenPresentationStyle How the NFC screen should be presented.
 * @param segment The [AppSegment] of the calling flow, used for error telemetry.
 * @param actionDescription Description of the action being performed, used for error telemetry.
 * @param eventTrackerContext Analytics context for the NFC session.
 */
data class HardwarePresenceProps(
  val onSuccess: suspend () -> Unit,
  val onCancel: () -> Unit,
  val screenPresentationStyle: ScreenPresentationStyle = ScreenPresentationStyle.FullScreen,
  val segment: AppSegment,
  val actionDescription: String,
  val eventTrackerContext: NfcEventTrackerScreenIdContext,
  val showNativeSheetOnIos: Boolean = true,
)
