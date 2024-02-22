package build.wallet.statemachine.dev

import androidx.compose.runtime.Composable
import build.wallet.compose.collections.immutableListOf
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.F8eEnvironment.Custom
import build.wallet.f8e.F8eEnvironment.Development
import build.wallet.f8e.F8eEnvironment.Local
import build.wallet.f8e.F8eEnvironment.Production
import build.wallet.f8e.F8eEnvironment.Staging
import build.wallet.f8e.name
import build.wallet.f8e.url
import build.wallet.platform.config.AppVariant
import build.wallet.statemachine.data.keybox.AccountData
import build.wallet.statemachine.data.keybox.config.TemplateKeyboxConfigData
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.switch.SwitchModel

class F8eEnvironmentPickerUiStateMachineImpl(
  private val appVariant: AppVariant,
) : F8eEnvironmentPickerUiStateMachine {
  @Composable
  override fun model(props: F8eEnvironmentPickerUiProps): ListGroupModel? {
    // Only show this option in development and team builds.
    when (appVariant) {
      AppVariant.Development, AppVariant.Team -> Unit
      AppVariant.Beta, AppVariant.Customer, AppVariant.Emergency -> return null
    }

    val configData =
      when (val accountData = props.accountData) {
        is AccountData.NoActiveAccountData.GettingStartedData ->
          accountData.templateKeyboxConfigData
        else -> return null
      }
    val f8eEnvironment = configData.config.f8eEnvironment
    val customUrl = if (f8eEnvironment is Custom) f8eEnvironment.url else ""

    return ListGroupModel(
      header = "F8e Environment",
      style = ListGroupStyle.DIVIDER,
      items =
        immutableListOf(
          F8eEnvironmentOptionModel(props, configData, Production),
          F8eEnvironmentOptionModel(props, configData, Staging),
          F8eEnvironmentOptionModel(props, configData, Development),
          F8eEnvironmentOptionModel(props, configData, Local),
          F8eEnvironmentOptionModel(props, configData, Custom(customUrl))
        )
    )
  }

  @Composable
  private fun F8eEnvironmentOptionModel(
    props: F8eEnvironmentPickerUiProps,
    templateKeyboxConfigData: TemplateKeyboxConfigData.LoadedTemplateKeyboxConfigData,
    environment: F8eEnvironment,
  ) = ListItemModel(
    title = environment.name,
    secondaryText = environment.url,
    trailingAccessory =
      ListItemAccessory.SwitchAccessory(
        model =
          SwitchModel(
            checked = templateKeyboxConfigData.config.f8eEnvironment == environment,
            onCheckedChange = { isChecked ->
              if (isChecked) {
                templateKeyboxConfigData.updateConfig {
                  it.copy(f8eEnvironment = environment)
                }
              }
            }
          )
      ),
    onClick = {
      if (environment is Custom) {
        props.openCustomUrlInput(environment.url, templateKeyboxConfigData)
      }
    }
  )
}
