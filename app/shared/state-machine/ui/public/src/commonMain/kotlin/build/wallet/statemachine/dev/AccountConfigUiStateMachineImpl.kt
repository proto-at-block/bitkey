package build.wallet.statemachine.dev

import androidx.compose.runtime.Composable
import build.wallet.bitkey.account.AccountConfig
import build.wallet.bitkey.account.FullAccountConfig
import build.wallet.compose.collections.immutableListOf
import build.wallet.f8e.name
import build.wallet.f8e.url
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.Customer
import build.wallet.statemachine.data.keybox.AccountData
import build.wallet.statemachine.data.keybox.config.TemplateFullAccountConfigData
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.switch.SwitchModel
import kotlinx.collections.immutable.toImmutableList

class AccountConfigUiStateMachineImpl(
  private val appVariant: AppVariant,
) : AccountConfigUiStateMachine {
  @Composable
  override fun model(props: AccountConfigProps): ListGroupModel? {
    // Do not show this option in Customer builds
    if (appVariant == Customer) return null

    return when (props.accountData) {
      is AccountData.HasActiveFullAccountData ->
        ActiveFullAccountModel(props.accountData.account.config)

      is AccountData.HasActiveLiteAccountData ->
        ActiveLiteAccountModel(
          accountConfig = props.accountData.account.config,
          templateFullAccountConfigData = props.accountData.accountUpgradeTemplateFullAccountConfigData
        )

      else -> {
        props.templateFullAccountConfigData?.let {
          NoActiveAccountModel(it)
        }
      }
    }
  }

  private fun ActiveFullAccountModel(accountConfig: FullAccountConfig): ListGroupModel {
    return ListGroupModel(
      header = "Keybox Configuration",
      style = ListGroupStyle.DIVIDER,
      items =
        buildList {
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
        }.toImmutableList()
    )
  }

  private fun ActiveLiteAccountModel(
    accountConfig: AccountConfig,
    templateFullAccountConfigData:
      TemplateFullAccountConfigData.LoadedTemplateFullAccountConfigData,
  ): ListGroupModel {
    return ListGroupModel(
      header = "Keybox Configuration",
      style = ListGroupStyle.DIVIDER,
      items =
        buildList {
          addAll(AccountConfigItems(accountConfig))
          add(MockBitkeyItem(templateFullAccountConfigData))
        }.toImmutableList()
    )
  }

  private fun NoActiveAccountModel(
    templateFullAccountConfigData:
      TemplateFullAccountConfigData.LoadedTemplateFullAccountConfigData,
  ): ListGroupModel {
    return ListGroupModel(
      header = "Keybox Configuration",
      style = ListGroupStyle.DIVIDER,
      items =
        immutableListOf(
          ListItemModel(
            title = "Test Account",
            secondaryText =
              "Create a test account instead of a regular account." +
                "Test accounts can use the code 123456 to verify their accounts. Their recovery " +
                "delay & notify period is also shortened to 20 seconds.",
            trailingAccessory =
              ListItemAccessory.SwitchAccessory(
                model =
                  SwitchModel(
                    checked = templateFullAccountConfigData.config.isTestAccount,
                    onCheckedChange = { isTestAccount ->
                      templateFullAccountConfigData.updateConfig {
                        it.copy(isTestAccount = isTestAccount)
                      }
                    }
                  )
              )
          ),
          MockBitkeyItem(templateFullAccountConfigData),
          ListItemModel(
            title = "Use SocRec Fakes",
            secondaryText = "SocRec interactions will be mocked",
            trailingAccessory =
              ListItemAccessory.SwitchAccessory(
                model =
                  SwitchModel(
                    checked = templateFullAccountConfigData.config.isUsingSocRecFakes,
                    onCheckedChange = { isUsingSocRecFakes ->
                      templateFullAccountConfigData.updateConfig {
                        it.copy(isUsingSocRecFakes = isUsingSocRecFakes)
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

  private fun MockBitkeyItem(
    templateFullAccountConfigData:
      TemplateFullAccountConfigData.LoadedTemplateFullAccountConfigData,
  ) = ListItemModel(
    title = "Mock Bitkey",
    secondaryText = "NFC interactions will be mocked",
    trailingAccessory =
      ListItemAccessory.SwitchAccessory(
        model =
          SwitchModel(
            checked = templateFullAccountConfigData.config.isHardwareFake,
            onCheckedChange = { fakeHardware ->
              templateFullAccountConfigData.updateConfig {
                it.copy(isHardwareFake = fakeHardware)
              }
            },
            testTag = "mock-bitkey"
          )
      )
  )
}
