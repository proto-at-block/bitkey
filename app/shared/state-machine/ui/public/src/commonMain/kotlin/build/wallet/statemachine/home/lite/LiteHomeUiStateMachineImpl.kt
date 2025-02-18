package build.wallet.statemachine.home.lite

import androidx.compose.runtime.*
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.v1.Action
import build.wallet.bitkey.account.FullAccount
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.onboarding.CreateFullAccountContext
import build.wallet.router.Route
import build.wallet.router.Router
import build.wallet.statemachine.account.create.full.CreateAccountUiProps
import build.wallet.statemachine.account.create.full.CreateAccountUiStateMachine
import build.wallet.statemachine.core.Retreat
import build.wallet.statemachine.core.RetreatStyle
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.home.lite.HomeScreen.MoneyHome
import build.wallet.statemachine.home.lite.HomeScreen.Settings
import build.wallet.statemachine.home.lite.PresentedScreen.AddTrustedContact
import build.wallet.statemachine.moneyhome.lite.LiteMoneyHomeUiProps
import build.wallet.statemachine.moneyhome.lite.LiteMoneyHomeUiStateMachine
import build.wallet.statemachine.recovery.socrec.LiteTrustedContactManagementProps.AcceptInvite
import build.wallet.statemachine.settings.lite.LiteSettingsHomeUiProps
import build.wallet.statemachine.settings.lite.LiteSettingsHomeUiStateMachine
import build.wallet.statemachine.status.HomeStatusBannerUiProps
import build.wallet.statemachine.status.HomeStatusBannerUiStateMachine
import build.wallet.statemachine.trustedcontact.TrustedContactEnrollmentUiProps
import build.wallet.statemachine.trustedcontact.TrustedContactEnrollmentUiStateMachine
import build.wallet.statemachine.trustedcontact.model.TrustedContactFeatureVariant

@BitkeyInject(ActivityScope::class)
class LiteHomeUiStateMachineImpl(
  private val homeStatusBannerUiStateMachine: HomeStatusBannerUiStateMachine,
  private val liteMoneyHomeUiStateMachine: LiteMoneyHomeUiStateMachine,
  private val liteSettingsHomeUiStateMachine: LiteSettingsHomeUiStateMachine,
  private val trustedContactEnrollmentUiStateMachine: TrustedContactEnrollmentUiStateMachine,
  private val eventTracker: EventTracker,
  private val createAccountUiStateMachine: CreateAccountUiStateMachine,
) : LiteHomeUiStateMachine {
  @Composable
  override fun model(props: LiteHomeUiProps): ScreenModel {
    var uiState: State by remember {
      mutableStateOf(State.LiteHomeUiState(rootScreen = MoneyHome, presentedScreen = null))
    }

    LaunchedEffect("deep-link-routing") {
      Router.onRouteChange { route ->
        when (route) {
          is Route.TrustedContactInvite -> {
            eventTracker.track(Action.ACTION_APP_SOCREC_ENTERED_INVITE_VIA_DEEPLINK)
            uiState = State.LiteHomeUiState(
              rootScreen = MoneyHome,
              presentedScreen = AddTrustedContact(
                acceptInvite = AcceptInvite(
                  inviteCode = route.inviteCode
                )
              )
            )
            return@onRouteChange true
          }
          is Route.BeneficiaryInvite -> {
            uiState = State.LiteHomeUiState(
              rootScreen = MoneyHome,
              presentedScreen = PresentedScreen.BecomeBeneficiary(
                acceptInvite = AcceptInvite(
                  inviteCode = route.inviteCode
                )
              )
            )
            return@onRouteChange true
          }
          else -> false
        }
      }
    }

    // Observe the global status banner model
    val homeStatusBannerModel =
      homeStatusBannerUiStateMachine.model(
        props =
          HomeStatusBannerUiProps(
            f8eEnvironment = props.account.config.f8eEnvironment,
            onBannerClick = null
          )
      )

    return when (val state = uiState) {
      is State.LiteHomeUiState -> return when (val presentedScreen = state.presentedScreen) {
        null -> {
          when (state.rootScreen) {
            MoneyHome -> liteMoneyHomeUiStateMachine.model(
              props = LiteMoneyHomeUiProps(
                account = props.account,
                onUpgradeAccount = {
                  uiState = State.UpgradingAccount
                },
                homeStatusBannerModel = homeStatusBannerModel,
                onSettings = { uiState = state.copy(rootScreen = Settings) },
                onAcceptInvite = {
                  uiState = state.copy(
                    presentedScreen =
                      AddTrustedContact(
                        acceptInvite = AcceptInvite(inviteCode = null)
                      )
                  )
                },
                onBecomeBeneficiary = {
                  uiState = state.copy(
                    presentedScreen =
                      PresentedScreen.BecomeBeneficiary(
                        acceptInvite = AcceptInvite(inviteCode = null)
                      )
                  )
                }
              )
            )

            Settings -> liteSettingsHomeUiStateMachine.model(
              props = LiteSettingsHomeUiProps(
                account = props.account,
                homeStatusBannerModel = homeStatusBannerModel,
                onBack = { uiState = state.copy(rootScreen = MoneyHome) },
                onAppDataDeleted = props.onAppDataDeleted,
                onAccountUpgraded = props.onUpgradeComplete
              )
            )
          }
        }

        is AddTrustedContact -> trustedContactEnrollmentUiStateMachine.model(
          props = TrustedContactEnrollmentUiProps(
            account = props.account,
            retreat = Retreat(
              style = RetreatStyle.Close,
              onRetreat = {
                uiState = state.copy(presentedScreen = null)
              }
            ),
            inviteCode = presentedScreen.acceptInvite?.inviteCode,
            screenPresentationStyle = ScreenPresentationStyle.RootFullScreen,
            onDone = { uiState = state.copy(presentedScreen = null) },
            variant = TrustedContactFeatureVariant.Direct(target = TrustedContactFeatureVariant.Feature.Recovery)
          )
        )
        is PresentedScreen.BecomeBeneficiary -> trustedContactEnrollmentUiStateMachine.model(
          props = TrustedContactEnrollmentUiProps(
            account = props.account,
            retreat = Retreat(
              style = RetreatStyle.Close,
              onRetreat = {
                uiState = state.copy(presentedScreen = null)
              }
            ),
            inviteCode = presentedScreen.acceptInvite?.inviteCode,
            screenPresentationStyle = ScreenPresentationStyle.RootFullScreen,
            onDone = { account ->
              when (account) {
                is FullAccount -> props.onUpgradeComplete(account)
                else -> uiState = state.copy(presentedScreen = null)
              }
            },
            variant = TrustedContactFeatureVariant.Direct(target = TrustedContactFeatureVariant.Feature.Inheritance)
          )
        )
      }
      State.UpgradingAccount -> createAccountUiStateMachine.model(
        props = CreateAccountUiProps(
          context = CreateFullAccountContext.LiteToFullAccountUpgrade(liteAccount = props.account),
          rollback = { uiState = State.LiteHomeUiState(rootScreen = MoneyHome, presentedScreen = null) },
          onOnboardingComplete = props.onUpgradeComplete
        )
      )
    }
  }
}

private sealed interface State {
  data class LiteHomeUiState(
    val rootScreen: HomeScreen,
    val presentedScreen: PresentedScreen?,
  ) : State

  data object UpgradingAccount : State
}

/**
 * Represents a specific "home" screen. These are special screens managed by this state
 * machine that share the ability to present certain content, [PresentedScreen]
 */
private enum class HomeScreen {
  /**
   * Indicates that money home is shown.
   */
  MoneyHome,

  /**
   * Indicates that settings are shown.
   */
  Settings,
}

/**
 * Represents a screen presented on top of either [HomeScreen]
 */
private sealed interface PresentedScreen {
  /** Indicates that the add trusted contact flow is currently presented */
  data class AddTrustedContact(
    val acceptInvite: AcceptInvite?,
  ) : PresentedScreen

  /** Indicates that the become beneficiary flow is currently presented */
  data class BecomeBeneficiary(
    val acceptInvite: AcceptInvite?,
  ) : PresentedScreen
}
