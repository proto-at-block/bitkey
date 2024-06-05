package build.wallet.statemachine.settings.full.device.resetdevice.confirmation

/**
 * Model for a line item in the [ResettingDeviceConfirmationModel]
 */
data class ResettingDeviceConfirmationItemModel(
  val state: ResettingDeviceConfirmationState,
  val title: String,
  val onClick: () -> Unit,
)

sealed class ResettingDeviceConfirmationState {
  data object NotCompleted : ResettingDeviceConfirmationState()

  data object Completed : ResettingDeviceConfirmationState()
}
