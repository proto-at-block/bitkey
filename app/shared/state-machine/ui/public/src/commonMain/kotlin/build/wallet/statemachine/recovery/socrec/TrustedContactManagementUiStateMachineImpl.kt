package build.wallet.statemachine.recovery.socrec

import androidx.compose.runtime.*
import build.wallet.bitkey.relationships.TrustedContactRole
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.relationships.RelationshipsService
import build.wallet.statemachine.account.BeTrustedContactIntroductionModel
import build.wallet.statemachine.core.Retreat
import build.wallet.statemachine.core.RetreatStyle
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.recovery.socrec.TrustedContactManagementUiStateMachineImpl.State.AddingTrustedContactState
import build.wallet.statemachine.recovery.socrec.TrustedContactManagementUiStateMachineImpl.State.ListingContactsState
import build.wallet.statemachine.recovery.socrec.add.AddingTrustedContactUiProps
import build.wallet.statemachine.recovery.socrec.add.AddingTrustedContactUiStateMachine
import build.wallet.statemachine.recovery.socrec.list.full.ListingTrustedContactsUiProps
import build.wallet.statemachine.recovery.socrec.list.full.ListingTrustedContactsUiStateMachine
import build.wallet.statemachine.trustedcontact.TrustedContactEnrollmentUiProps
import build.wallet.statemachine.trustedcontact.TrustedContactEnrollmentUiStateMachine

class TrustedContactManagementUiStateMachineImpl(
  private val listingTrustedContactsUiStateMachine: ListingTrustedContactsUiStateMachine,
  private val addingTrustedContactUiStateMachine: AddingTrustedContactUiStateMachine,
  private val trustedContactEnrollmentUiStateMachine: TrustedContactEnrollmentUiStateMachine,
  private val deviceInfoProvider: DeviceInfoProvider,
  private val relationshipsService: RelationshipsService,
) : TrustedContactManagementUiStateMachine {
  @Composable
  override fun model(props: TrustedContactManagementProps): ScreenModel {
    var state: State by remember { mutableStateOf(ListingContactsState) }

    return when (state) {
      ListingContactsState ->
        listingTrustedContactsUiStateMachine.model(
          ListingTrustedContactsUiProps(
            account = props.account,
            onAddTCButtonPressed = {
              state = AddingTrustedContactState
            },
            onAcceptTrustedContactInvite = { state = State.EnrollingAsTrustedContact },
            onExit = props.onExit
          )
        )

      AddingTrustedContactState ->
        addingTrustedContactUiStateMachine.model(
          AddingTrustedContactUiProps(
            account = props.account,
            onAddTc = { tcAlias, hardwareProofOfPossession ->
              relationshipsService.createInvitation(
                account = props.account,
                trustedContactAlias = tcAlias,
                hardwareProofOfPossession = hardwareProofOfPossession,
                roles = setOf(TrustedContactRole.SocialRecoveryContact)
              )
            },
            onInvitationShared = {
              state = ListingContactsState
            },
            onExit = {
              state = ListingContactsState
            }
          )
        )

      State.BeTrustedContactIntroduction -> {
        BeTrustedContactIntroductionModel(
          devicePlatform = deviceInfoProvider.getDeviceInfo().devicePlatform,
          onContinue = { state = State.EnrollingAsTrustedContact },
          onBack = { state = ListingContactsState }
        ).asModalScreen()
      }

      State.EnrollingAsTrustedContact ->
        trustedContactEnrollmentUiStateMachine.model(
          TrustedContactEnrollmentUiProps(
            retreat =
              Retreat(
                style = RetreatStyle.Close,
                onRetreat = { state = ListingContactsState }
              ),
            account = props.account,
            inviteCode = props.inviteCode,
            screenPresentationStyle = ScreenPresentationStyle.Modal,
            onDone = { state = ListingContactsState }
          )
        )
    }
  }

  private sealed interface State {
    data object ListingContactsState : State

    data object AddingTrustedContactState : State

    data object BeTrustedContactIntroduction : State

    data object EnrollingAsTrustedContact : State
  }
}
