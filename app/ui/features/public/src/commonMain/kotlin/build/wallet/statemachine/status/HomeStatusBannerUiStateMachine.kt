package build.wallet.statemachine.status

import build.wallet.availability.AppFunctionalityStatus
import build.wallet.statemachine.core.StateMachine
import build.wallet.ui.model.status.StatusBannerModel

/**
 * State machine for the status banner shown on Home screens (Money Home and Settings) when
 * the app is in a state of limited functionality (AppFunctionalityStatus.LimitedFunctionality).
 */
interface HomeStatusBannerUiStateMachine : StateMachine<HomeStatusBannerUiProps, StatusBannerModel?>

data class HomeStatusBannerUiProps(
  val onBannerClick: ((AppFunctionalityStatus.LimitedFunctionality) -> Unit)?,
)
