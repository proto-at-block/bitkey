package build.wallet.ui.app.mobilepay

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.settings.full.mobilepay.MobilePayStatusModel
import build.wallet.statemachine.settings.full.mobilepay.SpendingLimitCardModel
import build.wallet.statemachine.settings.full.mobilepay.disableMobilePayAlertModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.components.alertdialog.AlertDialog
import build.wallet.ui.components.limit.SpendingLimitCard
import build.wallet.ui.components.switch.SwitchCard
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.components.toolbar.ToolbarAccessory
import build.wallet.ui.model.switch.SwitchCardModel.ActionRow
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.tooling.PreviewWalletTheme

@Composable
fun MobilePayStatusScreen(model: MobilePayStatusModel) {
  val onBack =
    when (val disableAlertModel = model.disableAlertModel) {
      null -> model.onBack
      else -> disableAlertModel.onDismiss
    }

  FormScreen(
    onBack = onBack,
    toolbarContent = {
      Toolbar(
        leadingContent = {
          ToolbarAccessory(
            model = BackAccessory(onClick = model.onBack)
          )
        }
      )
    },
    mainContent = {
      SwitchCard(model = model.switchCardModel)
      Spacer(modifier = Modifier.height(24.dp))
      model.spendingLimitCardModel?.let { cardModel ->
        SpendingLimitCard(
          modifier = Modifier.fillMaxWidth(),
          model = cardModel
        )
      }

      model.disableAlertModel?.let { alertModel ->
        AlertDialog(alertModel)
      }
    }
  )
}

@Preview
@Composable
fun MobilePayStatusScreenEnabledPreview() {
  PreviewWalletTheme {
    MobilePayStatusScreen(
      MobilePayStatusModel(
        onBack = {},
        switchIsChecked = true,
        onSwitchCheckedChange = {},
        dailyLimitRow =
          ActionRow(
            title = "Daily limit",
            sideText = "$100.00",
            onClick = {}
          ),
        disableAlertModel = null,
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
      MobilePayStatusModel(
        onBack = {},
        switchIsChecked = true,
        onSwitchCheckedChange = {},
        dailyLimitRow =
          ActionRow(
            title = "Daily limit",
            sideText = "$100.00",
            onClick = {}
          ),
        disableAlertModel =
          disableMobilePayAlertModel(
            onConfirm = {},
            onCancel = {}
          ),
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
      MobilePayStatusModel(
        onBack = {},
        switchIsChecked = false,
        onSwitchCheckedChange = {},
        dailyLimitRow = null,
        disableAlertModel = null,
        spendingLimitCardModel = null
      )
    )
  }
}
