package build.wallet.ui.app.partnerships

import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.partnerships.PartnerId
import build.wallet.partnerships.PartnerInfo
import build.wallet.statemachine.partnerships.expected.ExpectedTransactionNoticeModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class ExpectedTransactionNoticeScreenSnapshots : FunSpec({
  val paparazzi = paparazziExtension()

  test("expected transaction notice with partner info") {
    paparazzi.snapshot {
      FormScreen(
        model = ExpectedTransactionNoticeModel(
          partnerInfo = PartnerInfo(
            logoUrl = null,
            name = "Cash App",
            partnerId = PartnerId("partner-id"),
            logoBadgedUrl = null
          ),
          transactionDate = "Jan 4, 1:00 PM",
          onViewInPartnerApp = {},
          onBack = {}
        )
      )
    }
  }

  test("expected transaction notice without partner info") {
    paparazzi.snapshot {
      FormScreen(
        model = ExpectedTransactionNoticeModel(
          partnerInfo = null,
          transactionDate = "Jan 4, 1:00 PM",
          onViewInPartnerApp = {},
          onBack = {}
        )
      )
    }
  }
})
