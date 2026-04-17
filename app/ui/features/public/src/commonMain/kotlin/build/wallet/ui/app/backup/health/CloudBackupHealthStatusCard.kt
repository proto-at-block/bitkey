package build.wallet.ui.app.backup.health

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.statemachine.cloud.health.CloudBackupHealthStatusCardModel
import build.wallet.statemachine.cloud.health.CloudBackupHealthStatusCardType
import build.wallet.statemachine.cloud.health.CloudBackupHealthStatusTone
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.Icon.SmallIconCheckFilled
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.card.Card
import build.wallet.ui.components.header.Header
import build.wallet.ui.components.icon.IconButton
import build.wallet.ui.components.icon.IconImage
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment.Secondary
import build.wallet.ui.components.label.buildAnnotatedString
import build.wallet.ui.components.layout.Divider
import build.wallet.ui.components.list.ListItem
import build.wallet.ui.components.sheet.LocalSheetCloser
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.compose.resId
import build.wallet.ui.compose.scalingClickable
import build.wallet.ui.model.SheetClosingClick
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Compact
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.icon.*
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.theme.LocalDesignSystemUpdatesEnabled
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tokens.market.MarketIcons
import kotlinx.coroutines.launch
import build.wallet.ui.model.button.ButtonModel.Treatment.Primary as ButtonPrimary

@Composable
fun CloudBackupHealthStatusCard(model: CloudBackupHealthStatusCardModel) {
  if (LocalDesignSystemUpdatesEnabled.current) {
    CloudBackupHealthStatusCardDesignSystemV2(model)
  } else {
    CloudBackupHealthStatusCardLegacy(model)
  }
}

@Composable
private fun CloudBackupHealthStatusCardLegacy(model: CloudBackupHealthStatusCardModel) {
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

@Composable
private fun CloudBackupHealthStatusCardDesignSystemV2(model: CloudBackupHealthStatusCardModel) {
  val actionAccessory = model.toolbarModel?.trailingAccessory as? ToolbarAccessoryModel.IconAccessory

  Column(
    modifier = Modifier.fillMaxWidth()
  ) {
    IconImage(
      model = IconModel(
        icon = model.designSystemV2Icon(),
        iconSize = IconSize.Regular
      )
    )

    if (model.headerModel.headline != null || model.headerModel.sublineModel != null) {
      Spacer(modifier = Modifier.height(12.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
      ) {
        Column(
          modifier = Modifier.weight(1f)
        ) {
          model.headerModel.headline?.let { headline ->
            Label(
              text = headline,
              type = LabelType.Body2MonoCaps
            )
          }

          model.headerModel.sublineModel?.let { subline ->
            if (model.headerModel.headline != null) {
              Spacer(modifier = Modifier.height(8.dp))
            }
            Label(
              text = subline.buildAnnotatedString(),
              type = LabelType.Body3Regular,
              treatment = Secondary
            )
          }
        }

        Box(
          modifier = Modifier
            .width(CloudBackupHeaderAccessoryReservedWidth)
            .height(CloudBackupHeaderAccessoryHitTargetHeight),
          contentAlignment = Alignment.TopEnd
        ) {
          actionAccessory?.let {
            CloudBackupHealthHeaderActionButton(model = it.model)
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(12.dp))
    Divider()
    CloudBackupHealthStatusRow(model)
    Divider()
  }
}

@Composable
private fun CloudBackupHealthStatusRow(model: CloudBackupHealthStatusCardModel) {
  val statusText = model.designSystemV2StatusText ?: model.backupStatus.secondaryText
  val statusIndicatorColor =
    model.designSystemV2StatusTone.colorOrNull() ?: model.backupStatus.statusIndicatorColor()

  Row(
    modifier = Modifier
      .fillMaxWidth()
      .padding(vertical = 20.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Row(
      modifier = Modifier.weight(1f),
      verticalAlignment = Alignment.CenterVertically
    ) {
      statusText?.let {
        statusIndicatorColor?.let { color ->
          CloudBackupHealthStatusIndicator(color)
          Spacer(modifier = Modifier.width(8.dp))
        }
        Label(
          text = it,
          type = LabelType.Body3Regular,
          treatment = Secondary
        )
      }
    }

    model.backupStatusActionButton?.let {
      Spacer(modifier = Modifier.width(12.dp))
      Button(model = it.asCompactButton())
    }
  }
}

@Composable
private fun CloudBackupHealthStatusIndicator(color: Color) {
  Box(
    modifier = Modifier
      .size(10.dp)
      .background(
        color = color,
        shape = CircleShape
      )
  )
}

private fun CloudBackupHealthStatusCardModel.designSystemV2Icon(): Icon =
  when (type) {
    CloudBackupHealthStatusCardType.APP_KEY_BACKUP -> Icon.DotCloudBackup
    CloudBackupHealthStatusCardType.EEK_BACKUP -> Icon.DotEmergency
  }

private fun ButtonModel.asCompactButton(): ButtonModel =
  copy(
    text = "Back up",
    size = Compact,
    treatment = ButtonPrimary
  )

@Composable
private fun CloudBackupHealthHeaderActionButton(model: IconButtonModel) {
  val actionButtonModel = model.designSystemV2IconButtonModel()
  if ((actionButtonModel.iconModel.iconImage as? IconImage.MarketIconImage)?.icon != MarketIcons.FileUpload) {
    IconButton(
      model = actionButtonModel,
      modifier = Modifier
        .resId(actionButtonModel.testTag)
        .size(CloudBackupHeaderAccessoryHitTargetHeight)
    )
    return
  }

  val clickHandler: () -> Unit =
    when (actionButtonModel.onClick) {
      is StandardClick -> {
        { actionButtonModel.onClick() }
      }

      is SheetClosingClick -> {
        val scope = rememberStableCoroutineScope()
        val sheetCloser = LocalSheetCloser.current

        {
          scope.launch {
            sheetCloser()
          }.invokeOnCompletion { actionButtonModel.onClick() }
        }
      }
    }

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(CloudBackupHeaderAccessoryHitTargetHeight)
      .resId(actionButtonModel.testTag)
      .alpha(if (actionButtonModel.enabled) 1f else 0.5f)
      .scalingClickable(
        enabled = actionButtonModel.enabled,
        onClick = clickHandler
      ),
    contentAlignment = Alignment.TopEnd
  ) {
    IconImage(model = actionButtonModel.iconModel)
  }
}

private fun IconButtonModel.designSystemV2IconButtonModel(): IconButtonModel =
  if ((iconModel.iconImage as? IconImage.LocalImage)?.icon == Icon.SmallIconShare) {
    copy(
      iconModel = iconModel.copy(
        iconImage = IconImage.MarketIconImage(MarketIcons.FileUpload)
      )
    )
  } else {
    this
  }

private fun ListItemModel.statusIndicatorColor(): Color? =
  ((trailingAccessory as? ListItemAccessory.IconAccessory)?.model?.iconImage as? IconImage.LocalImage)
    ?.icon
    ?.let { icon ->
      when (icon) {
        Icon.SmallIconCheckFilled -> CloudBackupHealthStatusSuccessGreen
        Icon.SmallIconWarning,
        Icon.SmallIconWarningFilled,
        -> CloudBackupHealthStatusWarningOrange
        else -> null
      }
    }

private fun CloudBackupHealthStatusTone?.colorOrNull(): Color? =
  when (this) {
    CloudBackupHealthStatusTone.SUCCESS -> CloudBackupHealthStatusSuccessGreen
    CloudBackupHealthStatusTone.WARNING -> CloudBackupHealthStatusWarningOrange
    CloudBackupHealthStatusTone.DANGER -> CloudBackupHealthStatusDangerRed
    null -> null
  }

private val CloudBackupHealthStatusDangerRed = Color(0xffca0000)
private val CloudBackupHealthStatusWarningOrange = Color(0xffbf46e38)
private val CloudBackupHealthStatusSuccessGreen = Color(0xff3aba5a)
private val CloudBackupHeaderAccessoryReservedWidth = 68.dp
private val CloudBackupHeaderAccessoryHitTargetHeight = 48.dp

val CloudBackupHealthStatusCardModelForPreview =
  CloudBackupHealthStatusCardModel(
    toolbarModel = null,
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
        headline = "App Key Backup",
        subline = "Encrypted backup of your App Key for easy access when you get a new phone.",
        alignment = FormHeaderModel.Alignment.CENTER,
        sublineTreatment = FormHeaderModel.SublineTreatment.SMALL
      ),
    backupStatus = ListItemModel(
      title = "Google Drive backup",
      secondaryText = "Successfully backed up",
      trailingAccessory = ListItemAccessory.IconAccessory(SmallIconCheckFilled)
    ),
    designSystemV2StatusText = "Successfully backed up",
    designSystemV2StatusTone = CloudBackupHealthStatusTone.SUCCESS,
    backupStatusActionButton = null,
    type = CloudBackupHealthStatusCardType.APP_KEY_BACKUP
  )

val CloudBackupHealthStatusCardEekModelForPreview =
  CloudBackupHealthStatusCardModel(
    toolbarModel = ToolbarModel(
      trailingAccessory = IconAccessory(
        IconButtonModel(
          iconModel = IconModel(
            icon = Icon.SmallIconShare,
            iconSize = IconSize.Small
          ),
          onClick = StandardClick {},
          testTag = "cloud-backup-health-share-button"
        )
      )
    ),
    headerModel =
      FormHeaderModel(
        iconModel = IconModel(
          icon = Icon.CloudBackupEmergencyExitKit,
          iconSize = IconSize.Large,
          iconTint = IconTint.Primary,
          iconBackgroundType = IconBackgroundType.Circle(
            circleSize = IconSize.Avatar,
            color = IconBackgroundType.Circle.CircleColor.Primary
          )
        ),
        headline = "Emergency Exit Kit",
        subline = "Ensures you still have access to your wallet if you can’t access the Bitkey App.",
        alignment = FormHeaderModel.Alignment.CENTER,
        sublineTreatment = FormHeaderModel.SublineTreatment.SMALL
      ),
    backupStatus = ListItemModel(
      title = "Google Drive backup",
      secondaryText = "Successfully backed up",
      trailingAccessory = ListItemAccessory.IconAccessory(SmallIconCheckFilled)
    ),
    designSystemV2StatusText = "Successfully backed up",
    designSystemV2StatusTone = CloudBackupHealthStatusTone.SUCCESS,
    backupStatusActionButton = null,
    type = CloudBackupHealthStatusCardType.EEK_BACKUP
  )

val CloudBackupHealthStatusActionButtonForPreview =
  ButtonModel(
    text = "Back up now",
    size = Footer,
    treatment = ButtonModel.Treatment.Primary,
    onClick = StandardClick {}
  )

val CloudBackupHealthStatusProblemListItemForPreview =
  ListItemModel(
    title = "Problem with App Key Backup",
    secondaryText = "No backup found",
    trailingAccessory = ListItemAccessory.IconAccessory(Icon.SmallIconWarning)
  )

val CloudBackupHealthStatusEekProblemListItemForPreview =
  ListItemModel(
    title = "Google Drive backup",
    secondaryText = "No backup found",
    trailingAccessory = ListItemAccessory.IconAccessory(Icon.SmallIconWarning)
  )
