package build.wallet.statemachine.moneyhome.card.gettingstarted

import build.wallet.home.GettingStartedTask
import build.wallet.home.GettingStartedTask.TaskId.AddBitcoin
import build.wallet.home.GettingStartedTask.TaskId.EnableSpendingLimit
import build.wallet.home.GettingStartedTask.TaskState.Complete
import build.wallet.home.GettingStartedTask.TaskState.Incomplete
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.Icon.*
import build.wallet.statemachine.moneyhome.card.CardModel.GettingStartedTileModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.list.ListItemTreatment

data class GettingStartedTaskRowModel(
  val task: GettingStartedTask,
  val isEnabled: Boolean,
  val onClick: () -> Unit,
) {
  val tileModel: GettingStartedTileModel
    get() {
      val taskPresentation = task.taskPresentation()

      return when (task.state) {
        Complete ->
          GettingStartedTileModel(
            id = taskPresentation.tileId,
            title = taskPresentation.title,
            leadingIcon =
              IconModel(
                icon = SmallIconCheckFilled,
                iconSize = IconSize.Small,
                iconTint = IconTint.On60
              ),
            isEnabled = true,
            isComplete = true
          )

        Incomplete ->
          GettingStartedTileModel(
            id = taskPresentation.tileId,
            title = taskPresentation.title,
            leadingIcon =
              IconModel(
                icon = taskPresentation.tileIcon,
                iconSize = IconSize.Regular,
                iconTint = if (isEnabled) null else IconTint.On30
              ),
            isEnabled = isEnabled,
            isComplete = false,
            onClick = onClick
          )
      }
    }

  val listItemModel: ListItemModel
    get() {
      val taskPresentation = task.taskPresentation()

      return when (task.state) {
        Complete ->
          ListItemModel(
            title = taskPresentation.title,
            leadingAccessory =
              ListItemAccessory.IconAccessory(
                model =
                  IconModel(
                    icon = SmallIconCheckFilled,
                    iconSize = IconSize.Small,
                    iconTint = IconTint.On60
                  )
              ),
            treatment = ListItemTreatment.SECONDARY
          )

        Incomplete ->
          ListItemModel(
            title = taskPresentation.title,
            leadingAccessory =
              ListItemAccessory.IconAccessory(
                model =
                  IconModel(
                    icon = taskPresentation.listIcon,
                    iconSize = IconSize.Small,
                    iconTint = if (isEnabled) null else IconTint.On30
                  )
              ),
            trailingAccessory = ListItemAccessory.drillIcon(tint = IconTint.On30),
            enabled = isEnabled,
            onClick = onClick
          )
      }
    }
}

private fun GettingStartedTask.taskPresentation(): GettingStartedTaskPresentation =
  when (id) {
    AddBitcoin ->
      GettingStartedTaskPresentation(
        title = "Add bitcoin",
        listIcon = SmallIconPlusStroked,
        tileIcon = DotCoins,
        tileId = GettingStartedTileModel.Id.AddBitcoin
      )

    EnableSpendingLimit ->
      GettingStartedTaskPresentation(
        title = "Customize transfer settings",
        listIcon = SmallIconMobileLimit,
        tileIcon = DotPair,
        tileId = GettingStartedTileModel.Id.EnableSpendingLimit
      )
  }

private data class GettingStartedTaskPresentation(
  val title: String,
  val listIcon: Icon,
  val tileIcon: Icon,
  val tileId: GettingStartedTileModel.Id,
)
