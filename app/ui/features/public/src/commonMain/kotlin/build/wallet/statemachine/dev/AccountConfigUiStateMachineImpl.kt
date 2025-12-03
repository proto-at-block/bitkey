package build.wallet.statemachine.dev

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import bitkey.account.*
import build.wallet.compose.collections.buildImmutableList
import build.wallet.compose.collections.immutableListOf
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.name
import build.wallet.f8e.url
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.Customer
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.platform.device.DevicePlatform.Android
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.switch.SwitchModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@BitkeyInject(ActivityScope::class)
class AccountConfigUiStateMachineImpl(
  private val appVariant: AppVariant,
  private val accountConfigService: AccountConfigService,
  private val deviceInfoProvider: DeviceInfoProvider,
) : AccountConfigUiStateMachine {
  @Composable
  override fun model(props: AccountConfigProps): ListGroupModel? {
    // Do not show this option in Customer builds
    if (appVariant == Customer) return null
    val accountConfig =
      remember { accountConfigService.activeOrDefaultConfig() }.collectAsState().value

    return when (accountConfig) {
      is FullAccountConfig -> {
        ActiveFullAccountModel(
          accountConfig = accountConfig,
          onBitcoinWalletClick = props.onBitcoinWalletClick
        )
      }
      is LiteAccountConfig -> ActiveLiteAccountModel(accountConfig)
      is SoftwareAccountConfig -> return null // Not yet supported.
      is DefaultAccountConfig -> NoActiveAccountModel(accountConfig)
    }
  }

  private fun ActiveFullAccountModel(
    accountConfig: FullAccountConfig,
    onBitcoinWalletClick: () -> Unit,
  ): ListGroupModel {
    return ListGroupModel(
      header = "Keybox Configuration",
      style = ListGroupStyle.DIVIDER,
      items = buildImmutableList {
        addAll(AccountConfigItems(accountConfig))
        add(
          ListItemModel(
            title = "Bitcoin network",
            sideText = accountConfig.bitcoinNetworkType.name.lowercase()
          )
        )
        add(
          ListItemModel(
            title = "Fake hardware",
            sideText = accountConfig.isHardwareFake.toString()
          )
        )
        add(
          ListItemModel(
            title = "Hardware Type",
            sideText = accountConfig.hardwareType.toString()
          )
        )
        add(
          ListItemModel(
            title = "Bitcoin wallet",
            trailingAccessory = ListItemAccessory.drillIcon(),
            onClick = onBitcoinWalletClick
          )
        )
      }.toImmutableList()
    )
  }

  @Composable
  private fun ActiveLiteAccountModel(accountConfig: AccountConfig): ListGroupModel {
    val defaultConfig by remember { accountConfigService.defaultConfig() }.collectAsState()
    return ListGroupModel(
      header = "Keybox Configuration",
      style = ListGroupStyle.DIVIDER,
      items = buildImmutableList {
        addAll(AccountConfigItems(accountConfig))
        add(MockBitkeyItem(defaultConfig))
        add(HardwareTypeItem(defaultConfig))
      }
    )
  }

  @Composable
  private fun NoActiveAccountModel(defaultConfig: DefaultAccountConfig): ListGroupModel {
    val scope = rememberStableCoroutineScope()
    return ListGroupModel(
      header = "Keybox Configuration",
      style = ListGroupStyle.DIVIDER,
      items = immutableListOf(
        ListItemModel(
          title = "Test Account",
          secondaryText =
            "Create a test account instead of a regular account." +
              "Test accounts can use the code 123456 to verify their accounts. Their recovery " +
              "delay & notify period is also shortened to 20 seconds.",
          trailingAccessory = ListItemAccessory.SwitchAccessory(
            model = SwitchModel(
              checked = defaultConfig.isTestAccount,
              onCheckedChange = { isTestAccount ->
                scope.launch {
                  accountConfigService.setIsTestAccount(isTestAccount)
                }
              }
            )
          )
        ),
        MockBitkeyItem(defaultConfig),
        HardwareTypeItem(defaultConfig),
        ListItemModel(
          title = "Use SocRec Fakes",
          secondaryText = "SocRec interactions will be mocked",
          trailingAccessory = ListItemAccessory.SwitchAccessory(
            model = SwitchModel(
              checked = defaultConfig.isUsingSocRecFakes,
              onCheckedChange = { isUsingSocRecFakes ->
                scope.launch {
                  accountConfigService.setUsingSocRecFakes(isUsingSocRecFakes)
                }
              },
              testTag = "socrec-fakes"
            )
          )
        )
      )
    )
  }

  private fun AccountConfigItems(accountConfig: AccountConfig): List<ListItemModel> {
    val deviceInfo = deviceInfoProvider.getDeviceInfo()
    val isAndroidEmulator = deviceInfo.devicePlatform == Android && deviceInfo.isEmulator
    return listOf(
      ListItemModel(
        title = "F8e Environment",
        sideText = accountConfig.f8eEnvironment.name,
        secondarySideText = accountConfig.f8eEnvironment.url(isAndroidEmulator)
      ),
      ListItemModel(
        title = "Test Account",
        sideText = accountConfig.isTestAccount.toString()
      ),
      ListItemModel(
        title = "SocRec Fakes",
        sideText = accountConfig.isUsingSocRecFakes.toString()
      )
    )
  }

  @Composable
  private fun MockBitkeyItem(defaultConfig: DefaultAccountConfig): ListItemModel {
    val scope = rememberStableCoroutineScope()
    return ListItemModel(
      title = "Mock Bitkey",
      secondaryText = "NFC interactions will be mocked",
      trailingAccessory = ListItemAccessory.SwitchAccessory(
        model = SwitchModel(
          checked = defaultConfig.isHardwareFake,
          onCheckedChange = { fakeHardware ->
            scope.launch {
              accountConfigService.setIsHardwareFake(fakeHardware)
            }
          },
          testTag = "mock-bitkey"
        )
      )
    )
  }

  @Composable
  private fun HardwareTypeItem(defaultConfig: DefaultAccountConfig): ListItemModel {
    val scope = rememberStableCoroutineScope()
    val hardwareTypeText = when (defaultConfig.hardwareType) {
      null -> "Auto-detect"
      HardwareType.W1 -> "W1"
      HardwareType.W3 -> "W3"
    }
    return ListItemModel(
      title = "Hardware Type",
      secondaryText = if (defaultConfig.isHardwareFake) {
        "Choose W1 or W3 for fake hardware"
      } else {
        "Auto-detect or force W1/W3"
      },
      sideText = hardwareTypeText,
      trailingAccessory = ListItemAccessory.ButtonAccessory(
        model = ButtonModel(
          text = "Toggle",
          size = ButtonModel.Size.Compact,
          treatment = ButtonModel.Treatment.Secondary,
          onClick = StandardClick {
            scope.launch {
              val nextType = when (defaultConfig.hardwareType) {
                null -> HardwareType.W1
                HardwareType.W1 -> HardwareType.W3
                HardwareType.W3 -> null
              }
              accountConfigService.setHardwareType(nextType)
            }
          }
        )
      )
    )
  }
}
