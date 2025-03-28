package build.wallet.pricechart

sealed class SelectedPointData {
  abstract val isUserSelected: Boolean

  data class BtcPrice(
    override val isUserSelected: Boolean,
    val primaryText: String,
    val secondaryText: String,
    val secondaryTimePeriodText: String,
    val direction: PriceDirection,
  ) : SelectedPointData()

  data class Balance(
    override val isUserSelected: Boolean,
    val primaryFiatText: String,
    val secondaryFiatText: String,
    val primaryBtcText: String,
    val secondaryBtcText: String,
  ) : SelectedPointData()
}
