package build.wallet.statemachine.account.create.full.onboard.notifications

/**
 * Model for a line item in the [NotificationPreferencesSetupFormBodyModel]
 */
data class NotificationPreferencesSetupFormItemModel(
  val state: State,
  val onClick: (() -> Unit)?,
) {
  enum class State {
    /** The item still needs to either be completed or skipped */
    NeedsAction,
    Completed,
    Skipped,
  }
}
