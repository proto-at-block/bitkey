package build.wallet.statemachine.recovery.socrec

import androidx.compose.runtime.*
import bitkey.ui.framework.Navigator
import bitkey.ui.framework.Screen
import bitkey.ui.framework.ScreenPresenter
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.relationships.TrustedContactRole
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.relationships.RelationshipsService
import build.wallet.statemachine.account.BeTrustedContactIntroductionModel
import build.wallet.statemachine.core.Retreat
import build.wallet.statemachine.core.RetreatStyle
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.recovery.socrec.TrustedContactManagementPresenter.State.AddingTrustedContactState
import build.wallet.statemachine.recovery.socrec.TrustedContactManagementPresenter.State.ListingContactsState
import build.wallet.statemachine.recovery.socrec.add.AddingTrustedContactUiProps
import build.wallet.statemachine.recovery.socrec.add.AddingTrustedContactUiStateMachine
import build.wallet.statemachine.recovery.socrec.list.full.ListingTrustedContactsUiProps
import build.wallet.statemachine.recovery.socrec.list.full.ListingTrustedContactsUiStateMachine
import build.wallet.statemachine.trustedcontact.TrustedContactEnrollmentUiProps
import build.wallet.statemachine.trustedcontact.TrustedContactEnrollmentUiStateMachine
import build.wallet.statemachine.trustedcontact.model.TrustedContactFeatureVariant

data class TrustedContactManagementScreen(
  val account: FullAccount,
  val onExit: () -> Unit,
  val inviteCode: String? = null,
) : Screen

@BitkeyInject(ActivityScope::class)
class TrustedContactManagementPresenter(
  private val listingTrustedContactsUiStateMachine: ListingTrustedContactsUiStateMachine,
  private val addingTrustedContactUiStateMachine: AddingTrustedContactUiStateMachine,
  private val trustedContactEnrollmentUiStateMachine: TrustedContactEnrollmentUiStateMachine,
  private val deviceInfoProvider: DeviceInfoProvider,
  private val relationshipsService: RelationshipsService,
) : ScreenPresenter<TrustedContactManagementScreen> {
  @Composable
  override fun model(
    navigator: Navigator,
    screen: TrustedContactManagementScreen,
  ): ScreenModel {
    var state: State by remember { mutableStateOf(ListingContactsState) }

    return when (state) {
      ListingContactsState ->
        listingTrustedContactsUiStateMachine.model(
          ListingTrustedContactsUiProps(
            account = screen.account,
            onAddTCButtonPressed = {
              state = AddingTrustedContactState
            },
            onAcceptTrustedContactInvite = { state = State.EnrollingAsTrustedContact },
            onExit = screen.onExit
          )
        )

      AddingTrustedContactState ->
        addingTrustedContactUiStateMachine.model(
          AddingTrustedContactUiProps(
            account = screen.account,
            onAddTc = { tcAlias, hardwareProofOfPossession ->
              relationshipsService.createInvitation(
                account = screen.account,
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
            account = screen.account,
            inviteCode = screen.inviteCode,
            screenPresentationStyle = ScreenPresentationStyle.Modal,
            onDone = { state = ListingContactsState },
            variant = TrustedContactFeatureVariant.Direct(
              target = TrustedContactFeatureVariant.Feature.Recovery
            )
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
