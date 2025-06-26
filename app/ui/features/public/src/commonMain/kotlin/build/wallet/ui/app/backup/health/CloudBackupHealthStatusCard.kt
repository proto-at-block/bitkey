package build.wallet.ui.app.backup.health

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.cloud.health.CloudBackupHealthStatusCardModel
import build.wallet.statemachine.cloud.health.CloudBackupHealthStatusCardType
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.Icon.SmallIconCheckFilled
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.card.Card
import build.wallet.ui.components.header.Header
import build.wallet.ui.components.layout.Divider
import build.wallet.ui.components.list.ListItem
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.icon.*
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.theme.WalletTheme

@Composable
fun CloudBackupHealthStatusCard(model: CloudBackupHealthStatusCardModel) {
  val isProblemWithBackup = model.backupStatusActionButton != null
  Card(
    horizontalAlignment = Alignment.CenterHorizontally,
    paddingValues = PaddingValues(0.dp)
  ) {
    model.toolbarModel?.let {
      Toolbar(
        it,
        modifier =
          Modifier.padding(PaddingValues(0.dp, 20.dp, 20.dp, 0.dp))
      )
    }
    Header(
      model = model.headerModel,
      modifier = Modifier.padding(PaddingValues(20.dp, 0.dp, 20.dp, 0.dp))
    )

    Spacer(Modifier.height(20.dp))
    Column(
      modifier = if (isProblemWithBackup) {
        Modifier
          .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
          .background(WalletTheme.colors.secondary)
      } else {
        Modifier
      }.padding(horizontal = 20.dp)
    ) {
      if (!isProblemWithBackup) {
        Divider()
      }
      ListItem(model = model.backupStatus)
      model.backupStatusActionButton?.let {
        Button(it)
        Spacer(Modifier.height(20.dp))
      }
    }
  }
}

val CloudBackupHealthStatusCardModelForPreview =
  CloudBackupHealthStatusCardModel(
    toolbarModel = ToolbarModel(
      trailingAccessory = IconAccessory(
        IconButtonModel(
          iconModel = IconModel(
            icon = Icon.SmallIconShare,
            iconSize = IconSize.Small
          ),
          onClick = StandardClick {}
        )
      )
    ),
    headerModel =
      FormHeaderModel(
        iconModel = IconModel(
          icon = Icon.CloudBackupMobileKey,
          iconSize = IconSize.Large,
          iconTint = IconTint.Primary,
          iconBackgroundType = IconBackgroundType.Circle(
            circleSize = IconSize.Avatar,
            color = IconBackgroundType.Circle.CircleColor.Primary
          )
        ),
        headline = "Title",
        subline = "Subline",
        alignment = FormHeaderModel.Alignment.CENTER,
        sublineTreatment = FormHeaderModel.SublineTreatment.SMALL
      ),
    backupStatus = ListItemModel(
      title = "Backup status title",
      secondaryText = "Backup status description",
      trailingAccessory = ListItemAccessory.IconAccessory(SmallIconCheckFilled)
    ),
    backupStatusActionButton =
      ButtonModel(
        text = "Back up now",
        size = Footer,
        treatment = ButtonModel.Treatment.Primary,
        onClick = StandardClick {}
      ),
    type = CloudBackupHealthStatusCardType.APP_KEY_BACKUP
  )
