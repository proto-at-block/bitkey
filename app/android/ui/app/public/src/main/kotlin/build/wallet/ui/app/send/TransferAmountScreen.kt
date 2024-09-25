package build.wallet.ui.app.send

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement.Center
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.keypad.KeypadModel
import build.wallet.statemachine.limit.SpendingLimitsCopy
import build.wallet.statemachine.money.amount.MoneyAmountEntryModel
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.send.TransferAmountBodyModel
import build.wallet.statemachine.send.TransferScreenBannerModel.AmountEqualOrAboveBalanceBannerModel
import build.wallet.statemachine.send.TransferScreenBannerModel.HardwareRequiredBannerModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.components.amount.HeroAmount
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.card.Card
import build.wallet.ui.components.card.CardContent
import build.wallet.ui.components.keypad.Keypad
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.components.toolbar.rememberConditionally
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.PreviewWalletTheme

@Composable
fun TransferAmountScreen(model: TransferAmountBodyModel) {
  val horizontalPadding = 20.dp
  FormScreen(
    onBack = model.onBack,
    horizontalPadding = 0, // Manually apply padding so keypad can extend to edges
    toolbarContent = {
      Toolbar(
        modifier = Modifier.padding(horizontal = horizontalPadding),
        model = model.toolbar
      )
    },
    mainContent = {
      Spacer(Modifier.weight(1F))
      HeroAmount(
        modifier =
          Modifier.align(CenterHorizontally)
            .padding(horizontal = 20.dp)
            .clipToBounds(),
        primaryAmount =
          buildAnnotatedString {
            append(model.amountModel.primaryAmount)
            model.amountModel.primaryAmountGhostedSubstringRange?.let { substringRange ->
              addStyle(
                style = SpanStyle(color = WalletTheme.colors.foreground30),
                start = substringRange.first,
                end = substringRange.last + 1
              )
            }
          },
        primaryAmountLabelType = LabelType.Display1,
        secondaryAmountWithCurrency = model.amountModel.secondaryAmount,
        onSwapClick = model.onSwapCurrencyClick,
        disabled = model.amountDisabled
      )
      Spacer(Modifier.height(16.dp))

      model.cardModel?.let {
        SmartBar(
          modifier = Modifier.align(CenterHorizontally),
          model = it
        )
      }

      Spacer(Modifier.weight(1F))

      Keypad(
        showDecimal = model.keypadModel.showDecimal,
        onButtonPress = model.keypadModel.onButtonPress
      )
    },
    footerContent = {
      Button(
        modifier = Modifier.padding(horizontal = horizontalPadding),
        model = model.primaryButton
      )
    }
  )
}

/**
 * A design primitive within the transfer amount screen that allows us to surface actionable behaviours
 * based on the user's context.
 *
 * For instance, the Smart Bar will be able to tell users when the amount they enter would require a
 * HW tap, or when the user looks like they are trying to sweep their wallet. It leverages the same
 * underlying [CardModel] that other card primitives also use today.
 */
@Composable
private fun SmartBar(
  modifier: Modifier = Modifier,
  model: CardModel?,
) {
  Box(
    // banner space is always taken up even when it's not visible.
    // This is a workaround so that the amount above the banner doesn't jump when the banner changes visibility.
    modifier =
      modifier.height(48.dp)
        .clickable(
          interactionSource = MutableInteractionSource(),
          indication = null,
          onClick = { model?.onClick?.invoke() }
        ),
    contentAlignment = Alignment.Center
  ) {
    val showBanner = model != null
    // https://stackoverflow.com/a/73282996/16459196
    val bannerModel = rememberConditionally(condition = showBanner) { model }
    AnimatedVisibility(
      visible = showBanner,
      enter = fadeIn() + slideInVertically(),
      exit = fadeOut() + slideOutVertically()
    ) {
      bannerModel?.let {
        Card(
          modifier = Modifier.fillMaxHeight(),
          verticalArrangement = Center
        ) {
          CardContent(
            model = it
          )
        }
      }
    }
  }
}

@Preview
@Composable
internal fun TransferAmountScreenNoEntryPreview() {
  PreviewWalletTheme {
    TransferAmountScreen(
      TransferAmountBodyModel(
        onBack = {},
        balanceTitle = "$961.24 available",
        amountModel =
          MoneyAmountEntryModel(
            primaryAmount = "$0.00",
            primaryAmountGhostedSubstringRange = null,
            secondaryAmount = "0 sats"
          ),
        bannerModel = null,
        keypadModel =
          KeypadModel(
            showDecimal = false,
            onButtonPress = {}
          ),
        continueButtonEnabled = true,
        amountDisabled = false,
        f8eUnavailableWarningString = SpendingLimitsCopy.get(isRevampOn = false).transferScreenUnavailableWarning,
        onContinueClick = {},
        onSwapCurrencyClick = {},
        onSendMaxClick = {},
        onHardwareRequiredClick = {}
      )
    )
  }
}

@Preview
@Composable
internal fun TransferAmountScreenWithEntryPreview() {
  PreviewWalletTheme {
    TransferAmountScreen(
      TransferAmountBodyModel(
        onBack = {},
        balanceTitle = "$961.24 available",
        amountModel =
          MoneyAmountEntryModel(
            primaryAmount = "$4.00",
            primaryAmountGhostedSubstringRange = null,
            secondaryAmount = "70,000 sats"
          ),
        bannerModel = null,
        keypadModel =
          KeypadModel(
            showDecimal = false,
            onButtonPress = {}
          ),
        continueButtonEnabled = true,
        amountDisabled = false,
        f8eUnavailableWarningString = SpendingLimitsCopy.get(isRevampOn = false).transferScreenUnavailableWarning,
        onContinueClick = {},
        onSwapCurrencyClick = {},
        onSendMaxClick = {},
        onHardwareRequiredClick = {}
      )
    )
  }
}

@Preview
@Composable
internal fun TransferAmountScreenWithBannerPreview() {
  PreviewWalletTheme {
    TransferAmountScreen(
      TransferAmountBodyModel(
        onBack = {},
        balanceTitle = "$961.24 available",
        amountModel =
          MoneyAmountEntryModel(
            primaryAmount = "$4.00",
            primaryAmountGhostedSubstringRange = null,
            secondaryAmount = "70,000 sats"
          ),
        bannerModel = HardwareRequiredBannerModel,
        keypadModel =
          KeypadModel(
            showDecimal = false,
            onButtonPress = {}
          ),
        continueButtonEnabled = true,
        amountDisabled = false,
        f8eUnavailableWarningString = SpendingLimitsCopy.get(isRevampOn = false).transferScreenUnavailableWarning,
        onContinueClick = {},
        onSwapCurrencyClick = {},
        onSendMaxClick = {},
        onHardwareRequiredClick = {}
      )
    )
  }
}

@Preview
@Composable
internal fun TransferAmountScreenWithSmartBarPreview() {
  PreviewWalletTheme {
    TransferAmountScreen(
      TransferAmountBodyModel(
        onBack = {},
        balanceTitle = "$961.24 available",
        amountModel =
          MoneyAmountEntryModel(
            primaryAmount = "$4.00",
            primaryAmountGhostedSubstringRange = null,
            secondaryAmount = "70,000 sats"
          ),
        bannerModel = HardwareRequiredBannerModel,
        keypadModel =
          KeypadModel(
            showDecimal = false,
            onButtonPress = {}
          ),
        continueButtonEnabled = true,
        amountDisabled = false,
        f8eUnavailableWarningString = SpendingLimitsCopy.get(isRevampOn = false).transferScreenUnavailableWarning,
        onContinueClick = {},
        onSwapCurrencyClick = {},
        onSendMaxClick = {},
        onHardwareRequiredClick = {}
      )
    )
  }
}

@Preview
@Composable
internal fun TransferAmountScreenWithEqualOrMoreBannerPreview() {
  PreviewWalletTheme {
    TransferAmountScreen(
      TransferAmountBodyModel(
        onBack = {},
        balanceTitle = "$961.24 available",
        amountModel =
          MoneyAmountEntryModel(
            primaryAmount = "$4.00",
            primaryAmountGhostedSubstringRange = null,
            secondaryAmount = "70,000 sats"
          ),
        bannerModel = AmountEqualOrAboveBalanceBannerModel,
        keypadModel =
          KeypadModel(
            showDecimal = false,
            onButtonPress = {}
          ),
        continueButtonEnabled = false,
        amountDisabled = true,
        f8eUnavailableWarningString = SpendingLimitsCopy.get(isRevampOn = false).transferScreenUnavailableWarning,
        onContinueClick = {},
        onSwapCurrencyClick = {},
        onSendMaxClick = {},
        onHardwareRequiredClick = {}
      )
    )
  }
}
