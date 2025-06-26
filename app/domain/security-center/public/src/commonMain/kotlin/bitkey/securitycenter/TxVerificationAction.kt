package bitkey.securitycenter

import bitkey.verification.VerificationThreshold

class TxVerificationAction(
  private val threshold: VerificationThreshold,
) : SecurityAction {
  override fun getRecommendations(): List<SecurityActionRecommendation> {
    return if (threshold == VerificationThreshold.Disabled) {
      listOf(SecurityActionRecommendation.ENABLE_TRANSACTION_VERIFICATION)
    } else {
      emptyList()
    }
  }

  override fun category(): SecurityActionCategory {
    return SecurityActionCategory.SECURITY
  }

  override fun type(): SecurityActionType {
    return SecurityActionType.TRANSACTION_VERIFICATION
  }
}
