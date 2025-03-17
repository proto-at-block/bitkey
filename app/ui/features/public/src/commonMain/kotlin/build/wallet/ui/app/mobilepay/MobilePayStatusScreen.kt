package build.wallet.ui.app.mobilepay

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.settings.full.mobilepay.MobilePayStatusModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.components.alertdialog.AlertDialog
import build.wallet.ui.components.limit.SpendingLimitCard
import build.wallet.ui.components.switch.SwitchCard
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.components.toolbar.ToolbarAccessory
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory

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
