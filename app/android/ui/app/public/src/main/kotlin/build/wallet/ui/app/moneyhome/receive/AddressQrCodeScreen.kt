package build.wallet.ui.app.moneyhome.receive

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.Icon.LargeIconWarningFilled
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.qr.QrCodeModel
import build.wallet.statemachine.receive.AddressQrCodeBodyModel
import build.wallet.statemachine.receive.AddressQrCodeBodyModel.Content.Error
import build.wallet.statemachine.receive.AddressQrCodeBodyModel.Content.QrCode
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.button.ButtonContentsList
import build.wallet.ui.components.button.RowOfButtons
import build.wallet.ui.components.card.Card
import build.wallet.ui.components.header.Header
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.qr.QrCode
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.model.button.ButtonModel.Treatment.Secondary
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tooling.PreviewWalletTheme

@Composable
fun AddressQrCodeScreen(model: AddressQrCodeBodyModel) {
  BackHandler(onBack = model.onBack)
  Column(
    modifier =
      Modifier
        .padding(horizontal = 20.dp)
        .fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Toolbar(model = model.toolbarModel)
    Column(
      modifier =
        Modifier
          .fillMaxSize(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      when (val content = model.content) {
        is QrCode -> {
          QrCodeWithAddressCard(
            qrCodeModel = content.addressQrCode,
            address = content.address ?: "..."
          )
          RowOfButtons(
            modifier =
              Modifier
                .padding(top = 24.dp, bottom = 16.dp),
            buttonContents =
              ButtonContentsList(
                listOf(
                  {
                    Box(
                      modifier = Modifier.weight(1F),
                      contentAlignment = Alignment.Center
                    ) {
                      Button(model = content.shareButtonModel)
                    }
                  },
                  {
                    Box(
                      modifier = Modifier.weight(1F),
                      contentAlignment = Alignment.Center
                    ) {
                      Button(model = content.copyButtonModel)
                    }
                  }
                )
              ),
            interButtonSpacing = 16.dp
          )
        }

        is Error ->
          Header(
            model =
              FormHeaderModel(
                headline = content.title,
                subline = content.subline,
                icon = LargeIconWarningFilled,
                alignment = FormHeaderModel.Alignment.CENTER
              )
          )
      }
    }
  }
}

@Composable
private fun QrCodeWithAddressCard(
  qrCodeModel: QrCodeModel?,
  address: String,
) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(bottom = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      QrCode(
        modifier =
          Modifier
            .padding(8.dp)
            .fillMaxWidth(),
        data = qrCodeModel?.data
      )
      AddressLabel(
        modifier = Modifier.padding(horizontal = 20.dp),
        address = address
      )
    }
  }
}

@Composable
private fun AddressLabel(
  modifier: Modifier = Modifier,
  address: String,
) {
  Label(
    modifier = modifier,
    text = address,
    type = LabelType.Body2Mono,
    alignment = TextAlign.Center,
    treatment = LabelTreatment.Secondary
  )
}

@Preview
@Composable
internal fun AddressQrCodeScreenPreview() {
  PreviewWalletTheme {
    AddressQrCodeScreen(
      AddressQrCodeBodyModel(
        onBack = {},
        onRefreshClick = {},
        content =
          QrCode(
            address = "bc1q xy2k gdyg jrsq tzq2 n0yr f249 3p83 kkfj hx0w lh",
            addressQrCode =
              QrCodeModel(
                data = "bitcoin:bc1q42lja79elem0anu8q8s3h2n687re9jax556pcc"
              ),
            copyButtonIcon = Icon.SmallIconCopy,
            copyButtonLabelText = "Copy",
            onCopyClick = {},
            onShareClick = {}
          )
      )
    )
  }
}

@Preview
@Composable
internal fun AddressQrCodeScreenErrorPreview() {
  PreviewWalletTheme {
    AddressQrCodeScreen(
      AddressQrCodeBodyModel(
        onBack = {},
        onRefreshClick = {},
        content =
          Error(
            title = "We couldn’t create an address",
            subline = "We are looking into this. Please try again later."
          )
      )
    )
  }
}
