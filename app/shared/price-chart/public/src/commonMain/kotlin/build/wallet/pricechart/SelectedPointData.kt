package build.wallet.pricechart

sealed interface SelectedPointData {
  val isUserSelected: Boolean

  data class BtcPrice(
    override val isUserSelected: Boolean,
    val primaryText: String,
    val primaryValue: Long? = null,
    val secondaryText: String,
    val secondaryTimePeriodText: String,
    val direction: PriceDirection,
  ) : SelectedPointData

  data class Balance(
    override val isUserSelected: Boolean,
    val primaryFiatText: String,
    val primaryFiatValue: Long? = null,
    val secondaryFiatText: String,
    val primaryBtcText: String,
    val primaryBtcValue: Long? = null,
    val secondaryBtcText: String,
  ) : SelectedPointData
}
