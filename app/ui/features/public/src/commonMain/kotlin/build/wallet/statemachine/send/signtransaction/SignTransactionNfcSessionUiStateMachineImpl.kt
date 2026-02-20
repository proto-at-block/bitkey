package build.wallet.statemachine.send.signtransaction

import androidx.compose.runtime.*
import bitkey.account.AccountConfig
import bitkey.account.AccountConfigService
import bitkey.account.DefaultAccountConfig
import bitkey.account.FullAccountConfig
import bitkey.account.HardwareType
import bitkey.ui.verification.TxVerificationAppSegment
import build.wallet.Progress
import build.wallet.account.AccountService
import build.wallet.account.getAccount
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.NfcEventTrackerScreenId
import build.wallet.analytics.events.screen.id.NfcEventTrackerScreenId.*
import build.wallet.bitkey.account.FullAccount
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.crypto.random.SecureRandom
import build.wallet.crypto.random.nextBytes
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.encrypt.SignatureVerifier
import build.wallet.encrypt.verifyEcdsaResult
import build.wallet.feature.flags.NfcSessionRetryAttemptsFeatureFlag
import build.wallet.feature.intValue
import build.wallet.keybox.KeyboxDao
import build.wallet.logging.logDebug
import build.wallet.nfc.*
import build.wallet.nfc.NfcAvailability.Available.Disabled
import build.wallet.nfc.NfcAvailability.Available.Enabled
import build.wallet.nfc.NfcAvailability.NotAvailable
import build.wallet.nfc.NfcSession.RequirePairedHardware
import build.wallet.nfc.platform.EmulatedPromptOption
import build.wallet.nfc.platform.HardwareInteraction
import build.wallet.nfc.platform.NfcCommands
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.statemachine.core.NfcErrorFormBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.nfc.AndroidNfcAvailabilityUiState
import build.wallet.statemachine.nfc.AndroidNfcAvailabilityUiState.*
import build.wallet.statemachine.nfc.EnableNfcInstructionsModel
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NoNfcMessageModel
import build.wallet.statemachine.nfc.PromptSelectionFormBodyModel
import build.wallet.statemachine.platform.nfc.EnableNfcNavigator
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationUiProps
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationUiStateMachine
import build.wallet.statemachine.send.signtransaction.SignTransactionNfcSessionUiState.InSessionUiState
import build.wallet.statemachine.send.signtransaction.SignTransactionNfcSessionUiState.InSessionUiState.*
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.ThemePreference
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getOrThrow
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okio.ByteString.Companion.toByteString
import kotlin.coroutines.cancellation.CancellationException

@BitkeyInject(ActivityScope::class)
class SignTransactionNfcSessionUiStateMachineImpl(
  private val enableNfcNavigator: EnableNfcNavigator,
  private val eventTracker: EventTracker,
  private val nfcReaderCapability: NfcReaderCapability,
  private val nfcTransactor: NfcTransactor,
  private val accountConfigService: AccountConfigService,
  private val accountService: AccountService,
  private val keyboxDao: KeyboxDao,
  private val signatureVerifier: SignatureVerifier,
  private val nfcSessionRetryAttemptsFeatureFlag: NfcSessionRetryAttemptsFeatureFlag,
  private val hardwareConfirmationUiStateMachine: HardwareConfirmationUiStateMachine,
  private val inAppBrowserNavigator: InAppBrowserNavigator,
) : SignTransactionNfcSessionUiStateMachine {
  private val secureRandom = SecureRandom()

  @Composable
  override fun model(props: SignTransactionNfcSessionUiProps): ScreenModel {
    val accountConfig = remember { accountConfigService.activeOrDefaultConfig().value }
    val isHardwareFake = remember { determineIsHardwareFake(accountConfig) }
    val hardwareType = remember { determineHardwareType(accountConfig) }

    var uiState by remember {
      mutableStateOf<Any>(
        determineInitialUiState(nfcReaderCapability.availability(isHardwareFake))
      )
    }

    return when (val state = uiState) {
      is InSessionUiState -> inSessionScreenModel(
        props = props,
        state = state,
        isHardwareFake = isHardwareFake,
        hardwareType = hardwareType,
        setState = { uiState = it }
      )

      is AndroidNfcAvailabilityUiState -> androidOnlyScreenModel(
        props = props,
        state = state,
        setState = { uiState = it }
      )

      else -> error("Unexpected state: $state")
    }
  }

  /**
   * Generates the screen model for in-session UI states.
   * Manages NFC transaction effects and progress tracking during transaction signing.
   */
  @Composable
  private fun inSessionScreenModel(
    props: SignTransactionNfcSessionUiProps,
    state: InSessionUiState,
    isHardwareFake: Boolean,
    hardwareType: HardwareType,
    setState: (Any) -> Unit,
  ): ScreenModel {
    return when (state) {
      is InNfcSessionUiState -> {
        // Track progress separately from state to avoid closure capture issues in LaunchedEffect
        var transferProgress by remember { mutableStateOf(Progress.Zero) }

        // NfcTransactionEffect stays alive for the entire InNfcSessionUiState
        NfcTransactionEffect(
          props = props,
          state = state,
          isHardwareFake = isHardwareFake,
          hardwareType = hardwareType,
          setState = setState,
          onProgressUpdate = { progress -> transferProgress = progress }
        )

        when (state.displayMode) {
          InNfcSessionUiState.DisplayMode.Searching -> {
            SignTransactionNfcBodyModel(
              onCancel = props.onBack,
              status = SignTransactionNfcBodyModel.Status.Searching,
              eventTrackerScreenInfo = EventTrackerScreenInfo(NFC_INITIATE, props.eventTrackerContext)
            ).asFullScreen()
          }
          InNfcSessionUiState.DisplayMode.Transferring -> {
            SignTransactionNfcBodyModel(
              onCancel = props.onBack,
              status = SignTransactionNfcBodyModel.Status.Transferring(transferProgress),
              eventTrackerScreenInfo = EventTrackerScreenInfo(NFC_INITIATE, props.eventTrackerContext)
            ).asFullScreen()
          }
          InNfcSessionUiState.DisplayMode.LostConnection -> {
            SignTransactionNfcBodyModel(
              onCancel = props.onBack,
              status = SignTransactionNfcBodyModel.Status.LostConnection(transferProgress),
              eventTrackerScreenInfo = EventTrackerScreenInfo(NFC_INITIATE, props.eventTrackerContext)
            ).asPlatformNfcScreen()
          }
        }
      }

      is SuccessUiState -> {
        LaunchedEffect("sign-success") {
          logDebug { "Transaction signed successfully" }
          props.onSuccess(state.signedPsbt)
        }

        SignTransactionNfcBodyModel(
          onCancel = null,
          status = SignTransactionNfcBodyModel.Status.Success,
          eventTrackerScreenInfo = EventTrackerScreenInfo(NFC_SUCCESS, props.eventTrackerContext)
        ).asFullScreen()
      }

      is AwaitingConfirmationUiState -> {
        // Show confirmation UI for W3 two-tap flow
        hardwareConfirmationUiStateMachine.model(
          props = HardwareConfirmationUiProps(
            onBack = props.onBack,
            onConfirm = {
              // User confirmed - transition to InNfcSessionUiState with fetchResult
              // to start a new NFC session for the continuation
              setState(
                InNfcSessionUiState(
                  fetchResult = state.fetchResult
                )
              )
            }
          )
        )
      }

      is EmulatingPromptUiState -> {
        val scope = rememberStableCoroutineScope()
        PromptSelectionFormBodyModel(
          options = state.options.map { it.name },
          onOptionSelected = { selectedIndex ->
            val selectedOption = state.options[selectedIndex]
            scope.launch {
              selectedOption.onSelect?.invoke()
              // If "Deny" was selected, cancel the flow instead of continuing
              if (selectedOption.name == EmulatedPromptOption.DENY) {
                props.onBack()
              } else {
                // Transition to InNfcSessionUiState with fetchResult to start
                // a new NFC session for the continuation
                setState(
                  InNfcSessionUiState(
                    fetchResult = selectedOption.fetchResult
                  )
                )
              }
            }
          },
          onBack = props.onBack,
          eventTrackerContext = props.eventTrackerContext
        ).asModalScreen()
      }

      is ErrorUiState -> {
        NfcErrorFormBodyModel(
          exception = state.exception,
          onPrimaryButtonClick = props.onBack,
          onSecondaryButtonClick = {
            when (state.exception) {
              is NfcException.InauthenticHardware -> {
                // Inauthentic hardware should be caught during pairing, fail loudly.
                error("Inauthentic hardware detected during transaction signing: ${state.exception.message}")
              }
              else -> {
                inAppBrowserNavigator.open(NfcSessionUIStateMachine.TROUBLESHOOTING_URL) {
                  // onClose callback - do nothing
                }
              }
            }
          },
          segment = TxVerificationAppSegment.Transaction,
          actionDescription = "Sign Transaction",
          eventTrackerScreenId = NfcEventTrackerScreenId.NFC_FAILURE,
          eventTrackerScreenIdContext = props.eventTrackerContext
        ).asModalScreen()
      }
    }
  }

  /**
   * Generates the screen model for Android-only UI states.
   * Handles NFC availability issues specific to Android platform.
   */
  @Composable
  private fun androidOnlyScreenModel(
    props: SignTransactionNfcSessionUiProps,
    state: AndroidNfcAvailabilityUiState,
    setState: (Any) -> Unit,
  ): ScreenModel {
    return when (state) {
      is NoNFCMessage ->
        NoNfcMessageModel(onBack = props.onBack)
          .asModalScreen()

      is EnableNFCInstructions -> {
        EnableNfcInstructionsModel(
          onBack = props.onBack,
          onEnableClick = { setState(NavigateToEnableNFC) }
        ).asModalScreen()
      }

      is NavigateToEnableNFC -> {
        enableNfcNavigator.navigateToEnableNfc {
          setState(InNfcSessionUiState())
        }
        NoNfcMessageModel(onBack = props.onBack)
          .asModalScreen()
      }
    }
  }

  /**
   * Determines whether the hardware is using a fake implementation based on account configuration.
   */
  private fun determineIsHardwareFake(accountConfig: AccountConfig?): Boolean {
    return when (accountConfig) {
      is FullAccountConfig -> accountConfig.isHardwareFake
      is DefaultAccountConfig -> accountConfig.isHardwareFake
      else -> false
    }
  }

  /**
   * Determines the hardware type from account configuration.
   */
  private fun determineHardwareType(accountConfig: AccountConfig?): HardwareType {
    return when (accountConfig) {
      is FullAccountConfig -> accountConfig.hardwareType
      is DefaultAccountConfig -> accountConfig.hardwareType ?: HardwareType.W1
      else -> HardwareType.W1
    }
  }

  /**
   * Determines the initial UI state based on NFC availability.
   */
  private fun determineInitialUiState(availability: NfcAvailability): Any {
    return when (availability) {
      NotAvailable -> NoNFCMessage
      Disabled -> EnableNFCInstructions
      Enabled -> InNfcSessionUiState()
    }
  }

  /**
   * Single NFC transaction effect that handles both initial transactions and continuations.
   *
   * @param state The active NFC session state. If [InNfcSessionUiState.fetchResult] is set,
   * this is a continuation from a two-tap flow and will call fetchResult to fetch the signed PSBT.
   * Otherwise, starts a fresh transaction signing flow.
   */
  @Composable
  private fun NfcTransactionEffect(
    props: SignTransactionNfcSessionUiProps,
    state: InNfcSessionUiState,
    isHardwareFake: Boolean,
    hardwareType: HardwareType,
    setState: (Any) -> Unit,
    onProgressUpdate: (Progress) -> Unit,
  ) {
    val continuation = state.fetchResult
    // Include whether this is a continuation in the key so a fresh NFC session starts
    val effectKey = "sign-transaction-${continuation != null}"

    LaunchedEffect(effectKey) {
      val hwPubKey = keyboxDao.activeKeybox().first().value?.activeHwKeyBundle?.authKey?.pubKey
      nfcTransactor
        .transact(
          parameters =
            NfcSession.Parameters(
              isHardwareFake = isHardwareFake,
              hardwareType = hardwareType,
              needsAuthentication = true,
              shouldLock = true,
              skipFirmwareTelemetry = false,
              nfcFlowName = if (continuation != null) "sign-transaction-confirmation" else "sign-transaction",
              onTagConnected = {
                eventTracker.track(EventTrackerScreenInfo(NFC_DETECTED, props.eventTrackerContext))
                val newMode = if (continuation != null) {
                  // Continuation - we're fetching the signed PSBT
                  InNfcSessionUiState.DisplayMode.Searching
                } else {
                  // Fresh transaction - might show transferring if W3
                  InNfcSessionUiState.DisplayMode.Transferring
                }
                setState(state.copy(displayMode = newMode))
              },
              onTagDisconnected = {
                setState(state.copy(displayMode = InNfcSessionUiState.DisplayMode.LostConnection))
              },
              requirePairedHardware = hwPubKey?.let {
                RequirePairedHardware.Required(
                  challenge = secureRandom.nextBytes(32).toByteString(),
                  checkHardwareIsPaired = { signature, challengeString ->
                    val verification = signatureVerifier.verifyEcdsaResult(
                      message = challengeString,
                      signature = signature,
                      publicKey = hwPubKey
                    )
                    verification.get() == true
                  }
                )
              } ?: RequirePairedHardware.NotRequired,
              asyncNfcSigning = false,
              maxNfcRetryAttempts = nfcSessionRetryAttemptsFeatureFlag.intValue()
            ),
          transaction = { session, commands ->
            if (continuation != null) {
              // Continuation from two-tap flow: fetch the signed PSBT
              signTransactionContinuation(
                session = session,
                commands = commands,
                fetchResult = continuation
              )
            } else {
              // Fresh start: run full signTransaction
              signTransaction(
                session = session,
                commands = commands,
                props = props,
                onProgress = { progress ->
                  session.message = "${(progress.value * 100).toInt()}%"
                  onProgressUpdate(progress)
                }
              )
            }
          }
        ).onFailure { error ->
          when (error) {
            is NfcException.IOSOnly.UserCancellation -> {
              props.onBack()
            }
            else -> {
              val handled = props.onError(error)
              if (!handled) {
                setState(ErrorUiState(error))
              }
            }
          }
        }.onSuccess { result ->
          when (result) {
            is SignTransactionResult.Completed -> {
              setState(SuccessUiState(result.signedPsbt))
            }
            is SignTransactionResult.RequiresConfirmation -> {
              // W3 two-tap flow: transition to awaiting confirmation state
              setState(
                AwaitingConfirmationUiState(
                  fetchResult = result.fetchResult
                )
              )
            }
            is SignTransactionResult.RequiresEmulatedPrompt -> {
              // Fake hardware: transition to emulated prompt selection state
              setState(
                EmulatingPromptUiState(
                  options = result.options
                )
              )
            }
          }
        }
    }
  }

  /**
   * Performs the full transaction signing flow.
   * Response-based routing via HardwareInteraction handles W1 vs W3 automatically.
   */
  @Throws(NfcException::class, CancellationException::class)
  private suspend fun signTransaction(
    session: NfcSession,
    commands: NfcCommands,
    props: SignTransactionNfcSessionUiProps,
    onProgress: (Progress) -> Unit,
  ): SignTransactionResult {
    // Fetch the spending keyset right when we need it
    val account: FullAccount = accountService.getAccount<FullAccount>().getOrThrow()
    val spendingKeyset = account.keybox.activeSpendingKeyset

    val interaction = commands.signTransaction(
      session = session,
      psbt = props.psbt,
      spendingKeyset = spendingKeyset
    )

    return when (interaction) {
      is HardwareInteraction.Completed -> {
        // W1 path: immediate completion
        SignTransactionResult.Completed(interaction.result)
      }

      is HardwareInteraction.RequiresTransfer -> {
        // W3 path: chunked transfer required
        val nextInteraction = interaction.transferAndFetch(
          session,
          commands,
          onProgress
        )
        // After transfer, should be RequiresConfirmation or ConfirmWithEmulatedPrompt
        when (nextInteraction) {
          is HardwareInteraction.RequiresConfirmation -> {
            SignTransactionResult.RequiresConfirmation(nextInteraction.fetchResult)
          }
          is HardwareInteraction.ConfirmWithEmulatedPrompt -> {
            SignTransactionResult.RequiresEmulatedPrompt(nextInteraction.options)
          }
          is HardwareInteraction.Completed -> {
            // Unexpected but handle it
            SignTransactionResult.Completed(nextInteraction.result)
          }
          is HardwareInteraction.RequiresTransfer -> {
            throw NfcException.CommandError("Unexpected nested RequiresTransfer")
          }
        }
      }

      is HardwareInteraction.RequiresConfirmation -> {
        // Direct confirmation (shouldn't happen for signTransaction but handle it)
        SignTransactionResult.RequiresConfirmation(interaction.fetchResult)
      }

      is HardwareInteraction.ConfirmWithEmulatedPrompt -> {
        // Fake hardware emulated prompt
        SignTransactionResult.RequiresEmulatedPrompt(interaction.options)
      }
    }
  }

  /**
   * Continuation transaction for two-tap flow: calls fetchResult to fetch the signed PSBT.
   */
  @Throws(NfcException::class, CancellationException::class)
  private suspend fun signTransactionContinuation(
    session: NfcSession,
    commands: NfcCommands,
    fetchResult: suspend (
      NfcSession,
      NfcCommands,
    ) -> HardwareInteraction<build.wallet.bitcoin.transactions.Psbt>,
  ): SignTransactionResult {
    val interaction = fetchResult(session, commands)

    return when (interaction) {
      is HardwareInteraction.Completed -> {
        SignTransactionResult.Completed(interaction.result)
      }
      else -> {
        throw NfcException.CommandError("Unexpected interaction type in continuation: ${interaction::class.simpleName}")
      }
    }
  }

  private fun SignTransactionNfcBodyModel.asFullScreen() =
    ScreenModel(
      body = this,
      presentationStyle = ScreenPresentationStyle.FullScreen,
      themePreference = ThemePreference.Manual(Theme.DARK)
    )

  private fun SignTransactionNfcBodyModel.asPlatformNfcScreen() =
    ScreenModel(
      body = this,
      presentationStyle = ScreenPresentationStyle.FullScreen,
      themePreference = ThemePreference.Manual(Theme.DARK),
      platformNfcScreen = true
    )

  private fun PromptSelectionFormBodyModel.asModalScreen() =
    ScreenModel(
      body = this,
      presentationStyle = ScreenPresentationStyle.Modal
    )

  private fun EnableNfcInstructionsModel.asModalScreen() =
    ScreenModel(
      body = this,
      presentationStyle = ScreenPresentationStyle.Modal
    )

  private fun build.wallet.statemachine.core.form.FormBodyModel.asModalScreen() =
    ScreenModel(
      body = this,
      presentationStyle = ScreenPresentationStyle.Modal
    )
}

/**
 * Internal states for managing the transaction signing NFC session.
 * Includes both in-session states and Android NFC availability states.
 */
private sealed interface SignTransactionNfcSessionUiState {
  /**
   * States that occur during an active NFC session.
   */
  sealed interface InSessionUiState : SignTransactionNfcSessionUiState {
    /**
     * Active NFC session in progress.
     */
    data class InNfcSessionUiState(
      val displayMode: DisplayMode = DisplayMode.Searching,
      val fetchResult: (
        suspend (
          NfcSession,
          NfcCommands,
        ) -> HardwareInteraction<build.wallet.bitcoin.transactions.Psbt>
      )? = null,
    ) : InSessionUiState {
      enum class DisplayMode {
        /** Searching for NFC device */
        Searching,

        /** Transferring PSBT data (W3 chunked transfer) */
        Transferring,

        /** Lost connection during transfer */
        LostConnection,
      }
    }

    /**
     * Waiting for user to confirm transaction on device (W3 two-tap flow).
     */
    data class AwaitingConfirmationUiState(
      val fetchResult: suspend (
        NfcSession,
        NfcCommands,
      ) -> HardwareInteraction<build.wallet.bitcoin.transactions.Psbt>,
    ) : InSessionUiState

    /**
     * Showing emulated prompt for fake hardware (approve/deny selection).
     */
    data class EmulatingPromptUiState(
      val options: List<EmulatedPromptOption<build.wallet.bitcoin.transactions.Psbt>>,
    ) : InSessionUiState

    /**
     * Transaction signed successfully.
     */
    data class SuccessUiState(
      val signedPsbt: build.wallet.bitcoin.transactions.Psbt,
    ) : InSessionUiState

    /**
     * Error occurred during signing. Shows NFC-specific error UI.
     */
    data class ErrorUiState(
      val exception: NfcException,
    ) : InSessionUiState
  }
}

/**
 * Result of a transaction signing NFC transaction.
 */
private sealed interface SignTransactionResult {
  /**
   * Signing completed successfully with the signed PSBT.
   */
  data class Completed(
    val signedPsbt: build.wallet.bitcoin.transactions.Psbt,
  ) : SignTransactionResult

  /**
   * Requires user confirmation on device before continuing (W3 two-tap flow).
   */
  data class RequiresConfirmation(
    val fetchResult: suspend (
      NfcSession,
      NfcCommands,
    ) -> HardwareInteraction<build.wallet.bitcoin.transactions.Psbt>,
  ) : SignTransactionResult

  /**
   * Fake hardware requires emulated prompt selection (approve/deny).
   */
  data class RequiresEmulatedPrompt(
    val options: List<EmulatedPromptOption<build.wallet.bitcoin.transactions.Psbt>>,
  ) : SignTransactionResult
}
