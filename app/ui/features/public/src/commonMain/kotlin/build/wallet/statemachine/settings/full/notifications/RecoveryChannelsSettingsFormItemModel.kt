package build.wallet.statemachine.settings.full.notifications

import build.wallet.statemachine.account.create.full.onboard.notifications.UiErrorHint

/**
 * Model for a line item in the recovery channels settings screen.
 */
data class RecoveryChannelsSettingsFormItemModel(
  val displayValue: String? = null,
  val enabled: EnabledState,
  val uiErrorHint: UiErrorHint?,
  val onClick: (() -> Unit)?,
)

enum class EnabledState {
  Loading,
  Enabled,
  Disabled,
}
