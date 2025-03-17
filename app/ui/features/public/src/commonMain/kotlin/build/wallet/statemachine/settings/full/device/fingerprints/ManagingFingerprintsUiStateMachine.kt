package build.wallet.statemachine.settings.full.device.fingerprints

import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine to present screens for managing enrolled fingerprints. From here you can add a new
 * fingerprint enrollment, change the label of an existing fingerprint, remove a fingerprint, or
 * identify saved fingerprints.
 */
interface ManagingFingerprintsUiStateMachine : StateMachine<ManagingFingerprintsProps, ScreenModel>

data class ManagingFingerprintsProps(
  val onBack: () -> Unit,
  val onFwUpRequired: () -> Unit,
  val entryPoint: EntryPoint,
)

enum class EntryPoint {
  MONEY_HOME,
  DEVICE_SETTINGS,
}
