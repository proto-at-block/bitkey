package build.wallet.statemachine.nfc

import androidx.compose.runtime.*
import bitkey.account.AccountConfigService
import bitkey.account.DefaultAccountConfig
import bitkey.account.FullAccountConfig
import bitkey.account.HardwareType
import bitkey.account.LiteAccountConfig
import bitkey.account.SoftwareAccountConfig
import bitkey.recovery.RecoveryStatusService
import build.wallet.account.AccountService
import build.wallet.account.getActiveOrOnboardingAccountOrNull
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.NfcEventTrackerScreenId
import build.wallet.analytics.events.screen.id.NfcEventTrackerScreenId.NFC_INITIATE
import build.wallet.bitkey.account.FullAccount
import build.wallet.crypto.random.SecureRandom
import build.wallet.crypto.random.nextBytes
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.emergencyexitkit.EmergencyExitPayloadRestorer
import build.wallet.encrypt.Secp256k1PublicKey
import build.wallet.encrypt.SignatureVerifier
import build.wallet.encrypt.verifyEcdsaResult
import build.wallet.feature.flags.AsyncNfcSigningFeatureFlag
import build.wallet.feature.flags.DesignSystemUpdatesFeatureFlag
import build.wallet.feature.flags.NfcSessionRetryAttemptsFeatureFlag
import build.wallet.feature.intValue
import build.wallet.feature.isEnabled
import build.wallet.feature.collectIsEnabledAsState
import build.wallet.logging.logInfo
import build.wallet.logging.logWarn
import build.wallet.nfc.NfcAvailability.Available.Disabled
import build.wallet.nfc.NfcAvailability.Available.Enabled
import build.wallet.nfc.NfcAvailability.NotAvailable
import build.wallet.nfc.NfcException
import build.wallet.nfc.NfcReaderCapability
import build.wallet.nfc.NfcSession
import build.wallet.nfc.NfcSession.RequirePairedHardware
import build.wallet.nfc.NfcSession.RequirePairedHardware.NotRequired
import build.wallet.nfc.NfcTransactor
import build.wallet.nfc.platform.NfcCommands
import build.wallet.nfc.transaction.NfcTransaction
import build.wallet.platform.config.AppVariant
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.recovery.Recovery
import build.wallet.statemachine.core.*
import build.wallet.statemachine.nfc.NfcSessionUIState.*
import build.wallet.statemachine.nfc.NfcSessionUIState.AndroidOnly.*
import build.wallet.statemachine.nfc.NfcSessionUIState.InSession.*
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps.HardwareVerification.Required
import build.wallet.statemachine.platform.nfc.EnableNfcNavigator
import build.wallet.statemachine.settings.showDebugMenu
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.ThemePreference
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import okio.ByteString
import okio.ByteString.Companion.toByteString
import build.wallet.statemachine.nfc.NfcBodyModel.Status.Connected as ConnectedState
import build.wallet.statemachine.nfc.NfcBodyModel.Status.Searching as SearchingState
import build.wallet.statemachine.nfc.NfcBodyModel.Status.Success as SuccessState

/**
 * Shared configuration for NFC session props classes.
 * Contains all common fields between [NfcSessionUIStateMachineProps] and
 * [NfcConfirmableSessionUIStateMachineProps].
 */
data class NfcSessionConfig(
  val onConnected: () -> Unit = {},
  val onCancel: () -> Unit,
  /**
   * Optional back action for NFC flows that explicitly support a distinct "back"
   * behavior separate from [onCancel].
   *
   * Defaults to [onCancel] for backward compatibility. The standard NFC session
   * screens continue to use [onCancel] for system back and toolbar back actions
   * unless a specific flow wires [onBack] separately (for example, the hardware
   * confirmation waiting UI in the confirmable NFC session state machine).
   */
  val onBack: () -> Unit = onCancel,
  val onInauthenticHardware: (Throwable) -> Unit = {},
  /**
   * Optional callback invoked when an [NfcException] occurs during the session.
   * Returning `true` indicates the error was handled and the default error
   * screen should not be shown.
   */
  val onError: (NfcException) -> Boolean = { false },
  val needsAuthentication: Boolean = true,
  /** Whether the tapped hardware must match the hardware associated with the account. */
  val hardwareVerification: NfcSessionUIStateMachineProps.HardwareVerification = Required(),
  val shouldLock: Boolean = true,
  // TODO(BKR-1117): make non-nullable
  val segment: AppSegment? = null,
  val actionDescription: String? = null,
  val screenPresentationStyle: ScreenPresentationStyle,
  val eventTrackerContext: NfcEventTrackerScreenIdContext,
  /** Whether to skip collecting and uploading firmware telemetry after the transaction. */
  val skipFirmwareTelemetry: Boolean = false,
  /**
   *  Used to indicate that an operation may take awhile, by using an indeterminate spinner on Android and
   *  add some flavor text on iOS.
   */
  val shouldShowLongRunningOperation: Boolean = false,
  /**
   * When true on iOS, the app keeps the prior screen visible underneath the native CoreNFC sheet.
   * Disable this for longer-running flows that need the custom Bitkey NFC background to remain visible.
   */
  val showNativeSheetOnIos: Boolean = true,
  /**
   * When set, overrides the hardware type derived from [AccountConfig].
   * Use during recovery flows where the account config isn't available but the
   * hardware type was detected from the device in an earlier NFC tap.
   */
  val hardwareTypeOverride: HardwareType? = null,
  /**
   * Whether to show a device confirmation screen on W3 after a successful transaction.
   * When true, a "showConfirmation" interceptor will display an "APPROVED" screen on
   * the W3 device before locking. Only applies to W3 hardware.
   */
  val showDeviceConfirmation: Boolean = false,
  /**
   * When true, skips the hardware Delay+Notify guard.
   * Set only by the lost-hardware cancellation PoP flow so the replacement hardware
   * can cancel an in-progress recovery rather than being blocked by the guard.
   */
  val skipLostHardwareCheck: Boolean = false,
)

class NfcSessionUIStateMachineProps<T>(
  /**
   * The NFC session callback that callers should use to perform commands.
   * Callers should return the action that should be taken upon a successful transaction.
   */
  val session: suspend (NfcSession, NfcCommands) -> T,
  val onSuccess: suspend (@UnsafeVariance T) -> Unit,
  val config: NfcSessionConfig,
) {
  val onConnected: () -> Unit get() = config.onConnected
  val onCancel: () -> Unit get() = config.onCancel
  val onBack: () -> Unit get() = config.onBack
  val onInauthenticHardware: (Throwable) -> Unit get() = config.onInauthenticHardware
  val onError: (NfcException) -> Boolean get() = config.onError
  val needsAuthentication: Boolean get() = config.needsAuthentication
  val hardwareVerification: HardwareVerification get() = config.hardwareVerification
  val shouldLock: Boolean get() = config.shouldLock
  val segment: AppSegment? get() = config.segment
  val actionDescription: String? get() = config.actionDescription
  val screenPresentationStyle: ScreenPresentationStyle get() = config.screenPresentationStyle
  val eventTrackerContext: NfcEventTrackerScreenIdContext get() = config.eventTrackerContext
  val skipFirmwareTelemetry: Boolean get() = config.skipFirmwareTelemetry
  val shouldShowLongRunningOperation: Boolean get() = config.shouldShowLongRunningOperation
  val showNativeSheetOnIos: Boolean get() = config.showNativeSheetOnIos
  val showDeviceConfirmation: Boolean get() = config.showDeviceConfirmation
  val skipLostHardwareCheck: Boolean get() = config.skipLostHardwareCheck

  /**
   * Backward-compatible constructor that maintains existing callsite signatures.
   */
  constructor(
    session: suspend (NfcSession, NfcCommands) -> T,
    onConnected: () -> Unit = {},
    onSuccess: suspend (@UnsafeVariance T) -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit = onCancel,
    onInauthenticHardware: (Throwable) -> Unit = {},
    onError: (NfcException) -> Boolean = { false },
    needsAuthentication: Boolean = true,
    hardwareVerification: HardwareVerification = Required(),
    shouldLock: Boolean = true,
    segment: AppSegment? = null,
    actionDescription: String? = null,
    screenPresentationStyle: ScreenPresentationStyle,
    eventTrackerContext: NfcEventTrackerScreenIdContext,
    skipFirmwareTelemetry: Boolean = false,
    shouldShowLongRunningOperation: Boolean = false,
    showNativeSheetOnIos: Boolean = true,
    hardwareTypeOverride: HardwareType? = null,
    showDeviceConfirmation: Boolean = false,
    skipLostHardwareCheck: Boolean = false,
  ) : this(
    session = session,
    onSuccess = onSuccess,
    config = NfcSessionConfig(
      onConnected = onConnected,
      onCancel = onCancel,
      onBack = onBack,
      onInauthenticHardware = onInauthenticHardware,
      onError = onError,
      needsAuthentication = needsAuthentication,
      hardwareVerification = hardwareVerification,
      shouldLock = shouldLock,
      segment = segment,
      actionDescription = actionDescription,
      screenPresentationStyle = screenPresentationStyle,
      eventTrackerContext = eventTrackerContext,
      skipFirmwareTelemetry = skipFirmwareTelemetry,
      shouldShowLongRunningOperation = shouldShowLongRunningOperation,
      showNativeSheetOnIos = showNativeSheetOnIos,
      hardwareTypeOverride = hardwareTypeOverride,
      showDeviceConfirmation = showDeviceConfirmation,
      skipLostHardwareCheck = skipLostHardwareCheck
    )
  )

  constructor(
    transaction: NfcTransaction<T>,
    screenPresentationStyle: ScreenPresentationStyle,
    eventTrackerContext: NfcEventTrackerScreenIdContext,
    segment: AppSegment? = null,
    actionDescription: String? = null,
    hardwareVerification: HardwareVerification = Required(),
    onInauthenticHardware: (Throwable) -> Unit = {},
    onError: (NfcException) -> Boolean = { false },
    hardwareTypeOverride: HardwareType? = null,
    skipFirmwareTelemetry: Boolean = false,
  ) : this(
    session = transaction::session,
    onSuccess = transaction::onSuccess,
    config = NfcSessionConfig(
      onCancel = transaction::onCancel,
      needsAuthentication = transaction.needsAuthentication,
      hardwareVerification = hardwareVerification,
      shouldLock = transaction.shouldLock,
      segment = segment,
      actionDescription = actionDescription,
      screenPresentationStyle = screenPresentationStyle,
      eventTrackerContext = eventTrackerContext,
      onInauthenticHardware = onInauthenticHardware,
      onError = onError,
      hardwareTypeOverride = hardwareTypeOverride,
      showDeviceConfirmation = transaction.showDeviceConfirmation,
      skipFirmwareTelemetry = skipFirmwareTelemetry
    )
  )

  /**
   * Whether the hardware tapped via NFC must be paired to the current account.
   */
  sealed interface HardwareVerification {
    /** Any hardware device will be accepted. */
    data object NotRequired : HardwareVerification

    /** The hardware tapped must match that expected by the app. */
    data class Required(
      /**
       * During lost hardware recovery flows, we may choose to check against the new hardware instead
       * of the current hardware. We force callers to specify this so that regular flows, e.g. spending,
       * would still work with the old hardware even if a lost hardware recovery was in progress.
       */
      val useRecoveryPubKey: Boolean = false,
    ) : HardwareVerification
  }
}

interface NfcSessionUIStateMachine : StateMachine<NfcSessionUIStateMachineProps<*>, ScreenModel> {
  companion object {
    const val TROUBLESHOOTING_URL = "https://bitkey.world/hc/nfc-error"
  }
}

@BitkeyInject(ActivityScope::class)
class NfcSessionUIStateMachineImpl(
  private val appVariant: AppVariant,
  private val nfcReaderCapability: NfcReaderCapability,
  private val enableNfcNavigator: EnableNfcNavigator,
  private val deviceInfoProvider: DeviceInfoProvider,
  private val nfcTransactor: NfcTransactor,
  private val asyncNfcSigningFeatureFlag: AsyncNfcSigningFeatureFlag,
  private val accountConfigService: AccountConfigService,
  private val signatureVerifier: SignatureVerifier,
  private val accountService: AccountService,
  private val recoveryStatusService: RecoveryStatusService,
  private val inAppBrowserNavigator: InAppBrowserNavigator,
  private val nfcSessionRetryAttemptsFeatureFlag: NfcSessionRetryAttemptsFeatureFlag,
  private val designSystemUpdatesFeatureFlag: DesignSystemUpdatesFeatureFlag,
) : NfcSessionUIStateMachine {
  /**
   * Text shown under the progress spinner (on Android) or on the iOS NFC Sheet when performing
   * a potentially long running operation, like UTXO Consolidation.
   */
  private val longRunningOperationText = "This can take up to 1 minute…"
  private val secureRandom = SecureRandom()

  @Composable
  @Suppress("CyclomaticComplexMethod")
  override fun model(props: NfcSessionUIStateMachineProps<*>): ScreenModel {
    val designSystemV2Enabled by designSystemUpdatesFeatureFlag.collectIsEnabledAsState()
    val devicePlatform = remember { deviceInfoProvider.getDeviceInfo().devicePlatform }
    val accountConfig = remember { accountConfigService.activeOrDefaultConfig().value }
    val isHardwareFake = remember {
      when (accountConfig) {
        is FullAccountConfig -> accountConfig.isHardwareFake
        is DefaultAccountConfig -> accountConfig.isHardwareFake
        // Allow the use of mock hardware to upgrade from Lite -> Full in dev/team apps
        is LiteAccountConfig -> if (appVariant.showDebugMenu) {
          accountConfigService.defaultConfig().value.isHardwareFake
        } else {
          false
        }
        else -> false
      }
    }
    val hardwareType = remember(props.config.hardwareTypeOverride) {
      // Use caller-provided override (e.g. W1 during upgrade flow),
      // otherwise resolve from account config.
      // For DefaultAccountConfig (pre-login onboarding), leave null so NfcCommandsProvider
      // falls back to W1 commands, which work on both W1 and W3 for basic operations.
      // Callers should pass hardwareTypeOverride once the hardware type is detected.
      props.config.hardwareTypeOverride ?: when (accountConfig) {
        is FullAccountConfig -> accountConfig.hardwareType
        // For accounts without a known hardware type (pre-login onboarding, lite/software
        // upgrading to full), leave null so NfcCommandsProvider falls back to W1 commands.
        // W1 commands work on both W1 and W3 for basic operations (all delegated).
        // Callers should pass hardwareTypeOverride once the hardware type is detected.
        is DefaultAccountConfig -> accountConfig.hardwareType
        is LiteAccountConfig, is SoftwareAccountConfig -> null
      }
    }

    var newState by remember {
      mutableStateOf<NfcSessionUIState>(
        when (nfcReaderCapability.availability(isHardwareFake)) {
          NotAvailable -> NoNFCMessage
          Disabled -> EnableNFCInstructions
          Enabled -> Searching
        }
      )
    }
    var sessionCanceledByPlatform by remember { mutableStateOf(false) }

    if (newState is InSession) {
      LaunchedEffect("nfc-transaction") {
        sessionCanceledByPlatform = false
        delayForIosNativeNfcTransition(
          designSystemV2Enabled = designSystemV2Enabled,
          devicePlatform = deviceInfoProvider.getDeviceInfo().devicePlatform
        )
        nfcTransactor.transact(
          parameters = NfcSession.Parameters(
            isHardwareFake = isHardwareFake,
            hardwareType = hardwareType,
            needsAuthentication = props.needsAuthentication,
            shouldLock = props.shouldLock,
            requirePairedHardware = determineNfcHardwarePairingRequired(props.hardwareVerification),
            skipFirmwareTelemetry = props.skipFirmwareTelemetry,
            onTagConnected = { session ->
              if (newState is InSession) {
                props.onConnected()
                if (props.shouldShowLongRunningOperation) {
                  session?.message = longRunningOperationText
                }
                newState = Communicating
              }
            },
            onTagDisconnected = {
              // NB: This is only called on Android.
              if (newState is InSession && newState !is Success) {
                newState = Searching
              }
            },
            onSessionCanceled = {
              if (newState is InSession) {
                sessionCanceledByPlatform = true
                props.onCancel()
              }
            },
            asyncNfcSigning = asyncNfcSigningFeatureFlag.isEnabled(),
            nfcFlowName = props.eventTrackerContext.name,
            maxNfcRetryAttempts = nfcSessionRetryAttemptsFeatureFlag.intValue(),
            showDeviceConfirmation = props.showDeviceConfirmation,
            skipLostHardwareCheck = props.skipLostHardwareCheck
          ),
          transaction = props.session
        ).onSuccess { result ->
          if (newState !is InSession) return@onSuccess
          newState = Success
          delay(
            NfcSuccessScreenDuration(
              devicePlatform = deviceInfoProvider.getDeviceInfo().devicePlatform,
              isHardwareFake = isHardwareFake
            )
          )
          // Must be cast to satisfy compiler type resolution
          @Suppress("USELESS_CAST")
          (props.onSuccess as suspend (Any?) -> Unit).invoke(result)
        }.onFailure { error ->
          if (newState !is InSession) return@onFailure
          when (error) {
            is NfcException.IOSOnly.UserCancellation ->
              if (!sessionCanceledByPlatform) {
                props.onCancel()
              }
            else -> {
              val handled = props.onError(error)
              if (!handled) {
                newState = Error(error)
              }
            }
          }
        }
      }
    }

    return when (val currentState = newState) {
      is InSession ->
        when (currentState) {
          is Searching -> {
            NfcBodyModel(
              text = "Hold your Bitkey to the back of your phone",
              status = SearchingState(onCancel = props.onCancel),
              hardwareType = hardwareType ?: HardwareType.W3,
              showNativeSheetOnIos = props.showNativeSheetOnIos,
              onHelpClick =
                if (designSystemV2Enabled) {
                  { newState = Help(Searching) }
                } else {
                  null
                },
              eventTrackerScreenInfo =
                EventTrackerScreenInfo(
                  eventTrackerScreenId = NFC_INITIATE,
                  eventTrackerContext = props.eventTrackerContext
                )
            ).asPlatformNfcScreen(
              designSystemV2Enabled = designSystemV2Enabled,
              devicePlatform = devicePlatform
            )
          }

          is Communicating -> {
            val text = if (props.shouldShowLongRunningOperation) {
              longRunningOperationText
            } else {
              "Connected"
            }
            NfcBodyModel(
              text = text,
              status = ConnectedState(
                onCancel = props.onCancel,
                showProgressSpinner = props.shouldShowLongRunningOperation
              ),
              hardwareType = hardwareType ?: HardwareType.W3,
              showNativeSheetOnIos = props.showNativeSheetOnIos,
              onHelpClick =
                if (designSystemV2Enabled) {
                  { newState = Help(Searching) }
                } else {
                  null
                },
              eventTrackerScreenInfo =
                EventTrackerScreenInfo(
                  eventTrackerScreenId = NfcEventTrackerScreenId.NFC_DETECTED,
                  eventTrackerContext = props.eventTrackerContext
                )
            ).asPlatformNfcScreen(
              designSystemV2Enabled = designSystemV2Enabled,
              devicePlatform = devicePlatform
            )
          }

          is Success -> {
            NfcBodyModel(
              text = "Success",
              status = SuccessState,
              hardwareType = hardwareType ?: HardwareType.W3,
              showNativeSheetOnIos = props.showNativeSheetOnIos,
              eventTrackerScreenInfo =
                EventTrackerScreenInfo(
                  eventTrackerScreenId = NfcEventTrackerScreenId.NFC_SUCCESS,
                  eventTrackerContext = props.eventTrackerContext
                )
            ).asPlatformNfcScreen(
              designSystemV2Enabled = designSystemV2Enabled,
              devicePlatform = devicePlatform
            )
          }
        }

      is Help ->
        ScreenModel(
          body = NfcHelpBodyModel(
            onBack = {
              newState = currentState.previousState
            }
          ),
          presentationStyle =
            if (designSystemV2Enabled) {
              ScreenPresentationStyle.ModalFullScreen
            } else {
              ScreenPresentationStyle.FullScreen
            },
          themePreference = nfcThemePreference(designSystemV2Enabled, devicePlatform)
        )

      is AndroidOnly -> {
        when (currentState) {
          is NoNFCMessage ->
            NoNfcMessageModel(onBack = props.onCancel)
              .asScreen(presentationStyle = props.screenPresentationStyle)

          is EnableNFCInstructions -> {
            EnableNfcInstructionsModel(
              onBack = props.onCancel,
              onEnableClick = { newState = NavigateToEnableNFC }
            ).asScreen(presentationStyle = props.screenPresentationStyle)
          }

          is NavigateToEnableNFC -> {
            enableNfcNavigator.navigateToEnableNfc { newState = Searching }

            NoNfcMessageModel(onBack = props.onCancel)
              .asScreen(presentationStyle = props.screenPresentationStyle)
          }
        }
      }

      is Error -> {
        NfcErrorFormBodyModel(
          exception = currentState.nfcException,
          onPrimaryButtonClick = props.onCancel,
          onSecondaryButtonClick = {
            when (currentState.nfcException) {
              is NfcException.InauthenticHardware -> {
                props.onInauthenticHardware(currentState.nfcException)
              }
              else -> {
                inAppBrowserNavigator.open(NfcSessionUIStateMachine.TROUBLESHOOTING_URL) {
                  // onClose callback
                }
              }
            }
          },
          segment = props.segment,
          actionDescription = props.actionDescription,
          eventTrackerScreenId = NfcEventTrackerScreenId.NFC_FAILURE,
          eventTrackerScreenIdContext = props.eventTrackerContext
        ).asScreen(props.screenPresentationStyle)
      }
    }
  }

  /**
   * Determines [RequirePairedHardware] to pass to the nfc session, taking into account
   * recovery state, missing pubkeys, and EEK mode bypass.
   */
  private suspend fun determineNfcHardwarePairingRequired(
    hardwareVerification: NfcSessionUIStateMachineProps.HardwareVerification,
  ): RequirePairedHardware {
    if (hardwareVerification !is Required) {
      return NotRequired
    }

    // Retrieve the hardware pubkey expected to be used; using the recovery pubkey is specified
    // and otherwise the hw auth pubkey associated with the account.
    val hwPubKey = if (hardwareVerification.useRecoveryPubKey) {
      when (val recoveryStatus = recoveryStatusService.status.first()) {
        is Recovery.StillRecovering -> recoveryStatus.hardwareAuthKey.pubKey
        else -> null
      }
    } else {
      accountService.getActiveOrOnboardingAccountOrNull<FullAccount>()
        .get()?.keybox?.activeHwKeyBundle?.authKey?.pubKey
    }

    if (hwPubKey == null) {
      logWarn { "hwPubKey is null, unexpectedly bypassing hardwareIsPaired check" }
      return NotRequired
    }

    // In EEK mode we hardcode the value of the pubkey to EEK_PUBLIC_KEY, which isn't valid, so we
    // have to fail open.
    if (hwPubKey.value == EmergencyExitPayloadRestorer.EEK_PUBLIC_KEY) {
      logInfo { "Bypassing hardware checking due to EEK mode" }
      return NotRequired
    }

    return RequirePairedHardware.Required(
      challenge = secureRandom.nextBytes(32).toByteString(),
      checkHardwareIsPaired = { signature, challengeString ->
        verifyEcdsaResult(signature, challengeString, hwPubKey)
      }
    )
  }

  /**
   * Verifies a challenge signed by the hardware.
   */
  private fun verifyEcdsaResult(
    signature: String,
    challengeString: ByteString,
    publicKey: Secp256k1PublicKey,
  ): Boolean {
    val verification = signatureVerifier.verifyEcdsaResult(
      message = challengeString,
      signature = signature,
      publicKey = publicKey
    )
    return verification.get() == true
  }
}

private sealed class NfcSessionUIState {
  sealed class InSession : NfcSessionUIState() {
    data object Searching : InSession()

    data object Communicating : InSession()

    data object Success : InSession()
  }

  data class Help(
    val previousState: InSession,
  ) : NfcSessionUIState()

  sealed class AndroidOnly : NfcSessionUIState() {
    /** Showing a message for mobile devices that don't have NFC -- Android-only. */
    data object NoNFCMessage : AndroidOnly()

    /** Showing a message for when NFC is not enabled -- Android-only. */
    data object EnableNFCInstructions : AndroidOnly()

    /** Navigating to the settings screen for enabling NFC -- Android-only. */
    data object NavigateToEnableNFC : AndroidOnly()
  }

  data class Error(
    val nfcException: NfcException,
  ) : NfcSessionUIState()
}
