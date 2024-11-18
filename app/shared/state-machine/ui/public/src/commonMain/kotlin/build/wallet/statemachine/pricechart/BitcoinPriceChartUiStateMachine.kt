package build.wallet.statemachine.pricechart

import androidx.compose.runtime.Stable
import build.wallet.bitkey.f8e.AccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.pricechart.ChartType
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

interface BitcoinPriceChartUiStateMachine : StateMachine<BitcoinPriceChartUiProps, ScreenModel>

@Stable
data class BitcoinPriceChartUiProps(
  val initialType: ChartType,
  val accountId: AccountId,
  val f8eEnvironment: F8eEnvironment,
  val onBack: () -> Unit,
)
