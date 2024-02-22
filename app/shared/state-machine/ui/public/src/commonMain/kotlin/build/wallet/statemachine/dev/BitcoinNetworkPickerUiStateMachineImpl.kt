package build.wallet.statemachine.dev

import androidx.compose.runtime.Composable
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.Customer
import build.wallet.statemachine.data.keybox.config.TemplateKeyboxConfigData
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory.SwitchAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.switch.SwitchModel
import kotlinx.collections.immutable.toImmutableList

class BitcoinNetworkPickerUiStateMachineImpl(
  private val appVariant: AppVariant,
) : BitcoinNetworkPickerUiStateMachine {
  @Composable
  override fun model(props: BitcoinNetworkPickerUiProps): ListGroupModel? {
    // Do not allow changing network type for Customers.
    if (appVariant == Customer) return null

    val templateKeyboxConfigData = props.templateKeyboxConfigData ?: return null

    return ListGroupModel(
      header = "Bitcoin network",
      style = ListGroupStyle.DIVIDER,
      items =
        BitcoinNetworkType.entries.map { networkType ->
          BitcoinNetworkTypeOptionModel(templateKeyboxConfigData, network = networkType)
        }.toImmutableList()
    )
  }

  @Composable
  private fun BitcoinNetworkTypeOptionModel(
    templateKeyboxConfigData: TemplateKeyboxConfigData.LoadedTemplateKeyboxConfigData,
    network: BitcoinNetworkType,
  ) = ListItemModel(
    title = network.name.lowercase(),
    trailingAccessory =
      SwitchAccessory(
        model =
          SwitchModel(
            checked = network == templateKeyboxConfigData.config.networkType,
            onCheckedChange = { isChecked ->
              if (isChecked) {
                templateKeyboxConfigData.updateConfig {
                  it.copy(networkType = network)
                }
              }
            }
          )
      )
  )
}
