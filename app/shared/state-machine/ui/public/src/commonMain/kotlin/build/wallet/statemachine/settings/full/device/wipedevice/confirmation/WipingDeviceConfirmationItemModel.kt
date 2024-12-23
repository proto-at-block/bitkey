package build.wallet.statemachine.settings.full.device.wipedevice.confirmation

/**
 * Model for a line item in the [WipingDeviceConfirmationModel]
 */
data class WipingDeviceConfirmationItemModel(
  val state: WipingDeviceConfirmationState,
  val title: String,
  val onClick: () -> Unit,
)

sealed class WipingDeviceConfirmationState {
  data object NotCompleted : WipingDeviceConfirmationState()

  data object Completed : WipingDeviceConfirmationState()
}
