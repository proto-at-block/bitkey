@file:Suppress("detekt:TooManyFunctions")

package build.wallet.ui.app.core.form

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.Incoming
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.Outgoing
import build.wallet.compose.collections.immutableListOf
import build.wallet.money.currency.EUR
import build.wallet.money.currency.GBP
import build.wallet.money.currency.USD
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel.StringModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.statemachine.core.form.formBodyModel
import build.wallet.statemachine.core.list.ListFormBodyModel
import build.wallet.statemachine.money.currency.AppearancePreferenceFormModel
import build.wallet.statemachine.money.currency.FiatCurrencyListFormModel
import build.wallet.statemachine.transactions.TransactionItemModel
import build.wallet.ui.components.label.Label
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.callout.CalloutModel
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.input.TextFieldModel
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemPickerMenu
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun FormScreenAllContentsPreview() {
  PreviewWalletTheme {
    FormScreen(
      onBack = null,
      toolbarContent = {
        ToolbarPlaceholder()
      },
      headerContent = {
        HeaderPlaceholder()
      },
      mainContent = {
        MainContentPlaceholder()
      },
      footerContent = {
        FooterPlaceholder()
      }
    )
  }
}

@Preview
@Composable
fun FormScreenAllContentsNoToolbarPreview() {
  PreviewWalletTheme {
    FormScreen(
      onBack = null,
      toolbarContent = null,
      headerContent = {
        HeaderPlaceholder()
      },
      mainContent = {
        MainContentPlaceholder()
      },
      footerContent = {
        FooterPlaceholder()
      }
    )
  }
}

@Preview
@Composable
fun FormScreenAllContentsNoMainContentPreview() {
  PreviewWalletTheme {
    FormScreen(
      onBack = null,
      toolbarContent = {
        ToolbarPlaceholder()
      },
      headerContent = {
        HeaderPlaceholder()
      },
      mainContent = null,
      footerContent = {
        FooterPlaceholder()
      }
    )
  }
}

@Preview
@Composable
fun FormScreenAllContentsNoFooterPreview() {
  PreviewWalletTheme {
    FormScreen(
      onBack = null,
      toolbarContent = {
        ToolbarPlaceholder()
      },
      headerContent = {
        HeaderPlaceholder()
      },
      mainContent = {
        MainContentPlaceholder()
      },
      footerContent = null
    )
  }
}

@Preview
@Composable
fun FormScreenAllContentsNoMainAndFooterContentPreview() {
  PreviewWalletTheme {
    FormScreen(
      onBack = null,
      toolbarContent = {
        ToolbarPlaceholder()
      },
      headerContent = {
        HeaderPlaceholder()
      },
      mainContent = null,
      footerContent = null
    )
  }
}

@Preview
@Composable
fun FormScreenNotFullHeightPreview() {
  PreviewWalletTheme {
    FormScreen(
      onBack = null,
      toolbarContent = {
        ToolbarPlaceholder()
      },
      headerContent = {
        HeaderPlaceholder()
      },
      mainContent = {
        MainContentPlaceholder()
      },
      footerContent = {
        FooterPlaceholder()
      },
      renderContext = RenderContext.Sheet
    )
  }
}

@Composable
private fun ToolbarPlaceholder() {
  ContentPlaceholder(
    borderColor = Color.Red,
    label = "Toolbar Content"
  )
}

@Composable
private fun HeaderPlaceholder() {
  ContentPlaceholder(
    borderColor = Color.Blue,
    label = "Header Content"
  )
}

@Composable
private fun MainContentPlaceholder() {
  ContentPlaceholder(
    borderColor = Color.Yellow,
    label = "Main Content"
  )
}

@Composable
private fun FooterPlaceholder() {
  ContentPlaceholder(
    borderColor = Color.Green,
    label = "Footer Content"
  )
}

@Composable
private fun ContentPlaceholder(
  borderColor: Color,
  label: String,
) {
  Box(
    modifier =
      Modifier
        .fillMaxWidth()
        .border(width = 2.dp, color = borderColor),
    contentAlignment = Alignment.Center
  ) {
    Label(text = label, type = LabelType.Title2)
  }
}

@Preview
@Composable
internal fun screenWithHeaderAndPrimaryButton() {
  FormScreen(
    model = formBodyModel(
      id = null,
      onBack = null,
      toolbar = null,
      header = FormHeaderModel(
        icon = Icon.LargeIconCheckFilled,
        headline = "title",
        subline = "message"
      ),
      primaryButton = ButtonModel(
        text = "primaryButtonModel",
        size = ButtonModel.Size.Footer,
        onClick = StandardClick {}
      )
    )
  )
}

@Preview
@Composable
internal fun PreviewMobilePaySheetScreen() {
  FormScreen(
    model = formBodyModel(
      id = null,
      onBack = { },
      toolbar = null,
      header =
        FormHeaderModel(
          iconModel = IconModel(
            icon = Icon.SmallIconPhone,
            iconSize = IconSize.Large,
            iconTint = IconTint.Primary,
            iconBackgroundType = IconBackgroundType.Circle(
              circleSize = IconSize.Avatar,
              color = IconBackgroundType.Circle.CircleColor.PrimaryBackground20
            ),
            iconTopSpacing = 0
          ),
          headline = "Mobile pay",
          alignment = FormHeaderModel.Alignment.LEADING,
          subline =
            "Leave your device at home, and make small spends with just the key on your phone."
        ),
      primaryButton =
        ButtonModel(
          text = "Enable Mobile Pay",
          size = ButtonModel.Size.Footer,
          onClick = StandardClick {}
        ),
      secondaryButton =
        ButtonModel(
          text = "Set up later",
          size = ButtonModel.Size.Footer,
          treatment = ButtonModel.Treatment.Secondary,
          onClick = StandardClick {}
        ),
      renderContext = RenderContext.Sheet
    )
  )
}

@Preview
@Composable
fun PreviewFeeOptionsFormScreen() {
  FormScreen(
    model =
      formBodyModel(
        onBack = {},
        toolbar =
          ToolbarModel(
            leadingAccessory =
              BackAccessory(onClick = {})
          ),
        header =
          FormHeaderModel(
            icon = Icon.LargeIconSpeedometer,
            headline = "Select a transfer speed",
            alignment = FormHeaderModel.Alignment.CENTER
          ),
        mainContentList =
          immutableListOf(
            FormMainContentModel.FeeOptionList(
              options =
                immutableListOf(
                  FormMainContentModel.FeeOptionList.FeeOption(
                    optionName = "Priority",
                    transactionTime = "~10 mins",
                    transactionFee = "$0.33 (1,086 sats)",
                    selected = false,
                    enabled = true,
                    onClick = {}
                  ),
                  FormMainContentModel.FeeOptionList.FeeOption(
                    optionName = "Standard",
                    transactionTime = "~30 mins",
                    transactionFee = "$0.22 (1,086 sats)",
                    selected = true,
                    enabled = true,
                    onClick = {}
                  ),
                  FormMainContentModel.FeeOptionList.FeeOption(
                    optionName = "Slow",
                    transactionTime = "~60 mins",
                    transactionFee = "$0.11 (1,086 sats)",
                    selected = false,
                    enabled = true,
                    onClick = {}
                  )
                )
            )
          ),
        primaryButton =
          ButtonModel(
            text = "Continue",
            size = ButtonModel.Size.Footer,
            onClick = StandardClick {}
          ),
        id = null
      )
  )
}

@Preview
@Composable
fun SetCustomElectrumFormScreenPreview() {
  FormScreen(
    model =
      formBodyModel(
        id = null,
        onBack = {},
        header =
          FormHeaderModel(
            headline = "Change Electrum Server",
            subline = "Provide details for a custom Electrum Server: "
          ),
        mainContentList =
          immutableListOf(
            FormMainContentModel.TextInput(
              title = "Server:",
              fieldModel =
                TextFieldModel(
                  value = "",
                  placeholderText = "example.com",
                  onValueChange = { _, _ -> },
                  keyboardType = TextFieldModel.KeyboardType.Uri
                )
            ),
            FormMainContentModel.TextInput(
              title = "Port:",
              fieldModel =
                TextFieldModel(
                  value = "",
                  placeholderText = "50002",
                  onValueChange = { _, _ -> },
                  keyboardType = TextFieldModel.KeyboardType.Decimal
                )
            )
          ),
        toolbar =
          ToolbarModel(
            leadingAccessory = ToolbarAccessoryModel.IconAccessory.CloseAccessory {}
          ),
        primaryButton =
          ButtonModel(
            text = "Save",
            isEnabled = true,
            onClick = StandardClick {},
            size = ButtonModel.Size.Footer
          )
      )
  )
}

@Preview
@Composable
internal fun CurrencyListPreview() {
  FormScreen(
    model =
      FiatCurrencyListFormModel(
        onClose = {},
        selectedCurrency = USD,
        currencyList = listOf(USD, GBP, EUR),
        onCurrencySelection = {}
      )
  )
}

@Preview
@Composable
fun AppearancePreferencePreview() {
  FormScreen(
    model =
      AppearancePreferenceFormModel(
        onBack = {},
        moneyHomeHero = FormMainContentModel.MoneyHomeHero("$0", "0 sats"),
        fiatCurrencyPreferenceString = "USD",
        onFiatCurrencyPreferenceClick = {},
        bitcoinDisplayPreferenceString = "Sats",
        bitcoinDisplayPreferencePickerModel = CurrencyPreferenceListItemPickerMenu,
        onBitcoinDisplayPreferenceClick = {},
        onEnableHideBalanceChanged = {}
      )
  )
}

val CurrencyPreferenceListItemPickerMenu =
  ListItemPickerMenu(
    isShowing = false,
    selectedOption = "Sats",
    options = listOf("Sats", "Bitcoin"),
    onOptionSelected = {},
    onDismiss = {}
  )

@Preview
@Composable
fun AllTransactionsPreview() {
  FormScreen(
    model =
      ListFormBodyModel(
        toolbarTitle = "Activity",
        listGroups = immutableListOf(
          ListGroupModel(
            header = null,
            style = ListGroupStyle.NONE,
            items =
              immutableListOf(
                TransactionItemModel(
                  truncatedRecipientAddress = "1AH7...CkGJ",
                  date = "Apr 6 at 12:20 pm",
                  amount = "+ $11.36",
                  amountEquivalent = "0.000105 BTC",
                  transactionType = Incoming,
                  isPending = true,
                  isLate = false,
                  onClick = {}
                ),
                TransactionItemModel(
                  truncatedRecipientAddress = "2AH7...CkGJ",
                  date = "Apr 6 at 12:20 pm",
                  amount = "$21.36",
                  amountEquivalent = "0.000205 BTC",
                  transactionType = Outgoing,
                  isPending = true,
                  isLate = true,
                  onClick = {}
                )
              )
          ),
          ListGroupModel(
            header = null,
            style = ListGroupStyle.NONE,
            items =
              immutableListOf(
                TransactionItemModel(
                  truncatedRecipientAddress = "3AH7...CkGJ",
                  date = "Pending",
                  amount = "+ $11.36",
                  amountEquivalent = "0.000105 BTC",
                  transactionType = Incoming,
                  isPending = false,
                  isLate = false,
                  onClick = {}
                ),
                TransactionItemModel(
                  truncatedRecipientAddress = "4AH7...CkGJ",
                  date = "Pending",
                  amount = "$21.36",
                  amountEquivalent = "0.000205 BTC",
                  transactionType = Outgoing,
                  isPending = false,
                  isLate = false,
                  onClick = {}
                )
              )
          )
        ),
        onBack = {},
        id = null
      )
  )
}

@Preview
@Composable
fun CalloutPreview() {
  FormScreen(
    model = formBodyModel(
      id = null,
      onBack = {},
      toolbar = null,
      header = null,
      mainContentList =
        immutableListOf(
          FormMainContentModel.Callout(
            item = CalloutModel(
              title = "At least one fingerprint is required",
              subtitle = StringModel("Add another fingerprint to delete"),
              leadingIcon = Icon.SmallIconInformationFilled,
              treatment = CalloutModel.Treatment.Information
            )
          )
        ),
      primaryButton = null
    )
  )
}
