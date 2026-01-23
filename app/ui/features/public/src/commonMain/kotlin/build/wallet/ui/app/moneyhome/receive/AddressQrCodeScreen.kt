package build.wallet.ui.app.moneyhome.receive

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import build.wallet.partnerships.PartnerInfo
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.Icon.LargeIconWarningFilled
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.qr.QrCodeState
import build.wallet.statemachine.receive.AddressQrCodeBodyModel
import build.wallet.statemachine.receive.AddressQrCodeBodyModel.Content.Error
import build.wallet.statemachine.receive.AddressQrCodeBodyModel.Content.QrCode
import build.wallet.ui.components.card.Card
import build.wallet.ui.components.header.Header
import build.wallet.ui.components.icon.IconImage
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.qr.QrCode
import build.wallet.ui.components.qr.QrCodeLoader
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.system.BackHandler
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay

@Composable
fun AddressQrCodeScreen(
  modifier: Modifier = Modifier,
  model: AddressQrCodeBodyModel,
) {
  BackHandler(onBack = model.onBack)
  Box(
    modifier =
      modifier
        .background(WalletTheme.colors.background)
  ) {
    Column {
      Box(modifier = Modifier.padding(horizontal = 20.dp)) {
        Toolbar(model = model.toolbarModel)
      }
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
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                  QrCodeWithAddressCard(
                    onCopyClick = content.onCopyClick,
                    addressDisplayString = content.addressDisplayString,
                    qrCodeState = content.qrCodeState,
                    isRefreshing = content.isRefreshing
                  )
                }
                Box(
                  modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(top = 24.dp)
                ) {
                  Label(
                    text = "This address only accepts Bitcoin (BTC). " +
                      "Sending other assets will result in permanent loss of funds.",
                    type = LabelType.Body4Regular,
                    alignment = TextAlign.Center,
                    treatment = LabelTreatment.Secondary
                  )
                }
                CircularActionButtons(
                  modifier = Modifier
                    .padding(top = 40.dp, bottom = 16.dp)
                    .fillMaxWidth(),
                  partners = content.partners,
                  onPartnerClick = content.onPartnerClick,
                  onShareClick = content.onShareClick,
                  onCopyClick = content.onCopyClick,
                  copyButtonIcon = content.copyButtonIcon,
                  copyButtonLabelText = content.copyButtonLabelText,
                  loadingPartnerId = content.loadingPartnerId
                )
              }

              is Error ->
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
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
}

@Composable
private fun QrCodeWithAddressCard(
  onCopyClick: () -> Unit = {},
  addressDisplayString: LabelModel,
  qrCodeState: QrCodeState,
  isRefreshing: Boolean = false,
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
            QrCode(
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

      AddressLabel(
        address = addressDisplayString,
        isRefreshing = isRefreshing
      )
    }
  }
}

@Composable
private fun QrCodeError(modifier: Modifier = Modifier) {
  Column(
    modifier = modifier
      .clip(RoundedCornerShape(8.dp))
      .background(WalletTheme.colors.secondary)
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
  isRefreshing: Boolean = false,
) {
  // Track animation state
  var displayedAddress by remember { mutableStateOf(address) }
  var isAnimating by remember { mutableStateOf(false) }

  // Character pool for randomization (valid Bitcoin address characters)
  val charPool = remember { ('a'..'z') + ('A'..'Z') + ('0'..'9') }

  // Update displayed address when address changes (but not during animation)
  LaunchedEffect(address, isAnimating) {
    if (!isAnimating) {
      displayedAddress = address
    }
  }

  // Run animation while isRefreshing is true
  LaunchedEffect(isRefreshing, address) {
    if (isRefreshing) {
      isAnimating = true

      // Get the address string to randomize
      val addressString = when (address) {
        is LabelModel.StringModel -> address.string
        is LabelModel.StringWithStyledSubstringModel -> address.string
        else -> {
          isAnimating = false
          return@LaunchedEffect
        }
      }

      // Skip animation if address is just the loading placeholder "..."
      if (addressString == "...") {
        isAnimating = false
        return@LaunchedEffect
      }

      val frameDelay = 50L // Update every 50ms

      while (isRefreshing) {
        // Randomize each character (preserve spaces)
        val randomized = addressString.map { char ->
          if (char.isWhitespace()) char else charPool.random()
        }.joinToString("")

        displayedAddress = when (address) {
          is LabelModel.StringModel -> LabelModel.StringModel(randomized)
          is LabelModel.StringWithStyledSubstringModel -> {
            // Keep the styling structure but with randomized text
            LabelModel.StringModel(randomized)
          }
          else -> address
        }

        delay(frameDelay)
      }

      // Animation complete - restore actual address
      displayedAddress = address
      isAnimating = false
    } else {
      // When not refreshing, ensure we show the actual address
      displayedAddress = address
      isAnimating = false
    }
  }

  // Height accommodates 3 lines of Body2Mono text (typical for chunked address)
  Box(
    modifier = modifier,
    contentAlignment = Alignment.Center
  ) {
    Label(
      model = displayedAddress,
      type = LabelType.Body2Mono,
      alignment = TextAlign.Center,
      treatment = LabelTreatment.Primary
    )
  }
}

@Composable
private fun CircularActionButtons(
  modifier: Modifier = Modifier,
  partners: ImmutableList<PartnerInfo>,
  onPartnerClick: (PartnerInfo) -> Unit,
  onShareClick: () -> Unit,
  onCopyClick: () -> Unit,
  copyButtonIcon: Icon,
  copyButtonLabelText: String,
  loadingPartnerId: String? = null,
) {
  // Use horizontal scroll when there's more than 1 partner
  val shouldScroll = partners.size > 1

  if (shouldScroll) {
    Row(
      modifier = modifier
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(24.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // This inherits its spacing from the horizontalArrangement applied on this Row
      Spacer(modifier = Modifier)

      ActionButton(
        icon = Icon.SmallIconShare,
        text = "Share",
        onClick = onShareClick
      )

      ActionButton(
        icon = copyButtonIcon,
        text = copyButtonLabelText,
        onClick = onCopyClick
      )

      partners.forEach { partner ->
        PartnerActionButton(
          logoUrl = partner.logoUrl,
          name = partner.name,
          onClick = { onPartnerClick(partner) },
          isLoading = partner.partnerId.value == loadingPartnerId
        )
      }

      // This inherits its spacing from the horizontalArrangement applied on this Row
      Spacer(modifier = Modifier)
    }
  } else {
    Row(
      modifier = modifier.padding(horizontal = 20.dp),
      horizontalArrangement = Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically
    ) {
      ActionButton(
        icon = Icon.SmallIconShare,
        text = "Share",
        onClick = onShareClick
      )

      ActionButton(
        icon = copyButtonIcon,
        text = copyButtonLabelText,
        onClick = onCopyClick
      )

      partners.forEach { partner ->
        PartnerActionButton(
          logoUrl = partner.logoUrl,
          name = partner.name,
          onClick = { onPartnerClick(partner) },
          isLoading = partner.partnerId.value == loadingPartnerId
        )
      }
    }
  }
}
