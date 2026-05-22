package build.wallet.ui.app.moneyhome.receive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import build.wallet.partnerships.PartnerInfo
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.Icon.LargeIconWarningFilled
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.qr.QrCodeState
import build.wallet.statemachine.receive.AddressQrCodeBodyModel
import build.wallet.statemachine.receive.AddressQrCodeBodyModel.Content.Error
import build.wallet.statemachine.receive.AddressQrCodeBodyModel.Content.QrCode
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.button.buttonStyle
import build.wallet.ui.components.card.Card
import build.wallet.ui.components.header.Header
import build.wallet.ui.components.icon.IconImage
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.label.labelStyle
import build.wallet.ui.components.layout.Divider
import build.wallet.ui.components.qr.QrCode
import build.wallet.ui.components.qr.QrCodeLoader
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.system.BackHandler
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tokens.market.MarketIcons
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
    Column(modifier = Modifier.fillMaxSize()) {
      Box(modifier = Modifier.padding(horizontal = 20.dp)) {
        Toolbar(model = model.toolbarModel)
      }
      Box(
        modifier =
          Modifier
            .fillMaxWidth()
            .weight(1f)
      ) {
        val scrollState = rememberScrollState()

        Box(
          contentAlignment = Alignment.TopCenter,
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
                val isDesignSystemV2Enabled = true
                var isAddressExpanded by remember(content.addressDisplayString) {
                  mutableStateOf(false)
                }

                Box(modifier = Modifier.padding(horizontal = 32.dp)) {
                  QrCodeWithAddressCard(
                    onCopyClick = content.onCopyClick,
                    qrCodeState = content.qrCodeState
                  )
                }
                val copied = content.copyButtonIcon == Icon.SmallIconCheckFilled

                if (content.hideAddressByDefault) {
                  ExpandableAddressSection(
                    modifier = Modifier
                      .padding(horizontal = 32.dp),
                    expanded = isAddressExpanded,
                    onToggle = { isAddressExpanded = !isAddressExpanded },
                    onCopyClick = content.onCopyClick,
                    copied = copied,
                    address = content.addressDisplayString,
                    isLoadingPlaceholder = content.isAddressLoadingPlaceholder,
                    isRefreshing = content.isRefreshing,
                    onVerifyClick = content.onVerifyClick
                  )
                } else {
                  StaticAddressSection(
                    modifier = Modifier
                      .padding(horizontal = 32.dp),
                    onCopyClick = content.onCopyClick,
                    copied = copied,
                    address = content.addressDisplayString,
                    isLoadingPlaceholder = content.isAddressLoadingPlaceholder,
                    isRefreshing = content.isRefreshing,
                    onVerifyClick = content.onVerifyClick
                  )
                }
                Box(
                  modifier = Modifier
                    .padding(horizontal = 32.dp)
                    .padding(top = 8.dp)
                ) {
                  val disclaimerType = if (isDesignSystemV2Enabled) LabelType.Body4Mono else LabelType.Body4Regular
                  Label(
                    model = LabelModel.StringModel(
                      "This address only accepts Bitcoin (BTC). " +
                        "Sending other assets will result in permanent loss of funds."
                    ),
                    type = disclaimerType,
                    alignment = TextAlign.Start,
                    treatment = LabelTreatment.Secondary,
                    style = WalletTheme.labelStyle(disclaimerType, LabelTreatment.Secondary, TextAlign.Start)
                      .copy(fontSize = 10.sp)
                  )
                }
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

        // Gradient overlay at top of scroll area — fades in as user scrolls
        val gradientAlpha = (scrollState.value / 40f).coerceIn(0f, 1f)
        if (gradientAlpha > 0f) {
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(20.dp)
              .align(Alignment.TopCenter)
              .alpha(gradientAlpha)
              .background(
                brush = Brush.verticalGradient(
                  colors = listOf(
                    WalletTheme.colors.background,
                    WalletTheme.colors.background.copy(alpha = 0.65f),
                    Color.Transparent
                  )
                )
              )
          )
        }
      }

      // Sticky bottom action buttons
      when (val content = model.content) {
        is QrCode -> {
          CircularActionButtons(
            modifier = Modifier
              .padding(horizontal = 20.dp)
              .padding(vertical = 16.dp),
            partners = content.partners,
            onPartnerClick = content.onPartnerClick,
            onShareClick = content.onShareClick,
            loadingPartnerId = content.loadingPartnerId
          )
        }
        is Error -> Unit
      }
    }
  }
}

@Composable
private fun QrCodeWithAddressCard(
  onCopyClick: () -> Unit = {},
  qrCodeState: QrCodeState,
) {
  val isDesignSystemV2Enabled = true
  val cardBackgroundColor = if (isDesignSystemV2Enabled) {
    WalletTheme.colors.subtleBackground
  } else {
    WalletTheme.colors.containerBackground
  }
  val cardCornerRadius = if (isDesignSystemV2Enabled) 12.dp else 24.dp
  val cardBorderWidth = if (isDesignSystemV2Enabled) 0.dp else 2.dp
  val interactionSource = remember { MutableInteractionSource() }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = { onCopyClick() }
      ),
    backgroundColor = cardBackgroundColor,
    cornerRadius = cardCornerRadius,
    borderWidth = cardBorderWidth,
    paddingValues = PaddingValues(horizontal = 24.dp, vertical = 24.dp)
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
            centerIcon = Icon.SmallIconBitcoinStroked,
            backgroundColor = cardBackgroundColor
          )
        }
        is QrCodeState.Error -> {
          QrCodeError(
            modifier = Modifier.fillMaxWidth()
          )
        }
      }
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
  isLoadingPlaceholder: Boolean = false,
  isRefreshing: Boolean = false,
  textAlign: TextAlign = TextAlign.Center,
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
        is LabelModel.ChunkedAddressModel -> address.string
        else -> {
          isAnimating = false
          return@LaunchedEffect
        }
      }

      // Skip animation if address is just the loading placeholder.
      if (isLoadingPlaceholder) {
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
          is LabelModel.StringModel -> address.copy(string = randomized)
          is LabelModel.StringWithStyledSubstringModel ->
            address.copy(string = randomized)
          is LabelModel.ChunkedAddressModel -> address.copy(string = randomized)
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

  Label(
    modifier = modifier,
    model = displayedAddress,
    type = if (isLoadingPlaceholder) LabelType.Body3Mono else LabelType.Body2Mono,
    alignment = textAlign,
    treatment = LabelTreatment.Primary
  )
}

@Composable
private fun AnimatedCopyIcon(
  copied: Boolean,
  onClick: () -> Unit,
) {
  val copyIcon = if (copied) MarketIcons.CheckmarkCircleFill else MarketIcons.Copy

  Box(
    modifier = Modifier.clickable(onClick = onClick),
    contentAlignment = Alignment.Center
  ) {
    val scale = remember { Animatable(1f) }
    var displayedIcon by remember { mutableStateOf(copyIcon) }

    LaunchedEffect(copyIcon) {
      if (displayedIcon != copyIcon) {
        scale.animateTo(
          targetValue = 0f,
          animationSpec = tween(durationMillis = 150)
        )
        displayedIcon = copyIcon
        scale.animateTo(
          targetValue = 1f,
          animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f)
        )
      } else {
        // Snap to full scale if cancelled mid-animation and restarted
        scale.snapTo(1f)
      }
    }

    IconImage(
      model = IconModel(
        icon = displayedIcon,
        iconSize = IconSize.Accessory
      ),
      modifier = Modifier
        .graphicsLayer {
          scaleX = scale.value
          scaleY = scale.value
        },
      color = WalletTheme.colors.foreground60
    )
  }
}

@Composable
private fun AnimatedVerifyButton(onVerifyClick: (() -> Unit)?) {
  AnimatedVisibility(
    visible = onVerifyClick != null,
    enter = fadeIn(spring(stiffness = 400f)) + scaleIn(
      spring(dampingRatio = 0.6f, stiffness = 400f),
      initialScale = 0.8f
    ),
    exit = fadeOut(spring(stiffness = 500f)) + scaleOut(
      spring(stiffness = 500f),
      targetScale = 0.8f
    )
  ) {
    Button(
      text = "Verify",
      leadingIcon = Icon.SmallIconBitkey,
      style = WalletTheme.buttonStyle(
        treatment = ButtonModel.Treatment.Secondary,
        size = ButtonModel.Size.Compact
      ).copy(iconSize = IconSize.Custom(16)),
      enabled = onVerifyClick != null,
      onClick = { onVerifyClick?.invoke() }
    )
  }
}

@Composable
private fun ExpandableAddressSection(
  modifier: Modifier = Modifier,
  expanded: Boolean,
  onToggle: () -> Unit,
  onCopyClick: () -> Unit,
  copied: Boolean,
  address: LabelModel,
  isLoadingPlaceholder: Boolean,
  isRefreshing: Boolean,
  onVerifyClick: (() -> Unit)? = null,
) {
  Column(modifier = modifier.fillMaxWidth()) {
    // Header row
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(52.dp)
        .clickable(onClick = onToggle)
        .padding(vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Left group: chevron + label + copy
      Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        val chevronRotation by animateFloatAsState(
          targetValue = if (expanded) 90f else 0f,
          label = "chevron-rotation"
        )
        IconImage(
          model = IconModel(
            icon = MarketIcons.ChevronRight,
            iconSize = IconSize.Custom(14)
          ),
          modifier = Modifier.rotate(chevronRotation),
          color = WalletTheme.colors.foreground60
        )
        Label(
          text = "YOUR ADDRESS",
          type = LabelType.Body4Mono,
          treatment = LabelTreatment.Secondary
        )
        AnimatedCopyIcon(copied = copied, onClick = onCopyClick)
      }

      AnimatedVerifyButton(onVerifyClick = onVerifyClick)
    }

    // Expandable address content
    AnimatedVisibility(
      visible = expanded,
      enter = expandVertically(),
      exit = shrinkVertically()
    ) {
      AddressLabel(
        modifier = Modifier
          .clickable(onClick = onCopyClick)
          .padding(bottom = 16.dp),
        address = address,
        isLoadingPlaceholder = isLoadingPlaceholder,
        isRefreshing = isRefreshing,
        textAlign = TextAlign.Start
      )
    }

    Divider()
  }
}

@Composable
private fun StaticAddressSection(
  modifier: Modifier = Modifier,
  onCopyClick: () -> Unit,
  copied: Boolean,
  address: LabelModel,
  isLoadingPlaceholder: Boolean,
  isRefreshing: Boolean,
  onVerifyClick: (() -> Unit)? = null,
) {
  Column(modifier = modifier.fillMaxWidth()) {
    // Header row
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(52.dp)
        .padding(vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Label(
        text = "YOUR ADDRESS",
        type = LabelType.Body4Mono,
        treatment = LabelTreatment.Secondary
      )
      AnimatedCopyIcon(copied = copied, onClick = onCopyClick)
      Spacer(modifier = Modifier.weight(1f))
      AnimatedVerifyButton(onVerifyClick = onVerifyClick)
    }

    // Address always visible
    AddressLabel(
      modifier = Modifier
        .clickable(onClick = onCopyClick)
        .padding(bottom = 16.dp),
      address = address,
      isLoadingPlaceholder = isLoadingPlaceholder,
      isRefreshing = isRefreshing,
      textAlign = TextAlign.Start
    )

    Divider()
  }
}

@Composable
private fun CircularActionButtons(
  modifier: Modifier = Modifier,
  partners: ImmutableList<PartnerInfo>,
  onPartnerClick: (PartnerInfo) -> Unit,
  onShareClick: () -> Unit,
  loadingPartnerId: String? = null,
) {
  Row(
    modifier = modifier
      .then(
        if (partners.size > 1) {
          Modifier.horizontalScroll(rememberScrollState())
        } else {
          Modifier
        }
      ),
    horizontalArrangement = Arrangement.spacedBy(24.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    ActionButton(
      icon = Icon.SmallIconShare,
      text = "Share",
      onClick = onShareClick
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
