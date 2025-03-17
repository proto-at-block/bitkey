package build.wallet.ui.model.slider

/**
 * Defines a slider component for amounts, allowing a user to drag the slider to
 * select a value for the amount in the given range.
 *
 * @property primaryAmount - The display string for the value in primary currency
 * @property secondaryAmount - The display string for the value in secondary currency
 *
 * @property value - the current value as a [Float]
 * @property valueRange - The range of possible values
 * @property onValueUpdate - action invoked once the user updates the value to be set
 *
 * @property isEnabled - whether the slider can be moved
 */
data class AmountSliderModel(
  val primaryAmount: String,
  val secondaryAmount: String,
  val value: Float,
  val valueRange: ClosedFloatingPointRange<Float>,
  val onValueUpdate: (Float) -> Unit,
  val isEnabled: Boolean,
) {
  init {
    require(value in valueRange)
  }
}
