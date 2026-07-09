package build.wallet.statemachine.settings.full.device.wipedevice.confirmation

/**
 * Model for a line item in the [WipingDeviceConfirmationBodyModel].
 */
data class WipingDeviceConfirmationItemModel(
  val state: WipingDeviceConfirmationState,
  val title: String,
  val onClick: () -> Unit,
)

sealed interface WipingDeviceConfirmationState {
  data object NotCompleted : WipingDeviceConfirmationState

  data object Completed : WipingDeviceConfirmationState
}
