package build.wallet.ui.components.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import bitkey.ui.screens.securityhub.SecurityHubBodyModel
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.ScreenPresentationStyle.*
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.inheritance.InheritanceUpsellBodyModel
import build.wallet.statemachine.partnerships.purchase.CustomAmountBodyModel
import build.wallet.statemachine.receive.AddressQrCodeBodyModel
import build.wallet.statemachine.receivev2.AddressQrCodeV2BodyModel
import build.wallet.statemachine.send.TransferAmountBodyModel
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.Theme.LIGHT
import build.wallet.ui.theme.WalletTheme

// These body models are sometimes presented as full screen, but they have toolbars,
// so they need system bar padding to their toolbar.
private val bodyModelsRequiringPadding = setOf(
  AddressQrCodeBodyModel::class,
  AddressQrCodeV2BodyModel::class,
  TransferAmountBodyModel::class,
  CustomAmountBodyModel::class
)

private val presentationStylesWithoutPadding = setOf(
  RootFullScreen,
  FullScreen,
  ModalFullScreen
)

private fun doesScreenRequirePadding(
  bodyModel: BodyModel,
  presentationStyle: ScreenPresentationStyle,
): Boolean {
  return bodyModel::class in bodyModelsRequiringPadding ||
    // For all other body models, add / don't add padding based on the presentation style
    presentationStyle !in presentationStylesWithoutPadding ||
    FormBodyModel::class.isInstance(bodyModel)
}

/**
 * Describes style of the system UI components.
 *
 * @param useDarkSystemBarIcons whether to use dark icons for the system bars.
 * @param addSystemBarsPadding whether to add system bars padding to the screen.
 */
data class ScreenStyle(
  val useDarkSystemBarIcons: Boolean,
  val addSystemBarsPadding: Boolean,
  val statusBarColor: Color,
  val screenBackgroundColor: Color,
)

/**
 * Construct [ScreenStyle] based on given [colorMode] and [presentationStyle].
 */
@Composable
internal fun screenStyle(
  bodyModel: BodyModel,
  presentationStyle: ScreenPresentationStyle,
): ScreenStyle {
  val theme = LocalTheme.current
  // This is a one off for the security hub to have the correct status bar color
  val statusBarColor = if (bodyModel is SecurityHubBodyModel) {
    WalletTheme.colors.secondary
  } else if (bodyModel is InheritanceUpsellBodyModel) {
    WalletTheme.colors.inheritanceSurface
  } else {
    WalletTheme.colors.background
  }

  val screenBackgroundColor = if (bodyModel is InheritanceUpsellBodyModel) {
    WalletTheme.colors.inheritanceSurface
  } else {
    WalletTheme.colors.background
  }

  return ScreenStyle(
    useDarkSystemBarIcons = theme == LIGHT,
    addSystemBarsPadding = doesScreenRequirePadding(bodyModel, presentationStyle),
    statusBarColor = statusBarColor,
    screenBackgroundColor = screenBackgroundColor
  )
}
