package build.wallet.statemachine.recovery.socrec.list.lite

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.bitkey.relationships.ProtectedCustomer
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.recovery.socrec.view.ViewingProtectedCustomerProps
import build.wallet.statemachine.recovery.socrec.view.ViewingProtectedCustomerUiStateMachine

class LiteListingTrustedContactsUiStateMachineImpl(
  private val viewingProtectedCustomerUiStateMachine: ViewingProtectedCustomerUiStateMachine,
) : LiteListingTrustedContactsUiStateMachine {
  @Composable
  override fun model(props: LiteListingTrustedContactsUiProps): ScreenModel {
    var state: State by remember { mutableStateOf(State.ViewingListState) }

    val screenModel =
      LiteTrustedContactsListBodyModel(
        onBackPressed = props.onExit,
        protectedCustomers = props.protectedCustomers,
        onProtectedCustomerPressed = { state = State.ViewingProtectedCustomerDetailState(it) },
        onAcceptInvitePressed = props.onAcceptInvitePressed
      ).asRootScreen()

    return when (val currentState = state) {
      is State.ViewingListState ->
        screenModel

      is State.ViewingProtectedCustomerDetailState ->
        viewingProtectedCustomerUiStateMachine.model(
          props =
            ViewingProtectedCustomerProps(
              screenModel = screenModel,
              protectedCustomer = currentState.protectedCustomer,
              onHelpWithRecovery = {
                props.onHelpWithRecovery(currentState.protectedCustomer)
              },
              onRemoveProtectedCustomer = {
                props.onRemoveProtectedCustomer(currentState.protectedCustomer)
              },
              onExit = { state = State.ViewingListState }
            )
        )
    }
  }
}

sealed interface State {
  data object ViewingListState : State

  data class ViewingProtectedCustomerDetailState(
    val protectedCustomer: ProtectedCustomer,
  ) : State
}
