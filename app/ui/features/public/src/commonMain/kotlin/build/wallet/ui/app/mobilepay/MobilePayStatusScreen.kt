package build.wallet.ui.app.mobilepay

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.settings.full.mobilepay.MobilePayStatusModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.app.core.form.FormScreenContentVerticalAlignment
import build.wallet.ui.components.alertdialog.AlertDialog
import build.wallet.ui.components.limit.SpendingLimitCard
import build.wallet.ui.components.switch.SwitchCard
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

@Composable
fun MobilePayStatusScreen(
  modifier: Modifier = Modifier,
  model: MobilePayStatusModel,
) {
  val onBack =
    when (val disableAlertModel = model.disableAlertModel) {
      null -> model.onBack
      else -> disableAlertModel.onDismiss
    }

  FormScreen(
    modifier = modifier,
    onBack = onBack,
    toolbarModel = ToolbarModel(
      leadingAccessory = BackAccessory(onClick = onBack)
    ),
    designSystemV2Title = "Transfer Settings",
    designSystemV2ContentSpacing = 40,
    designSystemV2Scrollable = false,
    designSystemV2MainContentAlignment = FormScreenContentVerticalAlignment.Top,
    mainContent = {
      SwitchCard(model = model.switchCardModel)
      model.spendingLimitCardModel?.let { cardModel ->
        SpendingLimitCard(
          modifier = Modifier.fillMaxWidth(),
          model = cardModel,
          icon = Icon.DotVerification
        )
      }

      model.disableAlertModel?.let { alertModel ->
        AlertDialog(alertModel)
      }
    }
  )
}
