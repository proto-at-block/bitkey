package build.wallet.statemachine.dev

import androidx.compose.runtime.Composable
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.Development
import build.wallet.platform.config.AppVariant.Team
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Compact
import build.wallet.ui.model.button.ButtonModel.Treatment.TertiaryDestructive
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory.ButtonAccessory
import build.wallet.ui.model.list.ListItemModel
import kotlinx.collections.immutable.toImmutableList

class AppStateDeleterOptionsUiStateMachineImpl(
  private val appVariant: AppVariant,
) : AppStateDeleterOptionsUiStateMachine {
  @Composable
  override fun model(props: AppStateDeleterOptionsUiProps): ListGroupModel {
    // Only show "Delete App Key and Backup" to customers, with a warning text.
    return ListGroupModel(
      style = ListGroupStyle.DIVIDER,
      items =
        buildList {
          when (appVariant) {
            Development, Team -> {
              ListItemModel(
                title = "Delete App Key",
                secondaryText = "Only use this if instructed to by a Bitkey team member. You may lose access to your money.",
                trailingAccessory =
                  ButtonAccessory(
                    model =
                      ButtonModel(
                        text = "Delete",
                        treatment = TertiaryDestructive,
                        size = Compact,
                        onClick = StandardClick(props.onDeleteAppKeyRequest),
                        testTag = "delete-app-key"
                      )
                  )
              ).run(::add)

              ListItemModel(
                title = "Delete App Key Backup",
                secondaryText = "Only use this if instructed to by a Bitkey team member. You may lose access to your money.",
                trailingAccessory =
                  ButtonAccessory(
                    model =
                      ButtonModel(
                        text = "Delete",
                        treatment = TertiaryDestructive,
                        size = Compact,
                        onClick = StandardClick(props.onDeleteAppKeyBackupRequest),
                        testTag = "delete-mobile-key-backup"
                      )
                  )
              ).run(::add)
            }

            else -> Unit
          }
          ListItemModel(
            title = "Delete App Key and Backup",
            secondaryText = "Only use this if instructed to by a Bitkey team member. You may lose access to your money.",
            trailingAccessory =
              ButtonAccessory(
                model =
                  ButtonModel(
                    text = "Delete",
                    treatment = TertiaryDestructive,
                    size = Compact,
                    onClick = StandardClick { props.onDeleteAppKeyAndBackupRequest() },
                    testTag = "delete-app-key-and-backup"
                  )
              )
          ).run(::add)
        }.toImmutableList()
    )
  }
}
