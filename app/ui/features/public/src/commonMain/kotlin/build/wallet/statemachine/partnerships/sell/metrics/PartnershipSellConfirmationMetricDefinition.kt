package build.wallet.statemachine.partnerships.sell.metrics

import bitkey.metrics.MetricDefinition
import bitkey.metrics.MetricName
import bitkey.metrics.Variant
import build.wallet.partnerships.PartnerId

/**
 * Tracks the user journey of selling bitcoin through a partner, tracking from when the user is
 * redirected back into the Bitkey App until the end of sell confirmation.
 *
 * @see [PartnershipSellMetricDefinition]
 */
data object PartnershipSellConfirmationMetricDefinition : MetricDefinition {
  override val name = MetricName("partnership_sell_confirmation")

  sealed class Variants(override val name: String) : Variant<PartnershipSellConfirmationMetricDefinition> {
    /**
     * We do not have an enum to switch off of, so unfortunately we must take the partnerId raw
     * value instead of creating more distinct variants.
     */
    data class Partner(val partnerId: PartnerId) : Variants(partnerId.value)
  }
}
