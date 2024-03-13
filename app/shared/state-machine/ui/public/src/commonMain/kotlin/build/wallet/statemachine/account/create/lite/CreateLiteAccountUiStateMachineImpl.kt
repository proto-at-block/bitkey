package build.wallet.statemachine.account.create.lite

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId.NEW_LITE_ACCOUNT_BACKUP_FAILURE
import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId.NEW_LITE_ACCOUNT_CREATION
import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId.NEW_LITE_ACCOUNT_CREATION_FAILURE
import build.wallet.analytics.v1.Action
import build.wallet.auth.LiteAccountCreator
import build.wallet.bitkey.account.LiteAccount
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.recovery.socrec.SocRecRelationshipsRepository
import build.wallet.statemachine.account.BeTrustedContactIntroductionModel
import build.wallet.statemachine.cloud.LiteAccountCloudSignInAndBackupProps
import build.wallet.statemachine.cloud.LiteAccountCloudSignInAndBackupUiStateMachine
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.NetworkErrorFormBodyModel
import build.wallet.statemachine.core.Retreat
import build.wallet.statemachine.core.RetreatStyle
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle.Root
import build.wallet.statemachine.trustedcontact.TrustedContactEnrollmentUiProps
import build.wallet.statemachine.trustedcontact.TrustedContactEnrollmentUiStateMachine
import build.wallet.statemachine.trustedcontact.model.EnteringInviteCodeBodyModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

class CreateLiteAccountUiStateMachineImpl(
  private val liteAccountCreator: LiteAccountCreator,
  private val trustedContactEnrollmentUiStateMachine: TrustedContactEnrollmentUiStateMachine,
  private val socRecRelationshipsRepository: SocRecRelationshipsRepository,
  private val liteAccountCloudSignInAndBackupUiStateMachine:
    LiteAccountCloudSignInAndBackupUiStateMachine,
  private val deviceInfoProvider: DeviceInfoProvider,
  private val eventTracker: EventTracker,
) : CreateLiteAccountUiStateMachine {
  @Composable
  override fun model(props: CreateLiteAccountUiProps): ScreenModel {
    var uiState: State by remember(props.inviteCode) {
      mutableStateOf(
        if (props.showBeTrustedContactIntroduction) {
          // If we are not skipping the introduction, show it.
          State.ShowingBeTrustedContactIntroduction(props.inviteCode ?: "")
        } else {
          if (props.inviteCode != null) {
            // If we are skipping the introduction, create the account immediately with invite code.
            State.CreatingLiteAccount(props.inviteCode)
          } else {
            // If there is no invite code, we need to enter one.
            State.EnteringInviteCode("")
          }
        }
      )
    }

    return when (val state = uiState) {
      is State.ShowingBeTrustedContactIntroduction -> {
        BeTrustedContactIntroductionModel(
          onBack = props.onBack,
          onContinue = {
            // Got here from a deeplink, skip entering the code and go straight to create account
            if (state.inviteCode.isNotEmpty()) {
              uiState = State.CreatingLiteAccount(state.inviteCode)
            } else {
              uiState = State.EnteringInviteCode(state.inviteCode)
            }
          },
          devicePlatform = deviceInfoProvider.getDeviceInfo().devicePlatform
        ).asRootScreen()
      }

      is State.EnteringInviteCode -> {
        var value by remember { mutableStateOf(state.inviteCode) }
        EnteringInviteCodeBodyModel(
          value = value,
          onValueChange = { value = it },
          primaryButton =
            ButtonModel(
              text = "Continue",
              isEnabled = value.isNotEmpty(),
              size = ButtonModel.Size.Footer,
              onClick = StandardClick {
                // If the TC is tapping the continue button, it wasn't from a deep link
                // but rather manually entered.
                eventTracker.track(Action.ACTION_APP_SOCREC_ENTERED_INVITE_MANUALLY)
                uiState = State.CreatingLiteAccount(value)
              }
            ),
          retreat =
            (
              if (props.showBeTrustedContactIntroduction) {
                {
                  uiState = State.ShowingBeTrustedContactIntroduction(value)
                }
              } else {
                props.onBack
              }
            ).toBackRetreat()
        ).asRootScreen()
      }

      is State.CreatingLiteAccount -> {
        LaunchedEffect("create-account-on-f8e") {
          liteAccountCreator
            .createAccount(props.accountConfig)
            .onSuccess { liteAccount ->
              uiState =
                State.BackingUpLiteAccount(
                  liteAccount = liteAccount,
                  inviteCode = state.inviteCode
                )
            }
            .onFailure { error ->
              uiState =
                State.CreatingLiteAccountFailure(
                  isConnectivityError = error.isConnectivityError,
                  inviteCode = state.inviteCode
                )
            }
        }
        LoadingBodyModel(id = NEW_LITE_ACCOUNT_CREATION)
          .asRootScreen()
      }

      is State.CreatingLiteAccountFailure ->
        NetworkErrorFormBodyModel(
          title = "We couldn’t create your account",
          isConnectivityError = state.isConnectivityError,
          onRetry =
            if (state.isConnectivityError) {
              {
                uiState = State.CreatingLiteAccount(state.inviteCode)
              }
            } else {
              null
            },
          onBack = { uiState = State.EnteringInviteCode(state.inviteCode) },
          eventTrackerScreenId = NEW_LITE_ACCOUNT_CREATION_FAILURE
        ).asRootScreen()

      is State.BackingUpLiteAccount ->
        liteAccountCloudSignInAndBackupUiStateMachine.model(
          props =
            LiteAccountCloudSignInAndBackupProps(
              liteAccount = state.liteAccount,
              onBackupFailed = {
                uiState =
                  State.BackingUpLiteAccountError(
                    liteAccount = state.liteAccount,
                    inviteCode = state.inviteCode
                  )
              },
              onBackupSaved = {
                uiState =
                  State.EnrollingAsTrustedContact(
                    liteAccount = state.liteAccount,
                    inviteCode = state.inviteCode
                  )
              },
              presentationStyle = Root
            )
        )

      is State.BackingUpLiteAccountError ->
        ErrorFormBodyModel(
          title = "We were unable to upload backup",
          subline = "Please try again.",
          toolbar = ToolbarModel(leadingAccessory = BackAccessory(props.onBack)),
          primaryButton =
            ButtonDataModel(
              text = "Retry",
              onClick = {
                uiState =
                  State.BackingUpLiteAccount(
                    liteAccount = state.liteAccount,
                    inviteCode = state.inviteCode
                  )
              }
            ),
          onBack = props.onBack,
          eventTrackerScreenId = NEW_LITE_ACCOUNT_BACKUP_FAILURE
        ).asScreen(Root)

      is State.EnrollingAsTrustedContact -> {
        val socRecLiteAccountActions =
          socRecRelationshipsRepository.toActions(state.liteAccount)
        trustedContactEnrollmentUiStateMachine.model(
          props =
            TrustedContactEnrollmentUiProps(
              retreat = props.onBack.toBackRetreat(),
              account = state.liteAccount,
              acceptInvitation = socRecLiteAccountActions::acceptInvitation,
              retrieveInvitation = socRecLiteAccountActions::retrieveInvitation,
              inviteCode = state.inviteCode,
              onDone = {
                props.onAccountCreated(state.liteAccount)
              },
              screenPresentationStyle = Root
            )
        )
      }
    }
  }
}

private fun (() -> Unit).toBackRetreat() = Retreat(style = RetreatStyle.Back, onRetreat = this)

private sealed class State {
  /**
   * Maintain the invite code when going "back" as well as the period between
   * [EnteringInviteCode] and [EnrollingAsTrustedContact].
   */
  abstract val inviteCode: String

  /**
   * Introduction to being a trusted contact.
   */
  data class ShowingBeTrustedContactIntroduction(
    override val inviteCode: String,
  ) : State()

  /**
   * Enter invite code.
   *
   * This is separate from the [TrustedContactEnrollmentUiStateMachine] so [CreatingLiteAccount]
   * and [BackingUpLiteAccount] can occur between [EnteringInviteCode]
   * and [EnrollingAsTrustedContact].
   */
  data class EnteringInviteCode(
    override val inviteCode: String,
  ) : State()

  /** We are generating keys and creating the lite account on the server */
  data class CreatingLiteAccount(
    override val inviteCode: String,
  ) : State()

  /** Create and upload cloud backup. */
  data class BackingUpLiteAccount(
    override val inviteCode: String,
    val liteAccount: LiteAccount,
  ) : State()

  /** Handle backup error. */
  data class BackingUpLiteAccountError(
    override val inviteCode: String,
    val liteAccount: LiteAccount,
  ) : State()

  /** Some part of the account creation process failed */
  data class CreatingLiteAccountFailure(
    val isConnectivityError: Boolean,
    override val inviteCode: String,
  ) : State()

  /** The account creation process succeeded, we are now enrolling the account as a Trusted Contact */
  data class EnrollingAsTrustedContact(
    override val inviteCode: String,
    val liteAccount: LiteAccount,
  ) : State()
}
