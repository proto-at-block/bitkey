package build.wallet.statemachine.moneyhome.card.gettingstarted

import build.wallet.home.GettingStartedTask
import build.wallet.home.GettingStartedTask.TaskId.*
import build.wallet.home.GettingStartedTask.TaskState.Complete
import build.wallet.home.GettingStartedTask.TaskState.Incomplete
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.Icon.*
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
  val listItemModel: ListItemModel
    get() {
      val (title: String, icon: Icon) =
        when (task.id) {
          AddBitcoin -> Pair("Add bitcoin", SmallIconPlusStroked)
          EnableSpendingLimit -> Pair("Customize transfer settings", SmallIconMobileLimit)
          InviteTrustedContact -> Pair("Invite a Trusted Contact", SmallIconShieldPerson)
          AddAdditionalFingerprint -> Pair("Add additional fingerprint", SmallIconFingerprint)
        }

      return when (task.state) {
        Complete ->
          ListItemModel(
            title = title,
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
            title = title,
            leadingAccessory =
              ListItemAccessory.IconAccessory(
                model =
                  IconModel(
                    icon = icon,
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
