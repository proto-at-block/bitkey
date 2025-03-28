package build.wallet.statemachine.pricechart

import build.wallet.analytics.events.screen.id.AppearanceEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.pricechart.ChartRange
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.CloseAccessory
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import kotlinx.collections.immutable.toImmutableList

data class TimeScaleListFormModel(
  val onClose: () -> Unit,
  val selectedTimeScale: ChartRange,
  val labels: List<String>,
  val timeScales: List<ChartRange>,
  val onTimeScaleSelection: (ChartRange) -> Unit,
) : FormBodyModel(
    id = AppearanceEventTrackerScreenId.APPEARANCE_TIME_SCALE_LIST_SELECTION,
    onBack = onClose,
    toolbar = ToolbarModel(
      leadingAccessory = CloseAccessory(onClick = onClose),
      middleAccessory = ToolbarMiddleAccessoryModel(title = "Default time scale")
    ),
    header = null,
    mainContentList = immutableListOf(
      FormMainContentModel.ListGroup(
        listGroupModel = ListGroupModel(
          items = timeScales.mapIndexed { index, scale ->
            ListItemModel(
              title = labels[index],
              trailingAccessory = if (selectedTimeScale == scale) {
                ListItemAccessory.IconAccessory(
                  model = IconModel(
                    icon = Icon.SmallIconCheckFilled,
                    iconSize = IconSize.Small,
                    iconTint = IconTint.Primary
                  )
                )
              } else {
                null
              },
              onClick = { onTimeScaleSelection(scale) }
            )
          }.toImmutableList(),
          style = ListGroupStyle.NONE
        )
      )
    ),
    primaryButton = null
  )
