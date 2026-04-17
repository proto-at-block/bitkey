package build.wallet.statemachine.account.create.full.onboard.notifications

/**
 * Model tracking the state of an individual notification channel (email, SMS, push)
 * in the sequential onboarding notification flow.
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
