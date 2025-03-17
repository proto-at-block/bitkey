package build.wallet.statemachine.dev

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import bitkey.account.AccountConfigService
import build.wallet.compose.collections.immutableListOf
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.F8eEnvironment.*
import build.wallet.f8e.name
import build.wallet.f8e.url
import build.wallet.platform.config.AppVariant
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.platform.device.DevicePlatform.Android
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.switch.SwitchModel
import kotlinx.coroutines.launch

@BitkeyInject(ActivityScope::class)
class F8eEnvironmentPickerUiStateMachineImpl(
  private val appVariant: AppVariant,
  private val accountConfigService: AccountConfigService,
  private val deviceInfoProvider: DeviceInfoProvider,
) : F8eEnvironmentPickerUiStateMachine {
  @Composable
  override fun model(props: F8eEnvironmentPickerUiProps): ListGroupModel? {
    // Only show this option in development and team builds.
    when (appVariant) {
      AppVariant.Development, AppVariant.Alpha, AppVariant.Team -> Unit
      AppVariant.Beta, AppVariant.Customer, AppVariant.Emergency -> return null
    }

    val defaultConfig = remember { accountConfigService.defaultConfig() }
      .collectAsState(initial = null)
      .value ?: return null

    val f8eEnvironment = defaultConfig.f8eEnvironment
    val customUrl = if (f8eEnvironment is Custom) f8eEnvironment.url else ""

    return ListGroupModel(
      header = "F8e Environment",
      style = ListGroupStyle.DIVIDER,
      items = immutableListOf(
        F8eEnvironmentOptionModel(props, f8eEnvironment, Production),
        F8eEnvironmentOptionModel(props, f8eEnvironment, Staging),
        F8eEnvironmentOptionModel(props, f8eEnvironment, Development),
        F8eEnvironmentOptionModel(props, f8eEnvironment, Local),
        F8eEnvironmentOptionModel(props, f8eEnvironment, Custom(customUrl))
      )
    )
  }

  @Composable
  private fun F8eEnvironmentOptionModel(
    props: F8eEnvironmentPickerUiProps,
    currentEnvironment: F8eEnvironment,
    environmentOption: F8eEnvironment,
  ): ListItemModel {
    val scope = rememberStableCoroutineScope()
    val deviceInfo = deviceInfoProvider.getDeviceInfo()
    val isAndroidEmulator = deviceInfo.devicePlatform == Android && deviceInfo.isEmulator
    return ListItemModel(
      title = environmentOption.name,
      secondaryText = environmentOption.url(isAndroidEmulator),
      trailingAccessory = ListItemAccessory.SwitchAccessory(
        model = SwitchModel(
          checked = currentEnvironment == environmentOption,
          onCheckedChange = { isChecked ->
            if (isChecked) {
              scope.launch {
                accountConfigService.setF8eEnvironment(environmentOption)
              }
            }
          }
        )
      ),
      onClick = {
        if (environmentOption is Custom) {
          props.openCustomUrlInput(environmentOption.url)
        }
      }
    )
  }
}
