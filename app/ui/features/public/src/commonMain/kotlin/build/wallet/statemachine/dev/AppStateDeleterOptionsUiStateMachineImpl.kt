package build.wallet.statemachine.dev

import androidx.compose.runtime.Composable
import build.wallet.compose.collections.buildImmutableList
import build.wallet.debug.cloud.availableCloudBackupStoreTypes
import build.wallet.debug.cloud.name
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
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

@BitkeyInject(ActivityScope::class)
class AppStateDeleterOptionsUiStateMachineImpl(
  private val appVariant: AppVariant,
) : AppStateDeleterOptionsUiStateMachine {
  @Composable
  override fun model(props: AppStateDeleterOptionsUiProps): ListGroupModel {
    // Only show "Delete App Key and Backup" to customers, with a warning text.
    return ListGroupModel(
      header = "Data Management",
      style = ListGroupStyle.DIVIDER,
      items =
        buildImmutableList {
          when (appVariant) {
            Development, Team -> {
              if (props.showDeleteAppKey) {
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
                          onClick = StandardClick(props.onDeleteAppKeyRequest)
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
                          onClick = StandardClick(props.onDeleteAppKeyBackupRequest)
                        )
                    )
                ).run(::add)
              }
            }

            else -> Unit
          }
          ListItemModel(
            title = "Delete All App Key Backups",
            secondaryText = "Only use this if instructed to by a Bitkey team member. You may lose access to your money.",
            trailingAccessory =
              ButtonAccessory(
                model =
                  ButtonModel(
                    text = "Delete",
                    treatment = TertiaryDestructive,
                    size = Compact,
                    onClick = StandardClick(props.onDeleteAllBackupsRequest)
                  )
              )
          ).run(::add)

          availableCloudBackupStoreTypes().forEachIndexed { index, target ->
            ListItemModel(
              title = "Delete App Key Backups (${target.name})",
              secondaryText = "Only use this if instructed to by a Bitkey team member. You may lose access to your money.",
              trailingAccessory =
                ButtonAccessory(
                  model =
                    ButtonModel(
                      text = "Delete",
                      treatment = TertiaryDestructive,
                      size = Compact,
                      onClick = StandardClick { props.onDeleteBackupsInStoreRequest(target) }
                    )
                )
            ).run(::add)
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
                    onClick = StandardClick { props.onDeleteAppKeyAndBackupRequest() }
                  )
              )
          ).run(::add)

          // Only show in Development and Team builds
          when (appVariant) {
            Development, Team -> {
              ListItemModel(
                title = "Delete Onboarding App Key",
                secondaryText = "Delete the persisted app key, so going through onboarding will generate a new one",
                trailingAccessory =
                  ButtonAccessory(
                    model =
                      ButtonModel(
                        text = "Delete",
                        treatment = TertiaryDestructive,
                        size = Compact,
                        onClick = StandardClick { props.onDeleteOnboardingAppKeyRequest() }
                      )
                  )
              ).run(::add)
            }
            else -> Unit
          }
        }
    )
  }
}
