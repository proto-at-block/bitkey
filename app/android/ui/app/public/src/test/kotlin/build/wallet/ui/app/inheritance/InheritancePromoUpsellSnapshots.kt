package build.wallet.ui.app.inheritance

import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import build.wallet.bitkey.promotions.PromotionCode
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.trustedcontact.PromoCodeUpsellBodyModel
import io.kotest.core.spec.style.FunSpec

class InheritancePromoUpsellSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("inheritance promo model") {
    paparazzi.snapshot {
      PromoCodeUpsellBodyModel(
        onBack = {},
        promoCode = PromotionCode("INHERITANCE-30-X5B4"),
        onClick = {},
        onContinue = {},
        onCopyCode = {},
        onShare = {},
        treatment = PromoCodeUpsellBodyModel.Treatment.ForBenefactor("Benny Benefactor")
      ).render(modifier = Modifier)
    }
  }

  test("inheritance promo model - scrollable") {
    paparazzi.snapshot(deviceConfig = DeviceConfig.NEXUS_4) {
      PromoCodeUpsellBodyModel(
        onBack = {},
        promoCode = PromotionCode("INHERITANCE-30-X5B4"),
        onClick = {},
        onContinue = {},
        onCopyCode = {},
        onShare = {},
        treatment = PromoCodeUpsellBodyModel.Treatment.ForBenefactor("Benny Benefactor")
      ).render(modifier = Modifier)
    }
  }
})
