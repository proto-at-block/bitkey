package build.wallet.statemachine.dev.analytics

import build.wallet.statemachine.core.StateMachine
import build.wallet.ui.model.list.ListGroupModel

/**
 * State machine for deciding which feature flag related options to show on the
 * main debug menu
 */
interface AnalyticsOptionsUiStateMachine : StateMachine<AnalyticsOptionsUiProps, ListGroupModel?>

data class AnalyticsOptionsUiProps(
  val onShowAnalytics: () -> Unit,
)
