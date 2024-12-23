package build.wallet.statemachine.dev

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.debug.DebugOptions
import build.wallet.debug.DebugOptionsService
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.config.AppVariant
import build.wallet.platform.config.AppVariant.Customer
import build.wallet.ui.model.list.ListGroupModel
import build.wallet.ui.model.list.ListGroupStyle
import build.wallet.ui.model.list.ListItemAccessory.SwitchAccessory
import build.wallet.ui.model.list.ListItemModel
import build.wallet.ui.model.switch.SwitchModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@BitkeyInject(ActivityScope::class)
class BitcoinNetworkPickerUiStateMachineImpl(
  private val appVariant: AppVariant,
  private val debugOptionsService: DebugOptionsService,
) : BitcoinNetworkPickerUiStateMachine {
  @Composable
  override fun model(props: Unit): ListGroupModel? {
    // Do not allow changing network type for Customers.
    if (appVariant == Customer) return null
    val debugOptions = remember { debugOptionsService.options() }
      .collectAsState(null).value ?: return null

    return ListGroupModel(
      header = "Bitcoin network",
      style = ListGroupStyle.DIVIDER,
      items = BitcoinNetworkType.entries.map { networkType ->
        BitcoinNetworkTypeOptionModel(debugOptions, network = networkType)
      }.toImmutableList()
    )
  }

  @Composable
  private fun BitcoinNetworkTypeOptionModel(
    debugOptions: DebugOptions,
    network: BitcoinNetworkType,
  ): ListItemModel {
    val scope = rememberStableCoroutineScope()
    return ListItemModel(
      title = network.name.lowercase(),
      trailingAccessory = SwitchAccessory(
        model = SwitchModel(
          checked = network == debugOptions.bitcoinNetworkType,
          onCheckedChange = { isChecked ->
            if (isChecked) {
              scope.launch {
                debugOptionsService.setBitcoinNetworkType(network)
              }
            }
          }
        )
      )
    )
  }
}
