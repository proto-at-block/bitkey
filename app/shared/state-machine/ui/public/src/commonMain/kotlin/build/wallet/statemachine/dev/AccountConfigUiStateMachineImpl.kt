package build.wallet.statemachine.dev

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import build.wallet.bitkey.account.AccountConfig
import build.wallet.bitkey.account.FullAccountConfig
import build.wallet.compose.collections.buildImmutableList
import build.wallet.compose.collections.immutableListOf
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.debug.DebugOptions
import build.wallet.debug.DebugOptionsService
import build.wallet.f8e.name
import build.wallet.f8e.url
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.Customer
import build.wallet.statemachine.data.keybox.AccountData
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.switch.SwitchModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

class AccountConfigUiStateMachineImpl(
  private val appVariant: AppVariant,
  private val debugOptionsService: DebugOptionsService,
) : AccountConfigUiStateMachine {
  @Composable
  override fun model(props: AccountConfigProps): ListGroupModel? {
    // Do not show this option in Customer builds
    if (appVariant == Customer) return null
    val debugOptions = remember { debugOptionsService.options() }
      .collectAsState(null).value ?: return null

    return when (props.accountData) {
      is AccountData.HasActiveFullAccountData -> {
        ActiveFullAccountModel(
          accountConfig = props.accountData.account.config,
          onBitcoinWalletClick = props.onBitcoinWalletClick
        )
      }

      is AccountData.HasActiveLiteAccountData ->
        ActiveLiteAccountModel(
          accountConfig = props.accountData.account.config,
          debugOptions = debugOptions
        )

      else -> NoActiveAccountModel(debugOptions)
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
            title = "Bitcoin wallet",
            trailingAccessory = ListItemAccessory.drillIcon(),
            onClick = onBitcoinWalletClick
          )
        )
      }.toImmutableList()
    )
  }

  @Composable
  private fun ActiveLiteAccountModel(
    accountConfig: AccountConfig,
    debugOptions: DebugOptions,
  ): ListGroupModel {
    return ListGroupModel(
      header = "Keybox Configuration",
      style = ListGroupStyle.DIVIDER,
      items = buildImmutableList {
        addAll(AccountConfigItems(accountConfig))
        add(MockBitkeyItem(debugOptions))
      }
    )
  }

  @Composable
  private fun NoActiveAccountModel(debugOptions: DebugOptions): ListGroupModel {
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
              checked = debugOptions.isTestAccount,
              onCheckedChange = { isTestAccount ->
                scope.launch {
                  debugOptionsService.setIsTestAccount(isTestAccount)
                }
              }
            )
          )
        ),
        MockBitkeyItem(debugOptions),
        ListItemModel(
          title = "Use SocRec Fakes",
          secondaryText = "SocRec interactions will be mocked",
          trailingAccessory = ListItemAccessory.SwitchAccessory(
            model = SwitchModel(
              checked = debugOptions.isUsingSocRecFakes,
              onCheckedChange = { isUsingSocRecFakes ->
                scope.launch {
                  debugOptionsService.setUsingSocRecFakes(isUsingSocRecFakes)
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
    return listOf(
      ListItemModel(
        title = "F8e Environment",
        sideText = accountConfig.f8eEnvironment.name,
        secondarySideText = accountConfig.f8eEnvironment.url
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
  private fun MockBitkeyItem(debugOptions: DebugOptions): ListItemModel {
    val scope = rememberStableCoroutineScope()
    return ListItemModel(
      title = "Mock Bitkey",
      secondaryText = "NFC interactions will be mocked",
      trailingAccessory = ListItemAccessory.SwitchAccessory(
        model = SwitchModel(
          checked = debugOptions.isHardwareFake,
          onCheckedChange = { fakeHardware ->
            scope.launch {
              debugOptionsService.setIsHardwareFake(fakeHardware)
            }
          },
          testTag = "mock-bitkey"
        )
      )
    )
  }
}
