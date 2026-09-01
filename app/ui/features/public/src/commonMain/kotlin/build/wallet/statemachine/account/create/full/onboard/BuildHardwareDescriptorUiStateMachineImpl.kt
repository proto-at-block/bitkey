package build.wallet.statemachine.account.create.full.onboard

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.ensure
import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId.LOADING_ONBOARDING_STEP
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.cloud.backup.CloudBackupHealthRepository
import build.wallet.cloud.backup.health.isHealthy
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.keybox.KeyboxDao
import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.NfcCommands
import build.wallet.nfc.platform.requireW3
import build.wallet.onboarding.HardwareDescriptorDeliveryService
import build.wallet.onboarding.OnboardingCompletionService
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.account.create.full.OnboardingAppSegment
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.ui.theme.ThemePreference
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.flow.map

@BitkeyInject(ActivityScope::class)
class BuildHardwareDescriptorUiStateMachineImpl(
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val hardwareDescriptorDeliveryService: HardwareDescriptorDeliveryService,
  private val cloudBackupHealthRepository: CloudBackupHealthRepository,
  private val keyboxDao: KeyboxDao,
  private val onboardingCompletionService: OnboardingCompletionService,
) : BuildHardwareDescriptorUiStateMachine {
  @Composable
  override fun model(props: BuildHardwareDescriptorUiProps): ScreenModel {
    var state: State by remember { mutableStateOf(State.CompletingOnboarding) }

    return when (val currentState = state) {
      is State.CompletingOnboarding -> {
        LaunchedEffect("complete-onboarding-v2") {
          hardwareDescriptorDeliveryService
            .fetchSignatureAndPrepareNfcSession(
              account = props.fullAccount,
            )
            .onSuccess { nfcSession ->
              // Since we have completed onboarding, prevent the fallback worker from running.
              onboardingCompletionService.recordFallbackCompletion()
              state = State.ShowingIntroScreen(nfcSession = nfcSession)
            }
            .onFailure { error ->
              props.onError(error)
            }
        }

        LoadingBodyModel(
          id = LOADING_ONBOARDING_STEP,
          title = "Completing onboarding"
        ).asRootScreen()
      }

      is State.ShowingIntroScreen -> {
        ScreenModel(
          body = BuildHardwareDescriptorIntroBodyModel(
            onTapBitkey = {
              state = State.TappingHardware(nfcSession = currentState.nfcSession)
            },
            onBack = props.onBack
          ),
          presentationStyle = ScreenPresentationStyle.RootFullScreen,
          themePreference = ThemePreference.System
        )
      }

      is State.TappingHardware -> {
        nfcSessionUIStateMachine.model(
          NfcSessionUIStateMachineProps(
            session = { session, commands ->
              currentState.nfcSession(session, commands.requireW3(session))
            },
            onSuccess = { signature ->
              state = State.SavingBackup(signature)
            },
            onCancel = {
              state = State.ShowingIntroScreen(nfcSession = currentState.nfcSession)
            },
            screenPresentationStyle = ScreenPresentationStyle.Root,
            segment = OnboardingAppSegment.FullAccount,
            actionDescription = "Verifying keys and building hardware descriptor during onboarding",
            eventTrackerContext = NfcEventTrackerScreenIdContext.VERIFY_KEYS_AND_BUILD_HARDWARE_DESCRIPTOR,
            showDeviceConfirmation = true
          )
        )
      }

      is State.SavingBackup -> {
        LaunchedEffect("save-cloud-backup") {
          coroutineBinding {
            // Persist the HW signature over the app auth key to the keybox.
            val updatedKeybox = keyboxDao.updateAppGlobalAuthKeyHwSignature(
              keybox = props.fullAccount.keybox,
              signature = AppGlobalAuthKeyHwSignature(currentState.signature)
            ).bind()

            // Re-sync cloud backup with the real HW signature.
            val backupStatus = cloudBackupHealthRepository.performSync(
              accountId = props.fullAccount.accountId,
              keybox = updatedKeybox
            )
            ensure(backupStatus.appKeyBackupStatus.isHealthy()) {
              Error("Failed to repair cloud backup after updating hardware signature")
            }
          }
            .onSuccess { props.onComplete() }
            .onFailure { error -> props.onError(error) }
        }

        LoadingBodyModel(
          id = LOADING_ONBOARDING_STEP,
          title = "Saving your cloud backup…"
        ).asRootScreen()
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
      val nfcSession: suspend (NfcSession, NfcCommands) -> String,
    ) : State

    /**
     * User is tapping their hardware device via NFC.
     */
    data class TappingHardware(
      val nfcSession: suspend (NfcSession, NfcCommands) -> String,
    ) : State

    /**
     * Saving a cloud backup with the hardware signature returned from NFC.
     */
    data class SavingBackup(
      val signature: String,
    ) : State
  }
}
