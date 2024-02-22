package build.wallet.statemachine.trustedcontact

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_LOAD_KEY
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E
import build.wallet.bitkey.socrec.Invitation
import build.wallet.bitkey.socrec.ProtectedCustomer
import build.wallet.bitkey.socrec.ProtectedCustomerAlias
import build.wallet.bitkey.socrec.TrustedContactIdentityKey
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.AcceptTrustedContactInvitationErrorCode
import build.wallet.f8e.error.code.RetrieveTrustedContactInvitationErrorCode
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.recovery.socrec.SocRecKeyError
import build.wallet.recovery.socrec.SocRecKeysRepository
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.Retreat
import build.wallet.statemachine.core.RetreatStyle
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.trustedcontact.model.AcceptingInviteWithF8eFailureBodyModel
import build.wallet.statemachine.trustedcontact.model.AcceptingInviteWithF8eSuccessBodyModel
import build.wallet.statemachine.trustedcontact.model.EnteringInviteCodeBodyModel
import build.wallet.statemachine.trustedcontact.model.EnteringProtectedCustomerNameBodyModel
import build.wallet.statemachine.trustedcontact.model.LoadKeyFailureBodyModel
import build.wallet.statemachine.trustedcontact.model.RetrievingInviteWithF8eFailureBodyModel
import build.wallet.ui.model.Click
import build.wallet.ui.model.button.ButtonModel
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

class TrustedContactEnrollmentUiStateMachineImpl(
  private val socRecKeysRepository: SocRecKeysRepository,
  private val deviceInfoProvider: DeviceInfoProvider,
) : TrustedContactEnrollmentUiStateMachine {
  @Composable
  override fun model(props: TrustedContactEnrollmentUiProps): ScreenModel {
    var uiState: State by remember {
      if (props.inviteCode != null) {
        mutableStateOf(State.RetrievingInviteWithF8e(props.inviteCode))
      } else {
        mutableStateOf(State.EnteringInviteCode)
      }
    }

    return when (val state = uiState) {
      is State.EnteringInviteCode -> {
        var value by remember { mutableStateOf("") }
        EnteringInviteCodeBodyModel(
          value = value,
          onValueChange = { value = it },
          primaryButton =
            ButtonModel(
              text = "Continue",
              isEnabled = value.isNotEmpty(),
              size = ButtonModel.Size.Footer,
              onClick =
                Click.standardClick {
                  uiState = State.RetrievingInviteWithF8e(value)
                }
            ),
          retreat = props.retreat
        )
      }

      is State.RetrievingInviteWithF8e -> {
        LaunchedEffect("retrieve-invitation") {
          props.retrieveInvitation(state.inviteCode)
            .onFailure {
              uiState =
                State.RetrievingInviteWithF8eFailure(
                  error = it,
                  inviteCode = state.inviteCode
                )
            }
            .onSuccess {
              uiState = State.EnteringProtectedCustomerName(it)
            }
        }
        LoadingBodyModel(id = TC_ENROLLMENT_RETRIEVE_INVITE_FROM_F8E)
      }

      is State.RetrievingInviteWithF8eFailure ->
        RetrievingInviteWithF8eFailureBodyModel(
          error = state.error,
          onRetry = { uiState = State.RetrievingInviteWithF8e(state.inviteCode) },
          onBack = { uiState = State.EnteringInviteCode }
        )

      is State.EnteringProtectedCustomerName -> {
        var value by remember { mutableStateOf("") }
        EnteringProtectedCustomerNameBodyModel(
          value = value,
          onValueChange = { value = it },
          primaryButton =
            ButtonModel(
              text = "Continue",
              isEnabled = value.isNotEmpty(),
              size = ButtonModel.Size.Footer,
              onClick =
                Click.standardClick {
                  uiState =
                    State.LoadIdentityKey(state.invitation, ProtectedCustomerAlias(value))
                }
            ),
          retreat =
            Retreat(
              style = RetreatStyle.Back,
              onRetreat = { uiState = State.EnteringInviteCode }
            )
        )
      }

      is State.LoadIdentityKey -> {
        LaunchedEffect("load-keys") {
          socRecKeysRepository.getOrCreateKey(::TrustedContactIdentityKey)
            .onSuccess { key ->
              uiState = State.AcceptingInviteWithF8e(state.invitation, state.protectedCustomerAlias, key)
            }
            .onFailure {
              uiState = State.LoadIdentityKeyFailure(state.invitation, state.protectedCustomerAlias, it)
            }
        }
        LoadingBodyModel(id = TC_ENROLLMENT_LOAD_KEY)
      }

      is State.LoadIdentityKeyFailure ->
        LoadKeyFailureBodyModel(
          onBack = { uiState = State.EnteringProtectedCustomerName(state.invitation) },
          onRetry = { uiState = State.LoadIdentityKey(state.invitation, state.protectedCustomerAlias) }
        )

      is State.AcceptingInviteWithF8e -> {
        LaunchedEffect("retrieve-invitation") {
          props.acceptInvitation(
            state.invitation,
            state.protectedCustomerAlias,
            state.trustedContactIdentityKey
          ).onFailure {
            uiState =
              State.AcceptingInviteWithF8eFailure(
                error = it,
                invitation = state.invitation,
                protectedCustomerAlias = state.protectedCustomerAlias,
                trustedContactIdentityKey = state.trustedContactIdentityKey
              )
          }
            .onSuccess {
              uiState = State.AcceptingInviteWithF8eSuccess(it)
            }
        }
        LoadingBodyModel(id = TC_ENROLLMENT_ACCEPT_INVITE_WITH_F8E)
      }

      is State.AcceptingInviteWithF8eFailure ->
        AcceptingInviteWithF8eFailureBodyModel(
          error = state.error,
          onRetry = {
            uiState = State.AcceptingInviteWithF8e(state.invitation, state.protectedCustomerAlias, state.trustedContactIdentityKey)
          },
          devicePlatform = deviceInfoProvider.getDeviceInfo().devicePlatform,
          onBack = { uiState = State.EnteringProtectedCustomerName(state.invitation) }
        )

      is State.AcceptingInviteWithF8eSuccess -> {
        AcceptingInviteWithF8eSuccessBodyModel(
          protectedCustomer = state.protectedCustomer,
          onDone = props.onDone
        )
      }
    }.asScreen(props.screenPresentationStyle)
  }
}

private sealed interface State {
  /**
   * The user is entering an invite code.
   * Optional step, omitted if the app is launched from a deeplink with an invite code embedded
   */
  data object EnteringInviteCode : State

  /** Server call to f8e to retrieve the invite data from the code */
  data class RetrievingInviteWithF8e(val inviteCode: String) : State

  /** Server call to f8e to retrieve the invite data from the code failed */
  data class RetrievingInviteWithF8eFailure(
    val error: F8eError<RetrieveTrustedContactInvitationErrorCode>,
    val inviteCode: String,
  ) : State

  /** The user is entering the name of the customer they will be protecting */
  data class EnteringProtectedCustomerName(
    val invitation: Invitation,
  ) : State

  /** Call to load the [TrustedContactIdentityKey] */
  data class LoadIdentityKey(
    val invitation: Invitation,
    val protectedCustomerAlias: ProtectedCustomerAlias,
  ) : State

  /** Failed to load the [TrustedContactIdentityKey] */
  data class LoadIdentityKeyFailure(
    val invitation: Invitation,
    val protectedCustomerAlias: ProtectedCustomerAlias,
    val socRecKeyError: SocRecKeyError,
  ) : State

  /** Server call to f8e to accept the invite */
  data class AcceptingInviteWithF8e(
    val invitation: Invitation,
    val protectedCustomerAlias: ProtectedCustomerAlias,
    val trustedContactIdentityKey: TrustedContactIdentityKey,
  ) : State

  /** Server call to f8e to retrieve the invite data from the code failed */
  data class AcceptingInviteWithF8eFailure(
    val error: F8eError<AcceptTrustedContactInvitationErrorCode>,
    val invitation: Invitation,
    val protectedCustomerAlias: ProtectedCustomerAlias,
    val trustedContactIdentityKey: TrustedContactIdentityKey,
  ) : State

  /** Screen shown when enrolling as a Trusted Contact succeeded, after accepting the invite. */
  data class AcceptingInviteWithF8eSuccess(
    val protectedCustomer: ProtectedCustomer,
  ) : State
}
