package build.wallet.ui.app.mobilepay

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.statemachine.limit.SpendingLimitsCopy
import build.wallet.statemachine.settings.full.mobilepay.MobilePayStatusModel
import build.wallet.statemachine.settings.full.mobilepay.SpendingLimitCardModel
import build.wallet.statemachine.settings.full.mobilepay.disableMobilePayAlertModel
import build.wallet.ui.model.switch.SwitchCardModel
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun MobilePayStatusScreenEnabledPreview() {
  PreviewWalletTheme {
    MobilePayStatusScreen(
      model = MobilePayStatusModel(
        onBack = {},
        switchIsChecked = true,
        onSwitchCheckedChange = {},
        dailyLimitRow =
          SwitchCardModel.ActionRow(
            title = "Daily limit",
            sideText = "$100.00",
            onClick = {}
          ),
        disableAlertModel = null,
        spendingLimitCopy = SpendingLimitsCopy.get(isRevampOn = false),
        spendingLimitCardModel =
          SpendingLimitCardModel(
            titleText = "Daily limit",
            dailyResetTimezoneText = "Resets at 3:00am PDT",
            spentAmountText = "$50.00 spent",
            remainingAmountText = "$50.00 remaining",
            progressPercentage = .5f
          )
      )
    )
  }
}

@Preview
@Composable
fun MobilePayStatusScreenEnabledWithDialogPreview() {
  PreviewWalletTheme {
    MobilePayStatusScreen(
      model = MobilePayStatusModel(
        onBack = {},
        switchIsChecked = true,
        onSwitchCheckedChange = {},
        dailyLimitRow =
          SwitchCardModel.ActionRow(
            title = "Daily limit",
            sideText = "$100.00",
            onClick = {}
          ),
        disableAlertModel =
          disableMobilePayAlertModel(
            title = SpendingLimitsCopy.get(false).disableAlert.title,
            subline = SpendingLimitsCopy.get(false).disableAlert.subline,
            primaryButtonText = SpendingLimitsCopy.get(false).disableAlert.primaryButtonText,
            cancelText = SpendingLimitsCopy.get(false).disableAlert.cancelText,
            onConfirm = {},
            onCancel = {}
          ),
        spendingLimitCopy = SpendingLimitsCopy.get(isRevampOn = false),
        spendingLimitCardModel =
          SpendingLimitCardModel(
            titleText = "Daily limit",
            dailyResetTimezoneText = "Resets at 3:00am PDT",
            spentAmountText = "$50.00 spent",
            remainingAmountText = "$50.00 remaining",
            progressPercentage = .5f
          )
      )
    )
  }
}

@Preview
@Composable
fun MobilePayStatusScreenEnabledWithDialogPreviewAndRevamp() {
  PreviewWalletTheme {
    MobilePayStatusScreen(
      model = MobilePayStatusModel(
        onBack = {},
        switchIsChecked = true,
        onSwitchCheckedChange = {},
        dailyLimitRow =
          SwitchCardModel.ActionRow(
            title = "Daily limit",
            sideText = "$100.00",
            onClick = {}
          ),
        disableAlertModel =
          disableMobilePayAlertModel(
            title = SpendingLimitsCopy.get(true).disableAlert.title,
            subline = SpendingLimitsCopy.get(true).disableAlert.subline,
            primaryButtonText = SpendingLimitsCopy.get(true).disableAlert.primaryButtonText,
            cancelText = SpendingLimitsCopy.get(true).disableAlert.cancelText,
            onConfirm = {},
            onCancel = {}
          ),
        spendingLimitCopy = SpendingLimitsCopy.get(isRevampOn = false),
        spendingLimitCardModel =
          SpendingLimitCardModel(
            titleText = "Daily limit",
            dailyResetTimezoneText = "Resets at 3:00am PDT",
            spentAmountText = "$50.00 spent",
            remainingAmountText = "$50.00 remaining",
            progressPercentage = .5f
          )
      )
    )
  }
}

@Preview
@Composable
fun MobilePayStatusScreenDisabledPreview() {
  PreviewWalletTheme {
    MobilePayStatusScreen(
      model = MobilePayStatusModel(
        onBack = {},
        switchIsChecked = false,
        onSwitchCheckedChange = {},
        dailyLimitRow = null,
        disableAlertModel = null,
        spendingLimitCardModel = null,
        spendingLimitCopy = SpendingLimitsCopy.get(isRevampOn = false)
      )
    )
  }
}
