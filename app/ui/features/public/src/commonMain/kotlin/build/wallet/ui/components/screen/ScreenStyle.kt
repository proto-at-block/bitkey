package build.wallet.ui.components.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import bitkey.ui.screens.securityhub.SecurityHubBodyModel
import build.wallet.statemachine.account.ChooseAccountAccessModel
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.ScreenPresentationStyle.*
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.limit.picker.SpendingLimitPickerModel
import build.wallet.statemachine.nfc.NfcBodyModel
import build.wallet.statemachine.partnerships.purchase.CustomAmountBodyModel
import build.wallet.statemachine.receive.AddressQrCodeBodyModel
import build.wallet.statemachine.send.TransferAmountBodyModel
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.Theme.LIGHT
import build.wallet.ui.theme.WalletTheme

// These body models are sometimes presented as full screen, but they have toolbars,
// so they need system bar padding to their toolbar.
private val bodyModelsRequiringPadding = setOf(
  AddressQrCodeBodyModel::class,
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
  return bodyModel.screenStyle.requiresSystemBarsPadding ||
    bodyModel::class in bodyModelsRequiringPadding ||
    presentationStyle !in presentationStylesWithoutPadding ||
    FormBodyModel::class.isInstance(bodyModel)
}

private fun isAmountEntryBodyModel(bodyModel: BodyModel): Boolean {
  return bodyModel is TransferAmountBodyModel ||
    bodyModel is CustomAmountBodyModel ||
    bodyModel is SpendingLimitPickerModel
}

internal fun usesBlackFullscreenBackground(
  bodyModel: BodyModel,
): Boolean {
  return bodyModel.screenStyle.usesBlackFullscreenBackground ||
    (bodyModel is ChooseAccountAccessModel) ||
    bodyModel is PairNewHardwareBodyModel ||
    bodyModel is NfcBodyModel
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
  hasStatusBanner: Boolean = false,
): ScreenStyle {
  val theme = LocalTheme.current
  val amountEntryBackgroundColor =
    if (isAmountEntryBodyModel(bodyModel)) {
      WalletTheme.colors.subtleBackground
    } else {
      WalletTheme.colors.background
    }
  val statusBarColor = when {
    usesBlackFullscreenBackground(
      bodyModel = bodyModel
    ) -> Color.Black
    bodyModel is SecurityHubBodyModel && bodyModel.isOffline -> WalletTheme.colors.background
    bodyModel is SecurityHubBodyModel -> WalletTheme.colors.background
    isAmountEntryBodyModel(bodyModel) -> amountEntryBackgroundColor
    else -> WalletTheme.colors.background
  }

  val screenBackgroundColor = when {
    bodyModel.screenStyle.usesThemeBackgroundForScreenContainer -> WalletTheme.colors.background
    usesBlackFullscreenBackground(
      bodyModel = bodyModel
    ) -> Color.Black
    isAmountEntryBodyModel(bodyModel) -> amountEntryBackgroundColor
    else -> WalletTheme.colors.background
  }

  // When there's a status banner in light mode, the banner has a dark
  // (inverse) background, so the status bar icons should be light to contrast it.
  val hasDarkBanner = hasStatusBanner && theme == LIGHT

  return ScreenStyle(
    useDarkSystemBarIcons =
      theme == LIGHT && !hasDarkBanner && !usesBlackFullscreenBackground(bodyModel),
    addSystemBarsPadding = doesScreenRequirePadding(bodyModel, presentationStyle),
    statusBarColor = statusBarColor,
    screenBackgroundColor = screenBackgroundColor
  )
}
