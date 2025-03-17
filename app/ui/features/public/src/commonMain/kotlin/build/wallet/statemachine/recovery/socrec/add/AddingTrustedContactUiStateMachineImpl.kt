package build.wallet.statemachine.recovery.socrec.add

import androidx.compose.runtime.*
import bitkey.notifications.NotificationsService
import bitkey.notifications.NotificationsService.NotificationStatus
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.id.SocialRecoveryEventTrackerScreenId
import build.wallet.analytics.v1.Action
import build.wallet.bitkey.promotions.PromotionCode
import build.wallet.bitkey.relationships.OutgoingInvitation
import build.wallet.bitkey.relationships.TrustedContactAlias
import build.wallet.bitkey.relationships.TrustedContactRole
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.ktor.result.HttpError
import build.wallet.platform.clipboard.Clipboard
import build.wallet.platform.clipboard.plainTextItemAndroid
import build.wallet.platform.sharing.SharingManager
import build.wallet.platform.sharing.shareInvitation
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.relationships.RelationshipsService
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.auth.Request
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.input.NameInputBodyModel
import build.wallet.statemachine.notifications.TosInfo
import build.wallet.statemachine.recovery.RecoverySegment
import build.wallet.statemachine.recovery.socrec.add.AddingTrustedContactUiStateMachineImpl.State.*
import build.wallet.statemachine.settings.full.notifications.RecoveryChannelSettingsProps
import build.wallet.statemachine.settings.full.notifications.RecoveryChannelSettingsUiStateMachine
import build.wallet.statemachine.settings.full.notifications.Source
import build.wallet.statemachine.trustedcontact.PromoCodeUpsellUiProps
import build.wallet.statemachine.trustedcontact.PromoCodeUpsellUiStateMachine
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import com.github.michaelbull.result.mapBoth
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@BitkeyInject(ActivityScope::class)
class AddingTrustedContactUiStateMachineImpl(
  private val proofOfPossessionNfcStateMachine: ProofOfPossessionNfcStateMachine,
  private val relationshipsService: RelationshipsService,
  private val inAppBrowserNavigator: InAppBrowserNavigator,
  private val sharingManager: SharingManager,
  private val clipboard: Clipboard,
  private val eventTracker: EventTracker,
  private val promoCodeUpsellUiStateMachine: PromoCodeUpsellUiStateMachine,
  private val notificationChannelStateMachine: RecoveryChannelSettingsUiStateMachine,
  private val notificationsService: NotificationsService,
) : AddingTrustedContactUiStateMachine {
  @Composable
  @Suppress("CyclomaticComplexMethod")
  override fun model(props: AddingTrustedContactUiProps): ScreenModel {
    val isInheritance by remember(props.trustedContactRole) {
      derivedStateOf { props.trustedContactRole == TrustedContactRole.Beneficiary }
    }
    val scope = rememberStableCoroutineScope()

    var state: State by remember {
      mutableStateOf(
        if (isInheritance) {
          InheritanceSetup
        } else {
          EnterTcNameState()
        }
      )
    }

    var promoCode: PromotionCode? by remember { mutableStateOf(null) }

    return when (val current = state) {
      InheritanceSetup -> {
        InheritanceInviteSetupBodyModel(
          onBack = {
            props.onExit()
          },
          onContinue = {
            state = InheritanceExplainer
          },
          learnMore = {
            state = InheritanceLearnMore(InheritanceSetup)
          }
        ).asModalScreen()
      }

      InheritanceExplainer -> {
        InheritanceInviteExplainerBodyModel(
          onBack = {
            state = InheritanceSetup
          },
          onContinue = {
            state = EnterTcNameState()
          },
          learnMore = {
            state = InheritanceLearnMore(InheritanceExplainer)
          }
        ).asModalScreen()
      }

      is InheritanceLearnMore -> {
        InAppBrowserModel(
          open = {
            inAppBrowserNavigator.open(
              url = current.previous.link,
              onClose = {
                state = current.previous
              }
            )
          }
        ).asModalScreen()
      }

      is EnterTcNameState -> {
        var input by remember { mutableStateOf(current.tcNameInitial) }
        val contactType = if (isInheritance) "beneficiary" else "trusted contact"

        val continueClick = remember(input) {
          StandardClick {
            if (input.isNotBlank()) {
              val cleanedName = input.trim()
              state = if (isInheritance) {
                LoadingNotificationPermissions(tcName = cleanedName)
              } else {
                SaveWithBitkeyRequestState(tcName = cleanedName)
              }
            }
          }
        }

        NameInputBodyModel(
          id = if (isInheritance) {
            SocialRecoveryEventTrackerScreenId.TC_BENEFICIARY_ADD_TC_NAME
          } else {
            SocialRecoveryEventTrackerScreenId.TC_ADD_TC_NAME
          },
          title = "Add your $contactType's name",
          subline = "Add a name, or nickname, to help you recognize your $contactType in the app.",
          value = input,
          primaryButton = ButtonModel(
            text = "Continue",
            isEnabled = input.isNotBlank(),
            onClick = continueClick,
            size = ButtonModel.Size.Footer
          ),
          onValueChange = { input = it },
          onClose = {
            if (isInheritance) {
              state = InheritanceExplainer
            } else {
              props.onExit()
            }
          },
          hasPreviousScreen = isInheritance
        ).asModalScreen()
      }

      is LoadingNotificationPermissions -> {
        LaunchedEffect(current) {
          val result = notificationsService
            .getCriticalNotificationStatus(
              accountId = props.account.accountId
            ).first()

          state = when (result) {
            NotificationStatus.Enabled ->
              SaveWithBitkeyRequestState(tcName = current.tcName)
            is NotificationStatus.Missing ->
              RequestingNotificationPermissions(tcName = current.tcName)
            is NotificationStatus.Error ->
              NotificationPermissionsLoadError(
                cause = result.cause,
                tcName = current.tcName
              )
          }
        }
        LoadingBodyModel(
          id = null,
          onBack = { state = EnterTcNameState(tcNameInitial = current.tcName) }
        ).asModalScreen()
      }

      is NotificationPermissionsLoadError -> {
        ErrorFormBodyModel(
          eventTrackerScreenId = null,
          errorData = ErrorData(
            segment = RecoverySegment.SocRec.ProtectedCustomer.Setup,
            actionDescription = "Loading Permissions before adding beneficiary",
            cause = current.cause
          ),
          title = "Error Loading Notification Preferences",
          onBack = { state = EnterTcNameState(tcNameInitial = current.tcName) },
          primaryButton = ButtonDataModel(
            text = "Skip",
            onClick = { state = SaveWithBitkeyRequestState(tcName = current.tcName) }
          ),
          secondaryButton = ButtonDataModel(
            text = "Cancel",
            onClick = { state = EnterTcNameState(tcNameInitial = current.tcName) }
          )
        ).asModalScreen()
      }

      is RequestingNotificationPermissions -> {
        notificationChannelStateMachine.model(
          props = RecoveryChannelSettingsProps(
            onContinue = { state = SaveWithBitkeyRequestState(tcName = current.tcName) },
            source = Source.InheritanceStartClaim,
            account = props.account,
            onBack = { state = EnterTcNameState(tcNameInitial = current.tcName) }
          )
        )
      }

      is SaveWithBitkeyRequestState ->
        SaveContactBodyModel(
          trustedContactName = current.tcName,
          isBeneficiary = isInheritance,
          onSave = {
            state = if (!isInheritance || current.termsAgree) {
              ScanningHardwareState(tcName = current.tcName)
            } else {
              SaveWithBitkeyRequestState(tcName = current.tcName, termsError = true)
            }
          },
          onBackPressed = {
            state =
              EnterTcNameState(
                tcNameInitial = current.tcName
              )
          },
          tosInfo = TosInfo(
            tosLink = {
              inAppBrowserNavigator.open(
                url = "https://bitkey.world/en-US/legal/terms-of-service",
                onClose = {}
              )
            },
            privacyLink = {},
            onTermsAgreeToggle = { isChecked ->
              state = SaveWithBitkeyRequestState(
                tcName = current.tcName,
                termsAgree = isChecked,
                termsError = if (isChecked) false else current.termsError
              )
            },
            termsAgree = current.termsAgree
          ),
          termsError = current.termsError
        ).asModalScreen()

      is ScanningHardwareState ->
        proofOfPossessionNfcStateMachine.model(
          ProofOfPossessionNfcProps(
            request =
              Request.HwKeyProof(
                onSuccess = { proof ->
                  state =
                    SavingWithBitkeyState(
                      proofOfPossession = proof,
                      tcName = current.tcName
                    )
                }
              ),
            fullAccountId = props.account.accountId,
            onBack = {
              state =
                SaveWithBitkeyRequestState(
                  tcName = current.tcName
                )
            },
            screenPresentationStyle = ScreenPresentationStyle.Modal
          )
        )

      is SavingWithBitkeyState -> {
        LaunchedEffect("save-tc-to-bitkey") {
          props.onAddTc(
            TrustedContactAlias(current.tcName),
            current.proofOfPossession
          ).onSuccess { result ->
            if (!isInheritance) {
              state = ShareState(invitation = result)
            } else {
              val promoResult = relationshipsService.retrieveInvitationPromotionCode(
                props.account,
                result.inviteCode
              )
              state = promoResult.mapBoth(
                success = {
                  promoCode = promoResult.value
                  ShareState(invitation = result)
                },
                failure = {
                  ShareState(invitation = result)
                }
              )
            }
          }.onFailure {
            state = FailedToSaveState(
              proofOfPossession = current.proofOfPossession,
              error = it,
              tcName = current.tcName
            )
          }
        }.let {
          LoadingBodyModel(
            id = null
          ).asModalScreen()
        }
      }

      is FailedToSaveState ->
        NetworkErrorFormBodyModel(
          eventTrackerScreenId = null,
          title = "Unable to save contact",
          isConnectivityError = current.error is HttpError.NetworkError,
          errorData = ErrorData(
            segment = RecoverySegment.SocRec.ProtectedCustomer.Setup,
            actionDescription = "Saving Trusted contact to F8e",
            cause = current.error
          ),
          onRetry = {
            state =
              SavingWithBitkeyState(
                proofOfPossession = current.proofOfPossession,
                tcName = current.tcName
              )
          },
          onBack = {
            state =
              SaveWithBitkeyRequestState(
                tcName = current.tcName
              )
          }
        ).asModalScreen()

      is ShareState ->
        ShareInviteBodyModel(
          trustedContactName = current.invitation.invitation.trustedContactAlias.alias,
          isBeneficiary = isInheritance,
          onShareComplete = {
            // We need to watch the clipboard on Android because we don't get
            // a callback from the share sheet when they use the copy action
            scope.launch {
              clipboard.plainTextItemAndroid().drop(1).collect { content ->
                content.let {
                  if (it.toString().contains(current.invitation.inviteCode)) {
                    state =
                      Success(contactAlias = current.invitation.invitation.trustedContactAlias)
                  }
                }
              }
            }

            sharingManager.shareInvitation(
              inviteCode = current.invitation.inviteCode,
              isBeneficiary = isInheritance,
              onCompletion = {
                state = Success(contactAlias = current.invitation.invitation.trustedContactAlias)
              }, onFailure = {
                eventTracker.track(Action.ACTION_APP_SOCREC_TC_INVITE_DISMISSED_SHEET_WITHOUT_SHARING)
              }
            )
          },
          onBackPressed = {
            // Complete flow without sharing, since invitation is already created:
            props.onInvitationShared()
          }
        ).asModalScreen()

      is State.ShowingPromoCodeUpsell -> {
        promoCodeUpsellUiStateMachine.model(
          PromoCodeUpsellUiProps(
            promoCode = current.promoCode,
            contactAlias = current.contactAlias.alias,
            onExit = props.onInvitationShared
          )
        )
      }

      is Success -> {
        SuccessBodyModel(
          id = if (isInheritance) {
            SocialRecoveryEventTrackerScreenId.TC_BENEFICIARY_ENROLLMENT_INVITE_SENT
          } else {
            SocialRecoveryEventTrackerScreenId.TC_ENROLLMENT_INVITE_SENT
          },
          primaryButtonModel = ButtonDataModel(
            text = "Got it",
            onClick = {
              promoCode?.let {
                state =
                  State.ShowingPromoCodeUpsell(promoCode = it, contactAlias = current.contactAlias)
              } ?: props.onInvitationShared.invoke()
            }
          ),
          title = "You're all set",
          message = if (isInheritance) {
            "We'll let you know when your contact accepts their invite."
          } else {
            "You can manage your trusted contacts in your settings."
          }
        ).asModalScreen()
      }
    }
  }

  private sealed interface State {
    sealed interface WithHelpCenterLink : State {
      /**
       * Help Center link used in this screen.
       */
      val link: String
    }

    /**
     * Setup instructions for inheritance invites only.
     */
    data object InheritanceSetup : State, WithHelpCenterLink {
      override val link: String = "https://bitkey.world/hc/set-up-inheritance"
    }

    /**
     * Explainer for inheritance process only.
     */
    data object InheritanceExplainer : State, WithHelpCenterLink {
      override val link: String = "https://bitkey.world/hc/initiate-claim"
    }

    /**
     * Learn more browser help page for inheritance.
     */
    data class InheritanceLearnMore(
      val previous: WithHelpCenterLink,
    ) : State

    data class EnterTcNameState(
      val tcNameInitial: String = "",
    ) : State

    data class LoadingNotificationPermissions(
      val tcName: String,
    ) : State

    data class NotificationPermissionsLoadError(
      val cause: Throwable,
      val tcName: String,
    ) : State

    data class RequestingNotificationPermissions(
      val tcName: String,
    ) : State

    data class SaveWithBitkeyRequestState(
      val tcName: String,
      val termsAgree: Boolean = false,
      val termsError: Boolean = false,
    ) : State

    data class ScanningHardwareState(
      val tcName: String,
    ) : State

    data class SavingWithBitkeyState(
      val proofOfPossession: HwFactorProofOfPossession,
      val tcName: String,
    ) : State

    data class FailedToSaveState(
      val proofOfPossession: HwFactorProofOfPossession,
      val error: Error,
      val tcName: String,
    ) : State

    data class ShareState(
      val invitation: OutgoingInvitation,
    ) : State

    data class ShowingPromoCodeUpsell(
      val promoCode: PromotionCode,
      val contactAlias: TrustedContactAlias,
    ) : State

    data class Success(
      val contactAlias: TrustedContactAlias,
    ) : State
  }
}
