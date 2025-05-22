package build.wallet.statemachine.account.create.lite

import androidx.compose.runtime.*
import bitkey.account.AccountConfigService
import bitkey.onboarding.CreateLiteAccountService
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId.*
import build.wallet.analytics.v1.Action
import build.wallet.bitkey.account.LiteAccount
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.router.Route
import build.wallet.router.Router
import build.wallet.statemachine.account.BeTrustedContactIntroductionModel
import build.wallet.statemachine.cloud.LiteAccountCloudSignInAndBackupProps
import build.wallet.statemachine.cloud.LiteAccountCloudSignInAndBackupUiStateMachine
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.ScreenPresentationStyle.Root
import build.wallet.statemachine.trustedcontact.TrustedContactEnrollmentUiProps
import build.wallet.statemachine.trustedcontact.TrustedContactEnrollmentUiStateMachine
import build.wallet.statemachine.trustedcontact.model.EnteringInviteCodeBodyModel
import build.wallet.statemachine.trustedcontact.model.TrustedContactFeatureVariant
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.flow.first

@BitkeyInject(ActivityScope::class)
class CreateLiteAccountUiStateMachineImpl(
  private val createLiteAccountService: CreateLiteAccountService,
  private val trustedContactEnrollmentUiStateMachine: TrustedContactEnrollmentUiStateMachine,
  private val liteAccountCloudSignInAndBackupUiStateMachine:
    LiteAccountCloudSignInAndBackupUiStateMachine,
  private val deviceInfoProvider: DeviceInfoProvider,
  private val eventTracker: EventTracker,
  private val accountConfigService: AccountConfigService,
) : CreateLiteAccountUiStateMachine {
  @Composable
  override fun model(props: CreateLiteAccountUiProps): ScreenModel {
    var inviteCode by remember { mutableStateOf(props.inviteCode) }

    var uiState: State by remember(inviteCode) {
      mutableStateOf(
        if (props.showBeTrustedContactIntroduction) {
          // If we are not skipping the introduction, show it.
          State.ShowingBeTrustedContactIntroduction(inviteCode ?: "")
        } else {
          if (inviteCode != null) {
            // If we are skipping the introduction, create the account immediately with invite code.
            State.CreatingLiteAccount(inviteCode!!)
          } else {
            // If there is no invite code, we need to enter one.
            State.EnteringInviteCode("")
          }
        }
      )
    }

    LaunchedEffect("deep-link-routing") {
      Router.onRouteChange { route ->
        when (route) {
          is Route.TrustedContactInvite -> {
            eventTracker.track(Action.ACTION_APP_SOCREC_ENTERED_INVITE_VIA_DEEPLINK)
            inviteCode = route.inviteCode
            return@onRouteChange true
          }
          is Route.BeneficiaryInvite -> {
            inviteCode = route.inviteCode
            return@onRouteChange true
          }
          else -> false
        }
      }
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
                // If the RC is tapping the continue button, it wasn't from a deep link
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
            ).toBackRetreat(),
          variant = TrustedContactFeatureVariant.Generic
        ).asRootScreen()
      }

      is State.CreatingLiteAccount -> {
        LaunchedEffect("create-account-on-f8e") {
          val defaultConfig = accountConfigService.defaultConfig().first()
          val accountConfig = defaultConfig.toLiteAccountConfig()
          createLiteAccountService
            .createAccount(accountConfig)
            .onSuccess { liteAccount ->
              uiState = State.BackingUpLiteAccount(
                liteAccount = liteAccount,
                inviteCode = state.inviteCode
              )
            }
            .onFailure { error ->
              uiState = State.CreatingLiteAccountFailure(
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
        trustedContactEnrollmentUiStateMachine.model(
          props =
            TrustedContactEnrollmentUiProps(
              retreat = props.onBack.toBackRetreat(),
              account = state.liteAccount,
              inviteCode = state.inviteCode,
              onDone = {
                props.onAccountCreated(it)
              },
              screenPresentationStyle = Root,
              variant = TrustedContactFeatureVariant.Generic
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
   * Introduction to being a Recovery Contact.
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

  /** The account creation process succeeded, we are now enrolling the account as a Recovery Contact */
  data class EnrollingAsTrustedContact(
    override val inviteCode: String,
    val liteAccount: LiteAccount,
  ) : State()
}
