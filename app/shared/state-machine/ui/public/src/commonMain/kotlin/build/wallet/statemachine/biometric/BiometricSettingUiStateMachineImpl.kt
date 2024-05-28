package build.wallet.statemachine.biometric

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.SettingsEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.encrypt.SignatureVerifier
import build.wallet.encrypt.verifyEcdsaResult
import build.wallet.inappsecurity.BiometricPreference
import build.wallet.isOk
import build.wallet.nfc.platform.signChallenge
import build.wallet.platform.biometrics.BiometricPrompter
import build.wallet.platform.biometrics.BiometricTextProvider
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

class BiometricSettingUiStateMachineImpl(
  private val biometricPreference: BiometricPreference,
  private val biometricTextProvider: BiometricTextProvider,
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val biometricPrompter: BiometricPrompter,
  private val signatureVerifier: SignatureVerifier,
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

    var sheetModel: SheetModel? by remember { mutableStateOf(null) }

    return when (uiState) {
      is State.EnablingBiometricSetting -> biometricSettingScreen(
        props = props,
        isEnabled = isEnabled,
        biometricSettingTitleText = biometricTextProvider.getSettingsTitleText(),
        biometricSettingSecondaryText = biometricTextProvider.getSettingsSecondaryText(),
        onEnableCheckedChange = {
          if (!isEnabled) {
            val biometricsAvailability = biometricPrompter.biometricsAvailability().result
            if (biometricsAvailability.isOk()) {
              sheetModel = nfcPromptSheetModel(
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
            } else {
              // TODO W-8192 Handle unable to enable biometrics flows
            }
          } else {
            sheetModel = nfcPromptSheetModel(
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
              // the signed challenged was verified so we can enable biometrics
              // on android this immediately succeeds
              // on ios, enrollment requires verification of biometric auth
              biometricPrompter.enrollBiometrics()
                .result
                .onSuccess {
                  biometricPreference.set(enabled = !isEnabled)
                  uiState = State.EnablingBiometricSetting(isEnabled = !isEnabled)
                }
                .onFailure {
                  uiState = State.EnablingBiometricSetting(isEnabled = isEnabled)
                  // TODO W-8192 Handle unable to enroll biometrics flows
                }
            } else {
              // we were unable to verify the signature from the hardware so we show an error
              uiState = State.EnablingBiometricSetting(isEnabled = isEnabled)
              sheetModel = errorSheetModel(
                headline = "Unable to verify your Bitkey device.",
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
                )
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
  onScanBitkeyDevice: () -> Unit,
  onCancel: () -> Unit,
  onBack: () -> Unit,
): SheetModel {
  return SheetModel(
    onClosed = onCancel,
    body = FormBodyModel(
      toolbar = null,
      header = FormHeaderModel(
        headline = "Scan your Bitkey device to enable Face ID.",
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

sealed interface State {
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
