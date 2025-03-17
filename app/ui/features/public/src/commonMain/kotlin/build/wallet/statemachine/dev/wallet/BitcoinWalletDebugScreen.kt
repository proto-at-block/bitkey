package build.wallet.statemachine.dev.wallet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import bitkey.ui.framework.Navigator
import bitkey.ui.framework.Screen
import bitkey.ui.framework.ScreenPresenter
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.bdk.bitcoinAmount
import build.wallet.bitcoin.bdk.transactionId
import build.wallet.bitcoin.explorer.BitcoinExplorer
import build.wallet.bitcoin.explorer.BitcoinExplorerType.Mempool
import build.wallet.bitcoin.transactions.BitcoinTransactionId
import build.wallet.bitcoin.transactions.BitcoinWalletService
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.dev.DebugMenuScreen

/**
 * Shows debug options for current Bitcoin wallet.
 */
data object BitcoinWalletDebugScreen : Screen

@BitkeyInject(ActivityScope::class, boundTypes = [BitcoinWalletDebugScreenPresenter::class])
class BitcoinWalletDebugScreenPresenter(
  private val bitcoinWalletService: BitcoinWalletService,
  private val moneyDisplayFormatter: MoneyDisplayFormatter,
  private val bitcoinExplorer: BitcoinExplorer,
  private val inAppBrowserNavigator: InAppBrowserNavigator,
) : ScreenPresenter<BitcoinWalletDebugScreen> {
  @Composable
  override fun model(
    navigator: Navigator,
    screen: BitcoinWalletDebugScreen,
  ): ScreenModel {
    val spendingWallet by remember { bitcoinWalletService.spendingWallet() }.collectAsState()
    val transactionsData by remember { bitcoinWalletService.transactionsData() }.collectAsState()

    val utxos = remember(transactionsData) { transactionsData?.utxos }

    val utxoModels = remember(spendingWallet, utxos) {
      utxos?.all?.map { utxo ->
        val utxoValueString = moneyDisplayFormatter.format(utxo.bitcoinAmount)
        val utxoTxId = utxo.transactionId
        UtxoRowModel(
          value = utxoValueString,
          txId = utxoTxId.truncated(),
          isConfirmed = utxo in utxos.confirmed,
          onClick = {
            spendingWallet?.networkType?.let { networkType ->
              openTransactionInExplorer(
                networkType = networkType,
                id = utxoTxId,
                vout = utxo.outPoint.vout.toInt()
              )
            }
          }
        )
      }
    }

    return BitcoinWalletDebugBodyModel(
      onBack = {
        navigator.goTo(DebugMenuScreen)
      },
      utxos = utxoModels
    ).asModalFullScreen()
  }

  private fun openTransactionInExplorer(
    networkType: BitcoinNetworkType,
    id: BitcoinTransactionId,
    vout: Int,
  ) {
    inAppBrowserNavigator.open(
      bitcoinExplorer.getTransactionUrl(
        txId = id.value,
        vout = vout,
        network = networkType,
        explorerType = Mempool
      ),
      onClose = {}
    )
  }
}
