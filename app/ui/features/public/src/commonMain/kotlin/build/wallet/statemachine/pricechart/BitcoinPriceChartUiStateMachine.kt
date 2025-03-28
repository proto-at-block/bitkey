package build.wallet.statemachine.pricechart

import androidx.compose.runtime.Stable
import build.wallet.pricechart.ChartType
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

interface BitcoinPriceChartUiStateMachine : StateMachine<BitcoinPriceChartUiProps, ScreenModel>

@Stable
data class BitcoinPriceChartUiProps(
  val initialType: ChartType,
  val onBuy: () -> Unit,
  val onTransfer: () -> Unit,
  val onBack: () -> Unit,
)
