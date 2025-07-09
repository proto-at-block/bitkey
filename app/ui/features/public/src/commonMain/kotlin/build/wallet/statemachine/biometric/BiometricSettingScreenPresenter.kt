package build.wallet.statemachine.biometric

import androidx.compose.runtime.*
import bitkey.ui.framework.Navigator
import bitkey.ui.framework.Screen
import bitkey.ui.framework.ScreenPresenter
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.SettingsEventTrackerScreenId
import build.wallet.bitkey.account.FullAccount
import build.wallet.compose.collections.immutableListOf
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.encrypt.SignatureVerifier
import build.wallet.encrypt.verifyEcdsaResult
import build.wallet.feature.isEnabled
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
import okio.ByteString.Companion.encodeUtf8

const val BIOMETRIC_AUTH_CHALLENGE = "biometric-auth-challenge"

/**
 * The Props for launching [BiometricSettingUiStateMachine]
 */
data class BiometricSettingScreen(
  val fullAccount: FullAccount,
  override val origin: Screen?,
) : Screen

@BitkeyInject(ActivityScope::class)
class BiometricSettingScreenPresenter(
  private val biometricPreference: BiometricPreference,
  private val biometricTextProvider: BiometricTextProvider,
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val biometricPrompter: BiometricPrompter,
  private val signatureVerifier: SignatureVerifier,
  private val settingsLauncher: SystemSettingsLauncher,
) : ScreenPresenter<BiometricSettingScreen> {
  @Composable
  override fun model(
    navigator: Navigator,
    screen: BiometricSettingScreen,
  ): ScreenModel {
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

    var sheetModel: SheetModel? by remember { mutableStateOf(null) }
    val biometricTitle = biometricTextProvider.getSettingsTitleText()
    return when (uiState) {
      is State.EnablingBiometricSetting -> BiometricSettingsScreenBodyModel(
        onBack = {
          if (screen.origin != null) {
            navigator.goTo(screen.origin)
          } else {
            navigator.exit()
          }
        },
        isEnabled = isEnabled,
        biometricSettingTitleText = biometricTitle,
        biometricSettingSecondaryText = biometricTextProvider.getSettingsSecondaryText(),
        appSecurityDescriptionText = biometricTextProvider.getAppSecurityDescriptionText(),
        onEnableCheckedChange = {
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
                  is BiometricError.BiometricsLocked -> ErrorSheetBodyModel(
                    headline = "Unable to enable biometrics",
                    subline = "Your biometrics are locked. Please unlock your device and try again.",
                    onBack = { sheetModel = null }
                  ).asSheetModalScreen(onClosed = { sheetModel = null })
                  is BiometricError.NoBiometricEnrolled -> NotEnrolledErrorSheetBodyModel(
                    headline = "No Biometric is enrolled",
                    subline = "Please visit system settings to enable biometrics.",
                    onCancel = { sheetModel = null },
                    onBack = { sheetModel = null },
                    onGoToSettings = {
                      sheetModel = null
                      settingsLauncher.launchSecuritySettings()
                    }
                  ).asSheetModalScreen(
                    onClosed = {
                      sheetModel = null
                    }
                  )
                  is BiometricError.NoHardware -> ErrorSheetBodyModel(
                    headline = "No Biometric hardware found",
                    subline = "Please verify your phone has biometric hardware.",
                    onBack = { sheetModel = null }
                  ).asSheetModalScreen(onClosed = { sheetModel = null })
                  else -> ErrorSheetBodyModel(
                    headline = "Unable to enable biometrics.",
                    subline = "Please try again later.",
                    onBack = { sheetModel = null }
                  ).asSheetModalScreen(onClosed = { sheetModel = null })
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
        }
      ).asRootScreen(
        bottomSheetModel = sheetModel
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
              publicKey = screen.fullAccount.keybox.activeHwKeyBundle.authKey.pubKey
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
                      is BiometricError.AuthenticationFailed -> ErrorSheetBodyModel(
                        headline = "Unable to verify",
                        subline = "We were unable to verify your biometric authentication. Please try again.",
                        onBack = { sheetModel = null }
                      ).asSheetModalScreen(onClosed = { sheetModel = null })
                      is BiometricError.BiometricsLocked -> ErrorSheetBodyModel(
                        headline = "Unable to verify",
                        subline = "We were unable to due to your biometrics being locked. Please try again later.",
                        onBack = { sheetModel = null }
                      ).asSheetModalScreen(onClosed = { sheetModel = null })
                      else -> ErrorSheetBodyModel(
                        headline = "Unable to enable biometrics.",
                        subline = "Please try again later.",
                        onBack = { sheetModel = null }
                      ).asSheetModalScreen(onClosed = { sheetModel = null })
                    }
                  }
              } else {
                biometricPreference.set(enabled = !isEnabled)
                uiState = State.EnablingBiometricSetting(isEnabled = !isEnabled)
              }
            } else {
              // we were unable to verify the signature from the hardware so we show an error
              uiState = State.EnablingBiometricSetting(isEnabled = isEnabled)
              sheetModel = ErrorSheetBodyModel(
                headline = "Unable to verify your Bitkey device",
                subline = "Verify you are using the hardware for this wallet and it is unlocked.",
                onBack = { sheetModel = null }
              ).asSheetModalScreen(onClosed = { sheetModel = null })
            }
          },
          onCancel = { uiState = State.EnablingBiometricSetting() },
          needsAuthentication = true,
          screenPresentationStyle = ScreenPresentationStyle.FullScreen,
          eventTrackerContext = NfcEventTrackerScreenIdContext.METADATA
        )
      )
    }
  }
}

internal data class BiometricSettingsScreenBodyModel(
  override val onBack: () -> Unit,
  val biometricSettingTitleText: String,
  val biometricSettingSecondaryText: String,
  val appSecurityDescriptionText: String,
  val isEnabled: Boolean,
  val onEnableCheckedChange: (Boolean) -> Unit,
) : FormBodyModel(
    toolbar = ToolbarModel(
      leadingAccessory = BackAccessory(onBack)
    ),
    header = FormHeaderModel(
      headline = "App Security",
      subline = appSecurityDescriptionText
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
              )
            )
          ),
          style = ListGroupStyle.DIVIDER
        )
      )
    ),
    primaryButton = null,
    onBack = onBack,
    id = SettingsEventTrackerScreenId.SETTING_BIOMETRICS
  )

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
    body = NfcPromptSheetBodyModel(
      biometricActionText = biometricActionText,
      onScanBitkeyDevice = onScanBitkeyDevice,
      onCancel = onCancel,
      onBack = onBack
    )
  )
}

internal data class NfcPromptSheetBodyModel(
  val biometricActionText: String,
  val onScanBitkeyDevice: () -> Unit,
  val onCancel: () -> Unit,
  override val onBack: () -> Unit,
) : FormBodyModel(
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
      treatment = ButtonModel.Treatment.BitkeyInteraction,
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

internal data class ErrorSheetBodyModel(
  val headline: String,
  val subline: String,
  override val onBack: () -> Unit,
) : FormBodyModel(
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

internal data class NotEnrolledErrorSheetBodyModel(
  val headline: String,
  val subline: String,
  val onCancel: () -> Unit,
  override val onBack: () -> Unit,
  val onGoToSettings: () -> Unit,
) : FormBodyModel(
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
