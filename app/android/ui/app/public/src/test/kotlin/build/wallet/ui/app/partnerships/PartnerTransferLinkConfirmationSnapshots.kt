package build.wallet.ui.app.partnerships

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.partnerships.PartnerId
import build.wallet.partnerships.PartnerInfo
import build.wallet.statemachine.partnerships.transferlink.PartnerTransferLinkConfirmationFormBodyModel
import build.wallet.ui.app.paparazzi.snapshotSheet
import io.kotest.core.spec.style.FunSpec

class PartnerTransferLinkConfirmationSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("partner transfer link confirmation with partner info") {
    paparazzi.snapshotSheet(
      PartnerTransferLinkConfirmationFormBodyModel(
        partnerInfo = PartnerInfo(
          logoUrl = null,
          name = "Test Partner",
          partnerId = PartnerId("partner"),
          logoBadgedUrl = null
        ),
        onConfirm = {},
        onCancel = {}
      )
    )
  }
})
