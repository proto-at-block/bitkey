package build.wallet.pricechart

import bitkey.ui.framework_public.generated.resources.Res
import bitkey.ui.framework_public.generated.resources.chart_type_label_balance
import bitkey.ui.framework_public.generated.resources.chart_type_label_btc_price
import org.jetbrains.compose.resources.StringResource

enum class ChartType(val label: StringResource) {
  BTC_PRICE(Res.string.chart_type_label_btc_price),
  BALANCE(Res.string.chart_type_label_balance),
}
