package build.wallet.statemachine.send

import build.wallet.ui.model.Model

sealed class TransferScreenBannerModel : Model() {
  data object HardwareRequiredBannerModel : TransferScreenBannerModel()

  data object AmountEqualOrAboveBalanceBannerModel : TransferScreenBannerModel()

  data object F8eUnavailableBannerModel : TransferScreenBannerModel()
}
