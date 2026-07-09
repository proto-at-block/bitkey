package build.wallet.statemachine.nfc

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId.LOADING_ONBOARDING_STEP
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.cloud.backup.CloudBackupHealthRepository
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.keybox.KeyboxDao
import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.NfcCommands
import build.wallet.onboarding.HardwareDescriptorDeliveryService
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorData
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.recovery.RecoverySegment
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

@BitkeyInject(ActivityScope::class)
class DescriptorRepairUiStateMachineImpl(
  private val hardwareDescriptorDeliveryService: HardwareDescriptorDeliveryService,
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val keyboxDao: KeyboxDao,
  private val cloudBackupHealthRepository: CloudBackupHealthRepository,
) : DescriptorRepairUiStateMachine {
  @Composable
  override fun model(props: DescriptorRepairUiProps): ScreenModel {
    var state: State by remember { mutableStateOf(State.FetchingFromServer) }

    return when (val currentState = state) {
      is State.FetchingFromServer -> {
        LaunchedEffect("fetch-signature-and-prepare") {
          hardwareDescriptorDeliveryService
            .fetchSignatureAndPrepareNfcSession(
              account = props.fullAccount,
            )
            .onSuccess { nfcSession ->
              state = State.ReadyToTap(nfcSession = nfcSession)
            }
            .onFailure { error ->
              state = State.Failed(error)
            }
        }

        LoadingBodyModel(
          id = LOADING_ONBOARDING_STEP,
          title = "Preparing wallet for use…"
        ).let { body ->
          ScreenModel(body = body, presentationStyle = props.presentationStyle)
        }
      }

      is State.ReadyToTap -> {
        nfcSessionUIStateMachine.model(
          NfcSessionUIStateMachineProps(
            session = { session, commands ->
              currentState.nfcSession(session, commands)
            },
            onSuccess = { signature ->
              keyboxDao
                .updateAppGlobalAuthKeyHwSignature(
                  keybox = props.fullAccount.keybox,
                  signature = AppGlobalAuthKeyHwSignature(signature)
                )
                .onSuccess { updatedKeybox ->
                  cloudBackupHealthRepository.performSync(
                    accountId = props.fullAccount.accountId,
                    keybox = updatedKeybox
                  )
                  props.onRepairComplete()
                }
                .onFailure { error ->
                  state = State.Failed(Error("Failed to persist wallet repair", error))
                }
            },
            onCancel = {
              props.onBack()
            },
            screenPresentationStyle = props.presentationStyle,
            eventTrackerContext = NfcEventTrackerScreenIdContext.DELIVER_HARDWARE_DESCRIPTOR,
          )
        )
      }

      is State.Failed -> {
        ErrorFormBodyModel(
          title = "Wallet preparation failed",
          subline = "Make sure you have internet connectivity and try again.",
          primaryButton = ButtonDataModel(
            text = "Retry",
            onClick = {
              state = State.FetchingFromServer
            }
          ),
          secondaryButton = ButtonDataModel(
            text = "Go back",
            onClick = {
              props.onBack()
            }
          ),
          errorData = ErrorData(
            segment = RecoverySegment.KeysetRepair.Repair,
            actionDescription = "Repairing wallet descriptor",
            cause = currentState.error
          ),
          eventTrackerScreenId = LOADING_ONBOARDING_STEP
        ).let { body ->
          ScreenModel(body = body, presentationStyle = props.presentationStyle)
        }
      }
    }
  }

  private sealed interface State {
    /** Calling the server to get WSM signature and extracting keys from keybox. */
    data object FetchingFromServer : State

    /** Server call succeeded, ready for NFC tap. */
    data class ReadyToTap(
      val nfcSession: suspend (NfcSession, NfcCommands) -> String,
    ) : State

    /** Server call or key extraction failed. */
    data class Failed(val error: Error) : State
  }
}
