package build.wallet.statemachine.send.utxo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.bdk.bindings.BdkOutPoint
import build.wallet.bitcoin.bdk.bitcoinAmount
import build.wallet.bitcoin.bdk.transactionId
import build.wallet.bitcoin.transactions.BitcoinWalletService
import build.wallet.bitcoin.utxo.CoinControl
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.money.BitcoinMoney
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.statemachine.core.ScreenModel
import com.github.michaelbull.result.get
import kotlinx.collections.immutable.toImmutableList

@BitkeyInject(ActivityScope::class)
class UtxoSelectionUiStateMachineImpl(
  private val bitcoinWalletService: BitcoinWalletService,
  private val moneyDisplayFormatter: MoneyDisplayFormatter,
) : UtxoSelectionUiStateMachine {
  @Composable
  override fun model(props: UtxoSelectionUiProps): ScreenModel {
    val transactionsData by remember { bitcoinWalletService.transactionsData() }.collectAsState()
    val confirmedUtxos = remember(transactionsData) {
      transactionsData?.utxos?.confirmed.orEmpty()
    }

    var selectedOutpoints by remember {
      mutableStateOf(props.initialSelection?.outpoints.orEmpty())
    }

    val selectedTotal = remember(confirmedUtxos, selectedOutpoints) {
      confirmedUtxos
        .filter { it.outPoint in selectedOutpoints }
        .fold(BitcoinMoney.zero()) { acc, utxo -> acc + utxo.bitcoinAmount }
    }

    val targetAmount = props.targetAmount
    val underfunded =
      targetAmount != null && selectedOutpoints.isNotEmpty() && selectedTotal < targetAmount

    val headerSubline = when {
      confirmedUtxos.isEmpty() -> "No confirmed coins available."
      selectedOutpoints.isEmpty() -> "Select one or more confirmed coins to spend."
      underfunded && targetAmount != null -> {
        val selectedLabel = moneyDisplayFormatter.format(selectedTotal)
        val targetLabel = moneyDisplayFormatter.format(targetAmount)
        "Selected total ($selectedLabel) is less than the send amount ($targetLabel)."
      }
      else -> {
        val count = selectedOutpoints.size
        val totalLabel = moneyDisplayFormatter.format(selectedTotal)
        "$count selected · $totalLabel"
      }
    }

    val utxoItems = confirmedUtxos
      .sortedByDescending { it.txOut.value }
      .map { utxo ->
        val outpoint = utxo.outPoint
        UtxoSelectionListItem(
          valueLabel = moneyDisplayFormatter.format(utxo.bitcoinAmount),
          outpointLabel = "${utxo.transactionId.truncated()}:${outpoint.vout}",
          isSelected = outpoint in selectedOutpoints,
          onToggle = {
            selectedOutpoints = selectedOutpoints.toggle(outpoint)
          }
        )
      }
      .toImmutableList()

    return UtxoSelectionBodyModel(
      onBack = props.onBack,
      utxoItems = utxoItems,
      headerSubline = headerSubline,
      confirmEnabled = selectedOutpoints.isNotEmpty(),
      onConfirm = {
        val coinControl = CoinControl.create(
          inventory = confirmedUtxos,
          selected = selectedOutpoints
        ).get()
        if (coinControl != null) {
          props.onConfirm(coinControl)
        }
      },
      onClear = props.onClear,
      showClear = selectedOutpoints.isNotEmpty() || props.initialSelection != null
    ).asModalFullScreen()
  }
}

private fun Set<BdkOutPoint>.toggle(outpoint: BdkOutPoint): Set<BdkOutPoint> =
  if (outpoint in this) this - outpoint else this + outpoint
