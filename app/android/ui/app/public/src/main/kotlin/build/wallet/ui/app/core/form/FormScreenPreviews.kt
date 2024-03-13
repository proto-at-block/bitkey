package build.wallet.ui.app.core.form

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.compose.collections.immutableListOf
import build.wallet.money.currency.EUR
import build.wallet.money.currency.GBP
import build.wallet.money.currency.USD
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.statemachine.money.currency.CurrencyPreferenceFormModel
import build.wallet.statemachine.money.currency.FiatCurrencyListFormModel
import build.wallet.statemachine.settings.full.notifications.NotificationsSettingsFormBodyModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.input.TextFieldModel
import build.wallet.ui.model.list.ListItemPickerMenu
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

@Preview
@Composable
internal fun PreviewMobilePaySheetScreen() {
  FormScreen(
    model = FormBodyModel(
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
internal fun PreviewFeeOptionsFormScreen() {
  FormScreen(
    model =
      FormBodyModel(
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
internal fun SetCustomElectrumFormScreenPreview() {
  FormScreen(
    model =
      FormBodyModel(
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
internal fun NotificationsSettingsPreview() {
  FormScreen(
    model =
      NotificationsSettingsFormBodyModel(
        smsText = "(555) 555-5555",
        emailText = "test@mail.com",
        onBack = {},
        onSmsClick = {},
        onEmailClick = {}
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
fun CurrencyPreferencePreview() {
  FormScreen(
    model =
      CurrencyPreferenceFormModel(
        onBack = null,
        moneyHomeHero = FormMainContentModel.MoneyHomeHero("$0", "0 sats"),
        fiatCurrencyPreferenceString = "USD",
        onFiatCurrencyPreferenceClick = {},
        bitcoinDisplayPreferenceString = "Sats",
        bitcoinDisplayPreferencePickerModel = CurrencyPreferenceListItemPickerMenu,
        onBitcoinDisplayPreferenceClick = {},
        onDone = {}
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
