package build.wallet.statemachine.dev

import androidx.compose.runtime.Composable
import build.wallet.compose.collections.immutableListOf
import build.wallet.onboarding.OnboardingKeyboxStep.CloudBackup
import build.wallet.onboarding.OnboardingKeyboxStep.NotificationPreferences
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.Customer
import build.wallet.statemachine.data.account.OnboardConfigData
import build.wallet.statemachine.data.keybox.AccountData.HasActiveLiteAccountData
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData.GettingStartedData
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory.SwitchAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.switch.SwitchModel

class OnboardingConfigStateMachineImpl(
  private val appVariant: AppVariant,
) : OnboardingConfigStateMachine {
  @Composable
  override fun model(props: OnboardingConfigProps): ListGroupModel? {
    // Do not show this option in Customer builds
    if (appVariant == Customer) return null

    val onboardConfigData =
      when (val accountData = props.accountData) {
        is GettingStartedData -> accountData.newAccountOnboardConfigData
        is HasActiveLiteAccountData -> accountData.accountUpgradeOnboardConfigData
        else -> null
      }

    return when (onboardConfigData) {
      is OnboardConfigData.LoadingOnboardConfigData, null -> null
      is OnboardConfigData.LoadedOnboardConfigData ->
        ListGroupModel(
          header = "Onboarding",
          style = ListGroupStyle.DIVIDER,
          items =
            immutableListOf(
              ListItemModel(
                title = "Skip Cloud Backup",
                secondaryText = "New wallet won’t be backed up to cloud",
                trailingAccessory =
                  SwitchAccessory(
                    model =
                      SwitchModel(
                        checked = onboardConfigData.config.stepsToSkip.contains(CloudBackup),
                        onCheckedChange = { shouldSkip ->
                          onboardConfigData.setShouldSkipStep(CloudBackup, shouldSkip)
                        },
                        testTag = "skip-cloud-backup"
                      )
                  )
              ),
              ListItemModel(
                title = "Skip Notifications",
                secondaryText = "New wallet won’t have notifications set up",
                trailingAccessory =
                  SwitchAccessory(
                    model =
                      SwitchModel(
                        checked =
                          onboardConfigData.config.stepsToSkip.contains(
                            NotificationPreferences
                          ),
                        onCheckedChange = { shouldSkip ->
                          onboardConfigData.setShouldSkipStep(NotificationPreferences, shouldSkip)
                        },
                        testTag = "skip-notifications"
                      )
                  )
              )
            )
        )
    }
  }
}
