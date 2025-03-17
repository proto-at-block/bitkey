package build.wallet.statemachine.account.create.full.onboard.notifications

/**
 * Model for a line item in the [RecoveryChannelsSetupFormBodyModel]
 */
data class RecoveryChannelsSetupFormItemModel(
  val state: State,
  val displayValue: String? = null,
  val uiErrorHint: UiErrorHint,
  val onClick: (() -> Unit)?,
) {
  enum class State {
    NotCompleted,
    Completed,
  }
}
