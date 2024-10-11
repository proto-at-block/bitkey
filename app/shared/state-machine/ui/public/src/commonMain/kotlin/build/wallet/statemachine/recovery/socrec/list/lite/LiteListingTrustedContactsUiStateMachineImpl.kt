package build.wallet.statemachine.recovery.socrec.list.lite

import androidx.compose.runtime.*
import build.wallet.bitkey.relationships.ProtectedCustomer
import build.wallet.recovery.socrec.SocRecService
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.recovery.socrec.view.ViewingProtectedCustomerProps
import build.wallet.statemachine.recovery.socrec.view.ViewingProtectedCustomerUiStateMachine
import kotlinx.collections.immutable.toImmutableList

class LiteListingTrustedContactsUiStateMachineImpl(
  private val socRecService: SocRecService,
  private val viewingProtectedCustomerUiStateMachine: ViewingProtectedCustomerUiStateMachine,
) : LiteListingTrustedContactsUiStateMachine {
  @Composable
  override fun model(props: LiteListingTrustedContactsUiProps): ScreenModel {
    var state: State by remember { mutableStateOf(State.ViewingListState) }

    val protectedCustomers = socRecService.socRecRelationships.value?.protectedCustomers ?: emptyList()

    val screenModel = LiteTrustedContactsListBodyModel(
      onBackPressed = props.onExit,
      protectedCustomers = protectedCustomers.toImmutableList(),
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
              account = props.account,
              screenModel = screenModel,
              protectedCustomer = currentState.protectedCustomer,
              onHelpWithRecovery = {
                props.onHelpWithRecovery(currentState.protectedCustomer)
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
