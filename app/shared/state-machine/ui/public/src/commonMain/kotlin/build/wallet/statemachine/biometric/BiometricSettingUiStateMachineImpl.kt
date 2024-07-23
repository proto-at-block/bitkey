package build.wallet.statemachine.biometric

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.SettingsEventTrackerScreenId
import build.wallet.coachmark.CoachmarkIdentifier
import build.wallet.coachmark.CoachmarkService
import build.wallet.compose.collections.immutableListOf
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.encrypt.SignatureVerifier
import build.wallet.encrypt.verifyEcdsaResult
import build.wallet.inappsecurity.BiometricPreference
import build.wallet.nfc.platform.signChallenge
import build.wallet.platform.biometrics.BiometricError
import build.wallet.platform.biometrics.BiometricPrompter
import build.wallet.platform.biometrics.BiometricTextProvider
import build.wallet.platform.settings.SystemSettingsLauncher
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.ui.model.SheetClosingClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.coachmark.CoachmarkModel
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.switch.SwitchModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.launch
import okio.ByteString.Companion.encodeUtf8

const val BIOMETRIC_AUTH_CHALLENGE = "biometric-auth-challenge"

class BiometricSettingUiStateMachineImpl(
  private val biometricPreference: BiometricPreference,
  private val biometricTextProvider: BiometricTextProvider,
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val biometricPrompter: BiometricPrompter,
  private val signatureVerifier: SignatureVerifier,
  private val settingsLauncher: SystemSettingsLauncher,
  private val coachmarkService: CoachmarkService,
) : BiometricSettingUiStateMachine {
  @Composable
  override fun model(props: BiometricSettingUiProps): ScreenModel {
    var uiState: State by remember {
      mutableStateOf(
        State.EnablingBiometricSetting(
          isEnabled = false
        )
      )
    }

    val isEnabled by remember {
      biometricPreference.isEnabled()
    }.collectAsState(false)

    val scope = rememberStableCoroutineScope()

    var coachmarkDisplayed by remember { mutableStateOf(false) }
    var coachmarksToDisplay by remember { mutableStateOf(listOf<CoachmarkIdentifier>()) }
    LaunchedEffect("coachmarks", coachmarkDisplayed) {
      coachmarkService
        .coachmarksToDisplay(setOf(CoachmarkIdentifier.BiometricUnlockCoachmark))
        .onSuccess { coachmarksToDisplay = it }
    }

    var sheetModel: SheetModel? by remember { mutableStateOf(null) }
    val biometricTitle = biometricTextProvider.getSettingsTitleText()
    return when (uiState) {
      is State.EnablingBiometricSetting -> biometricSettingScreen(
        props = props,
        isEnabled = isEnabled,
        biometricSettingTitleText = biometricTitle,
        biometricSettingSecondaryText = biometricTextProvider.getSettingsSecondaryText(),
        coachmark = if (coachmarksToDisplay.contains(CoachmarkIdentifier.BiometricUnlockCoachmark)) {
          CoachmarkModel(
            identifier = CoachmarkIdentifier.BiometricUnlockCoachmark,
            title = "Set up $biometricTitle",
            description = "We recommend you secure your app by setting up $biometricTitle to enhance app security.",
            arrowPosition = CoachmarkModel.ArrowPosition(
              vertical = CoachmarkModel.ArrowPosition.Vertical.Top,
              horizontal = CoachmarkModel.ArrowPosition.Horizontal.Trailing
            ),
            button = null,
            image = null,
            dismiss = {
              if (coachmarksToDisplay.contains(CoachmarkIdentifier.BiometricUnlockCoachmark)) {
                scope.launch {
                  coachmarkService
                    .markCoachmarkAsDisplayed(CoachmarkIdentifier.BiometricUnlockCoachmark)
                  coachmarkDisplayed = true
                }
              }
            }
          )
        } else {
          null
        },
        onEnableCheckedChange = {
          if (coachmarksToDisplay.contains(CoachmarkIdentifier.BiometricUnlockCoachmark)) {
            scope.launch {
              coachmarkService
                .markCoachmarkAsDisplayed(CoachmarkIdentifier.BiometricUnlockCoachmark)
              coachmarkDisplayed = true
            }
          }
          if (!isEnabled) {
            val biometricsAvailability = biometricPrompter.biometricsAvailability().result
            biometricsAvailability
              .onSuccess {
                sheetModel = nfcPromptSheetModel(
                  biometricText = biometricTextProvider.getSettingsTitleText(),
                  isDisabling = isEnabled,
                  onScanBitkeyDevice = {
                    sheetModel = null
                    uiState = State.Verifying
                  },
                  onCancel = {
                    sheetModel = null
                  },
                  onBack = {
                    sheetModel = null
                  }
                )
              }
              .onFailure { error ->
                sheetModel = when (error) {
                  is BiometricError.BiometricsLocked -> errorSheetModel(
                    headline = "Unable to enable biometrics",
                    subline = "Your biometrics are locked. Please unlock your device and try again.",
                    onCancel = { sheetModel = null },
                    onBack = { sheetModel = null }
                  )
                  is BiometricError.NoBiometricEnrolled -> notEnrolledErrorSheetModel(
                    headline = "No Biometric is enrolled",
                    subline = "Please visit system settings to enable biometrics.",
                    onCancel = { sheetModel = null },
                    onBack = { sheetModel = null },
                    onGoToSettings = {
                      sheetModel = null
                      settingsLauncher.launchSecuritySettings()
                    }
                  )
                  is BiometricError.NoHardware -> errorSheetModel(
                    headline = "No Biometric hardware found",
                    subline = "Please verify your phone has biometric hardware.",
                    onCancel = { sheetModel = null },
                    onBack = { sheetModel = null }
                  )
                  else -> errorSheetModel(
                    headline = "Unable to enable biometrics.",
                    subline = "Please try again later.",
                    onCancel = { sheetModel = null },
                    onBack = { sheetModel = null }
                  )
                }
              }
          } else {
            sheetModel = nfcPromptSheetModel(
              biometricText = biometricTextProvider.getSettingsTitleText(),
              isDisabling = isEnabled,
              onScanBitkeyDevice = {
                sheetModel = null
                uiState = State.Verifying
              },
              onCancel = {
                sheetModel = null
              },
              onBack = {
                sheetModel = null
              }
            )
          }
        },
        sheetModel = sheetModel
      )

      is State.Verifying -> nfcSessionUIStateMachine.model(
        props = NfcSessionUIStateMachineProps(
          session = { session, commands ->
            commands.signChallenge(session, BIOMETRIC_AUTH_CHALLENGE)
          },
          onSuccess = { signature ->
            val verification = signatureVerifier.verifyEcdsaResult(
              message = BIOMETRIC_AUTH_CHALLENGE.encodeUtf8(),
              signature = signature,
              publicKey = props.keybox.activeHwKeyBundle.authKey.pubKey
            )
            if (verification.get() == true) {
              if (!isEnabled) {
                // the signed challenged was verified so we can enable biometrics
                // on android this immediately succeeds
                // on ios, enrollment requires verification of biometric auth
                biometricPrompter.enrollBiometrics()
                  .result
                  .onSuccess {
                    biometricPreference.set(enabled = !isEnabled)
                    uiState = State.EnablingBiometricSetting(isEnabled = !isEnabled)
                  }
                  .onFailure { error ->
                    uiState = State.EnablingBiometricSetting(isEnabled = isEnabled)
                    sheetModel = when (error) {
                      is BiometricError.AuthenticationFailed -> errorSheetModel(
                        headline = "Unable to verify",
                        subline = "We were unable to verify your biometric authentication. Please try again.",
                        onCancel = { sheetModel = null },
                        onBack = { sheetModel = null }
                      )
                      is BiometricError.BiometricsLocked -> errorSheetModel(
                        headline = "Unable to verify",
                        subline = "We were unable to due to your biometrics being locked. Please try again later.",
                        onCancel = { sheetModel = null },
                        onBack = { sheetModel = null }
                      )
                      else -> errorSheetModel(
                        headline = "Unable to enable biometrics.",
                        subline = "Please try again later.",
                        onCancel = { sheetModel = null },
                        onBack = { sheetModel = null }
                      )
                    }
                  }
              } else {
                biometricPreference.set(enabled = !isEnabled)
                uiState = State.EnablingBiometricSetting(isEnabled = !isEnabled)
              }
            } else {
              // we were unable to verify the signature from the hardware so we show an error
              uiState = State.EnablingBiometricSetting(isEnabled = isEnabled)
              sheetModel = errorSheetModel(
                headline = "Unable to verify your Bitkey device",
                subline = "Verify you are using the hardware for this wallet and it is unlocked.",
                onCancel = { sheetModel = null },
                onBack = { sheetModel = null }
              )
            }
          },
          onCancel = { uiState = State.EnablingBiometricSetting() },
          isHardwareFake = props.keybox.config.isHardwareFake,
          needsAuthentication = true,
          screenPresentationStyle = ScreenPresentationStyle.FullScreen,
          eventTrackerContext = NfcEventTrackerScreenIdContext.METADATA
        )
      )
    }
  }
}

private fun biometricSettingScreen(
  props: BiometricSettingUiProps,
  biometricSettingTitleText: String,
  biometricSettingSecondaryText: String,
  coachmark: CoachmarkModel?,
  isEnabled: Boolean,
  onEnableCheckedChange: (Boolean) -> Unit,
  sheetModel: SheetModel?,
): ScreenModel {
  return ScreenModel(
    body = FormBodyModel(
      toolbar = ToolbarModel(
        leadingAccessory = BackAccessory(props.onBack)
      ),
      header = FormHeaderModel(
        headline = "App Security",
        subline = "Unlock the app using fingerprint or facial recognition."
      ),
      mainContentList = immutableListOf(
        FormMainContentModel.ListGroup(
          listGroupModel = ListGroupModel(
            items = immutableListOf(
              ListItemModel(
                title = biometricSettingTitleText,
                secondaryText = biometricSettingSecondaryText,
                trailingAccessory = ListItemAccessory.SwitchAccessory(
                  model = SwitchModel(
                    checked = isEnabled,
                    onCheckedChange = onEnableCheckedChange
                  )
                ),
                coachmark = coachmark
              )
            ),
            style = ListGroupStyle.DIVIDER
          )
        )
      ),
      primaryButton = null,
      onBack = props.onBack,
      id = SettingsEventTrackerScreenId.SETTING_BIOMETRICS
    ),
    bottomSheetModel = sheetModel
  )
}

private fun nfcPromptSheetModel(
  biometricText: String,
  isDisabling: Boolean = false,
  onScanBitkeyDevice: () -> Unit,
  onCancel: () -> Unit,
  onBack: () -> Unit,
): SheetModel {
  val biometricActionText = if (isDisabling) "disable $biometricText" else "enable $biometricText"
  return SheetModel(
    onClosed = onCancel,
    body = FormBodyModel(
      toolbar = null,
      header = FormHeaderModel(
        headline = "Scan your Bitkey device to $biometricActionText",
        subline = "To keep your bitcoin secure, your Bitkey device is required for any security changes."
      ),
      renderContext = RenderContext.Sheet,
      primaryButton = ButtonModel(
        text = "Scan Bitkey Device",
        size = ButtonModel.Size.Footer,
        leadingIcon = Icon.SmallIconBitkey,
        treatment = ButtonModel.Treatment.Black,
        onClick = SheetClosingClick { onScanBitkeyDevice() }
      ),
      secondaryButton = ButtonModel(
        text = "Cancel",
        size = ButtonModel.Size.Footer,
        isEnabled = true,
        treatment = ButtonModel.Treatment.Secondary,
        onClick = SheetClosingClick { onCancel() }
      ),
      onBack = onBack,
      id = null
    )
  )
}

private fun errorSheetModel(
  headline: String,
  subline: String,
  onCancel: () -> Unit,
  onBack: () -> Unit,
): SheetModel {
  return SheetModel(
    onClosed = onCancel,
    body = FormBodyModel(
      toolbar = null,
      header = FormHeaderModel(
        headline = headline,
        subline = subline
      ),
      renderContext = RenderContext.Sheet,
      primaryButton = ButtonModel(
        text = "Ok",
        size = ButtonModel.Size.Footer,
        onClick = SheetClosingClick { onBack() }
      ),
      onBack = onBack,
      id = null
    )
  )
}

private fun notEnrolledErrorSheetModel(
  headline: String,
  subline: String,
  onCancel: () -> Unit,
  onBack: () -> Unit,
  onGoToSettings: () -> Unit,
): SheetModel {
  return SheetModel(
    onClosed = onCancel,
    body = FormBodyModel(
      toolbar = null,
      header = FormHeaderModel(
        headline = headline,
        subline = subline
      ),
      renderContext = RenderContext.Sheet,
      primaryButton = ButtonModel(
        text = "Go to Settings",
        size = ButtonModel.Size.Footer,
        onClick = SheetClosingClick { onGoToSettings() }
      ),
      secondaryButton = ButtonModel(
        text = "Cancel",
        treatment = ButtonModel.Treatment.Secondary,
        size = ButtonModel.Size.Footer,
        onClick = SheetClosingClick { onCancel() }
      ),
      onBack = onBack,
      id = null
    )
  )
}

private sealed interface State {
  /**
   * Setting screen for biometric auth
   *
   * @isEnabled: whether biometric auth is enabled
   */
  data class EnablingBiometricSetting(val isEnabled: Boolean = false) : State

  /**
   * verifying the HW and enrolling in biometric auth
   */
  data object Verifying : State
}
