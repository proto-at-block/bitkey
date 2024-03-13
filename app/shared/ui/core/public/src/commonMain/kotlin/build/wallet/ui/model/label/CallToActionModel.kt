package build.wallet.ui.model.label

/**
 * Call to action text that appears above the primary button
 */
data class CallToActionModel(
  val text: String,
  val treatment: Treatment = Treatment.SECONDARY,
) {
  enum class Treatment {
    SECONDARY,
    WARNING,
  }
}
