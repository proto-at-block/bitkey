package build.wallet.statemachine.account.create.full.onboard

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId.LOADING_ONBOARDING_STEP
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.chaincode.delegation.ChaincodeExtractor
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.ensureNotNull
import build.wallet.f8e.onboarding.CompleteOnboardingResponseV2
import build.wallet.f8e.onboarding.OnboardingF8eClient
import build.wallet.onboarding.OnboardingCompletionService
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.ThemePreference
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import okio.ByteString
import okio.ByteString.Companion.decodeHex

private fun String.decodeHexOrError(fieldName: String): ByteString =
  runCatching { decodeHex() }
    .getOrElse { throw IllegalArgumentException("Invalid $fieldName hex from server") }

@BitkeyInject(ActivityScope::class)
class BuildHardwareDescriptorUiStateMachineImpl(
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val onboardingF8eClient: OnboardingF8eClient,
  private val chaincodeExtractor: ChaincodeExtractor,
  private val onboardingCompletionService: OnboardingCompletionService,
) : BuildHardwareDescriptorUiStateMachine {
  @Composable
  override fun model(props: BuildHardwareDescriptorUiProps): ScreenModel {
    var state: State by remember { mutableStateOf(State.CompletingOnboarding) }

    return when (val currentState = state) {
      is State.CompletingOnboarding -> {
        LaunchedEffect("complete-onboarding-v2") {
          onboardingF8eClient
            .completeOnboardingV2(
              f8eEnvironment = props.fullAccount.config.f8eEnvironment,
              fullAccountId = props.fullAccount.accountId
            )
            .onSuccess { response ->
              // Since we have completed onboarding, prevent the fallback worker from running.
              onboardingCompletionService.recordFallbackCompletion()
              state = State.ShowingIntroScreen(onboardingResponse = response)
            }
            .onFailure { error ->
              props.onBackupFailed(error)
            }
        }

        LoadingBodyModel(
          id = LOADING_ONBOARDING_STEP,
          title = "Completing onboarding"
        ).asRootScreen()
      }

      is State.ShowingIntroScreen -> ScreenModel(
        body = BuildHardwareDescriptorIntroBodyModel(
          onTapBitkey = {
            state = State.TappingHardware(onboardingResponse = currentState.onboardingResponse)
          },
          onBack = null
        ),
        presentationStyle = ScreenPresentationStyle.RootFullScreen,
        themePreference = ThemePreference.Manual(Theme.DARK)
      )

      is State.TappingHardware -> {
        nfcSessionUIStateMachine.model(
          NfcSessionUIStateMachineProps(
            session = { session, commands ->
              coroutineBinding<Boolean, Throwable> {
                val response = currentState.onboardingResponse
                val keyset = props.fullAccount.keybox.activeSpendingKeyset

                // Extract app spending key components
                val appSpendingKey = response.appSpendingPub.decodeHexOrError("app spending key")
                val appSpendingKeyChaincode = chaincodeExtractor
                  .extractChaincode(keyset.appKey.key.xpub)
                  .result.bind()

                val networkMainnet =
                  props.fullAccount.keybox.config.bitcoinNetworkType == BitcoinNetworkType.BITCOIN

                val appAuthKey = response.appAuthPub.decodeHexOrError("app auth key")

                // Extract server spending key components
                val serverSpendingXpub = keyset.f8eSpendingKeyset.privateWalletRootXpub
                ensureNotNull(serverSpendingXpub) {
                  IllegalStateException("Server spending xpub is required for private wallets")
                }

                val serverSpendingKeyChaincode = chaincodeExtractor
                  .extractChaincode(serverSpendingXpub)
                  .result.bind()
                val serverSpendingKey = response.serverSpendingPub.decodeHexOrError("server spending key")

                val wsmSignature = response.signature.decodeHexOrError("WSM signature")

                commands.verifyKeysAndBuildDescriptor(
                  session = session,
                  appSpendingKey = appSpendingKey,
                  appSpendingKeyChaincode = appSpendingKeyChaincode,
                  networkMainnet = networkMainnet,
                  appAuthKey = appAuthKey,
                  serverSpendingKey = serverSpendingKey,
                  serverSpendingKeyChaincode = serverSpendingKeyChaincode,
                  wsmSignature = wsmSignature
                )
              }
            },
            onSuccess = { _ ->
              props.onComplete()
            },
            onCancel = {
              state = State.ShowingIntroScreen(onboardingResponse = currentState.onboardingResponse)
            },
            screenPresentationStyle = ScreenPresentationStyle.Root,
            eventTrackerContext = NfcEventTrackerScreenIdContext.VERIFY_KEYS_AND_BUILD_HARDWARE_DESCRIPTOR
          )
        )
      }
    }
  }

  private sealed interface State {
    /**
     * Completing onboarding by calling the V2 endpoint to get signed keys.
     */
    data object CompletingOnboarding : State

    /**
     * Showing the intro screen with a button to start the NFC tap.
     */
    data class ShowingIntroScreen(
      val onboardingResponse: CompleteOnboardingResponseV2,
    ) : State

    /**
     * User is tapping their hardware device via NFC.
     */
    data class TappingHardware(
      val onboardingResponse: CompleteOnboardingResponseV2,
    ) : State
  }
}
