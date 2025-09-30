package build.wallet.ui.app.moneyhome.receivev2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.Icon.LargeIconWarningFilled
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.qr.QrCodeState
import build.wallet.statemachine.receivev2.AddressQrCodeV2BodyModel
import build.wallet.statemachine.receivev2.AddressQrCodeV2BodyModel.Content.Error
import build.wallet.statemachine.receivev2.AddressQrCodeV2BodyModel.Content.QrCode
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.button.ButtonContentsList
import build.wallet.ui.components.button.RowOfButtons
import build.wallet.ui.components.card.Card
import build.wallet.ui.components.header.Header
import build.wallet.ui.components.icon.IconImage
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.qr.QrCodeLoader
import build.wallet.ui.components.qr.QrCodeV2
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.system.BackHandler
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType

@Composable
fun AddressQrCodeV2Screen(
  modifier: Modifier = Modifier,
  model: AddressQrCodeV2BodyModel,
) {
  BackHandler(onBack = model.onBack)
  Box(
    modifier =
      modifier
        .background(WalletTheme.colors.background)
        .padding(horizontal = 20.dp)
  ) {
    Column {
      Toolbar(model = model.toolbarModel)
      Box(
        modifier =
          Modifier
            .fillMaxWidth()
      ) {
        val scrollState = rememberScrollState()

        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier.fillMaxSize()
        ) {
          Column(
            modifier = Modifier
              .verticalScroll(scrollState)
              .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            when (val content = model.content) {
              is QrCode -> {
                QrCodeWithAddressCard(
                  onCopyClick = content.onCopyClick,
                  addressDisplayString = content.addressDisplayString,
                  qrCodeState = content.qrCodeState
                )
                Label(
                  modifier = Modifier.padding(top = 24.dp),
                  text = "This address only accepts Bitcoin (BTC). " +
                    "Sending other assets will result in permanent loss of funds.",
                  type = LabelType.Body4Regular,
                  alignment = TextAlign.Center,
                  treatment = LabelTreatment.Secondary
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
    }
  }
}

@Composable
private fun QrCodeWithAddressCard(
  onCopyClick: () -> Unit = {},
  addressDisplayString: LabelModel,
  qrCodeState: QrCodeState,
) {
  val interactionSource = remember { MutableInteractionSource() }
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = { onCopyClick() }
      ),
    cornerRadius = 24.dp,
    borderWidth = 2.dp,
    paddingValues = PaddingValues(horizontal = 24.dp, vertical = 24.dp)
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
      Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        contentAlignment = Alignment.Center
      ) {
        when (qrCodeState) {
          is QrCodeState.Loading -> {
            QrCodeLoader(
              modifier = Modifier.fillMaxWidth()
            )
          }
          is QrCodeState.Success -> {
            QrCodeV2(
              matrix = qrCodeState.matrix,
              centerIcon = Icon.BitcoinB
            )
          }
          is QrCodeState.Error -> {
            QrCodeError(
              modifier = Modifier.fillMaxWidth()
            )
          }
        }
      }

      AddressLabel(address = addressDisplayString)
    }
  }
}

@Composable
private fun QrCodeError(modifier: Modifier = Modifier) {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(8.dp))
      .background(WalletTheme.colors.subtleBackground)
      .padding(36.dp)
      .fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    IconImage(
      model = IconModel(
        iconImage = IconImage.LocalImage(Icon.SmallIconWarning),
        iconSize = IconSize.Small
      )
    )
    Label(
      modifier = Modifier.padding(top = 8.dp),
      type = LabelType.Body3Bold,
      text = "QR code unavailable",
      alignment = TextAlign.Center
    )
    Label(
      modifier = Modifier.padding(top = 2.dp),
      text = "Use the address below to deposit bitcoin to your Bitkey wallet.",
      type = LabelType.Body4Medium,
      treatment = LabelTreatment.Secondary,
      alignment = TextAlign.Center
    )
  }
}

@Composable
private fun AddressLabel(
  modifier: Modifier = Modifier,
  address: LabelModel,
) {
  Label(
    modifier = modifier,
    model = address,
    type = LabelType.Body2Mono,
    alignment = TextAlign.Center,
    treatment = LabelTreatment.Primary
  )
}
