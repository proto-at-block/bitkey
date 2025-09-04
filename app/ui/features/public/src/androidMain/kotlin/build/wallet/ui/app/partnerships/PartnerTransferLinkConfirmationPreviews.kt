package build.wallet.ui.app.partnerships

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import build.wallet.partnerships.PartnerId
import build.wallet.partnerships.PartnerInfo
import build.wallet.statemachine.partnerships.transferlink.PartnerTransferLinkConfirmationFormBodyModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.tooling.PreviewWalletTheme

@Preview
@Composable
fun PartnerTransferLinkConfirmationPreview() {
  PreviewWalletTheme {
    FormScreen(
      model = PartnerTransferLinkConfirmationFormBodyModel(
        partnerInfo = PartnerInfo(
          logoUrl = "https://partner.me/logo.png",
          name = "Test Partner",
          partnerId = PartnerId("partner-id"),
          logoBadgedUrl = null
        ),
        onConfirm = {},
        onCancel = {}
      )
    )
  }
}
