package bitkey.ui.screens.securityhub.education

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import bitkey.ui.screens.securityhub.SecurityHubEventTrackerScreenId.SECURITY_HUB_EDUCATION_PROVISION_APP_KEY
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.tokens.LabelType

/**
 * Education shown before provisioning the app auth key to hardware.
 *
 * The provisioning step used to happen silently at the end of a firmware update. Customers
 * who updated before that step existed still need a single NFC tap to finish, so this
 * explains that a tap is needed before sending them into the NFC flow.
 */
data class ProvisionAppKeyEducationBodyModel(
  override val onBack: () -> Unit,
  val onContinue: () -> Unit,
) : FormBodyModel(
    id = SECURITY_HUB_EDUCATION_PROVISION_APP_KEY,
    onBack = onBack,
    toolbar = null,
    header = FormHeaderModel(
      iconModel = IconModel(
        icon = Icon.DotBitkey,
        iconSize = IconSize.Custom(48),
        iconTint = IconTint.Background,
        iconBackgroundType = IconBackgroundType.Circle(
          circleSize = IconSize.XLarge,
          color = IconBackgroundType.Circle.CircleColor.InverseBackground
        )
      ),
      headline = "Additional security features",
      subline = "Your Bitkey device supports security features that need one more step to turn on. " +
        "Hold your device to your phone to finish setting them up.",
      headlineLabelType = LabelType.Body1Mono
    ),
    primaryButton = ButtonModel(
      text = "Continue",
      requiresBitkeyInteraction = false,
      treatment = ButtonModel.Treatment.Primary,
      size = ButtonModel.Size.Footer,
      onClick = onContinue
    ),
    secondaryButton = ButtonModel(
      text = "Set up later",
      requiresBitkeyInteraction = false,
      treatment = ButtonModel.Treatment.Secondary,
      size = ButtonModel.Size.Footer,
      onClick = onBack
    )
  ) {
  @Composable
  override fun render(modifier: Modifier) {
    FormScreen(this)
  }
}
