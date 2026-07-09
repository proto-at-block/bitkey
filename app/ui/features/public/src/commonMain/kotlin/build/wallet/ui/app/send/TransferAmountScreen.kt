package build.wallet.ui.app.send

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement.Center
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import build.wallet.amount.KeypadButton
import build.wallet.platform.haptics.HapticsEffect
import build.wallet.statemachine.keypad.KeypadModel
import build.wallet.statemachine.moneyhome.card.CardModel
import build.wallet.statemachine.send.TransferAmountBodyModel
import build.wallet.ui.app.core.form.FormScreen
import build.wallet.ui.components.amount.AmountEntryKeypadFeedback
import build.wallet.ui.components.amount.AnimatedHeroAmount
import build.wallet.ui.components.amount.amountEntryKeypadFeedback
import build.wallet.ui.components.amount.rememberAmountEntryShakeOffset
import build.wallet.ui.components.button.Button
import build.wallet.ui.components.card.Card
import build.wallet.ui.components.card.CardContent
import build.wallet.ui.components.icon.Icon
import build.wallet.ui.components.icon.IconImage
import build.wallet.ui.components.keypad.Keypad
import build.wallet.ui.components.label.AutoResizedLabel
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.label.LabelTreatment.Disabled
import build.wallet.ui.components.label.LabelTreatment.Primary
import build.wallet.ui.components.toolbar.AmountEntryToolbar
import build.wallet.ui.components.toolbar.amountEntryBackgroundColor
import build.wallet.ui.components.toolbar.rememberConditionally
import build.wallet.ui.compose.thenIf
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import build.wallet.ui.tokens.currentStyle
import build.wallet.statemachine.core.Icon
import kotlinx.coroutines.delay

private const val AMOUNT_SWAP_ANIMATION_DURATION_MS = 180
private const val AMOUNT_SWAP_ICON_ANIMATION_DURATION_MS = 60
private const val AMOUNT_SWAP_DECIMAL_SUFFIX_ANIMATION_DURATION_MS = 90
private val AMOUNT_SWAP_STACK_OVERLAP = (-4).dp
private val AMOUNT_SWAP_BOTTOM_TOP_PADDING = 2.dp
private val TRAILING_DECIMAL_SUFFIX_REGEX = Regex("^(.*?)([.,]\\d{2})$")

private data class PendingAmountSwap(
  val sourcePrimaryAmount: String,
  val sourceSecondaryAmount: String,
)

private data class ActiveAmountSwap(
  val sourcePrimaryAmount: String,
  val sourceSecondaryAmount: String,
  val targetPrimaryAmount: String,
  val targetSecondaryAmount: String,
  val contextLineTreatment: LabelTreatment,
  val disabled: Boolean,
  val showSwapIcon: Boolean,
  val shouldAnimate: Boolean,
)

private data class TrailingDecimalSuffix(
  val baseAmount: String,
  val decimals: String,
)

private data class AmountSwapController(
  val activeAmountSwap: ActiveAmountSwap?,
  val primaryAmountAnimationResetKey: Int,
  val prepareForSwap: () -> Unit,
)

@Composable
@Suppress("CyclomaticComplexMethod")
fun TransferAmountScreen(
  modifier: Modifier = Modifier,
  model: TransferAmountBodyModel,
) {
  val horizontalPadding = 20.dp
  val backgroundColor = amountEntryBackgroundColor()
  val shouldTriggerInsufficientFundsFeedback = model.shouldTriggerContextualErrorFeedback
  var lastKeypadFeedback by remember { mutableStateOf<AmountEntryKeypadFeedback?>(null) }
  var keypadPressCount by remember { mutableIntStateOf(0) }
  val amountSwapController = rememberAmountSwapController(model)
  val feedbackForKeypadButton = { keypadButton: KeypadButton ->
    amountEntryKeypadFeedback(
      keypadButton = keypadButton,
      isButtonPressRejected = model.keypadModel.isButtonPressRejected(keypadButton),
      shouldTriggerContextualErrorFeedback = shouldTriggerInsufficientFundsFeedback
    )
  }
  val amountShakeOffsetPx =
    rememberAmountEntryShakeOffset(
      trigger =
        if (lastKeypadFeedback?.shouldShake == true) {
          keypadPressCount
        } else {
          null
        },
      hapticsEffect = HapticsEffect.Reject
    )

  FormScreen(
    modifier = modifier,
    onBack = model.onBack,
    background = backgroundColor,
    horizontalPadding = 0, // Manually apply padding so keypad can extend to edges
    toolbarContent = {
      AmountEntryToolbar(
        modifier = Modifier.padding(horizontal = horizontalPadding),
        model = model.toolbar
      )
    },
    mainContent = {
      Spacer(Modifier.weight(1F))
      Box(
        modifier =
          Modifier.align(CenterHorizontally)
            .graphicsLayer { translationX = amountShakeOffsetPx }
            .padding(horizontal = 20.dp)
            .thenIf(amountSwapController.activeAmountSwap == null) { Modifier.clipToBounds() }
      ) {
        AnimatedHeroAmount(
          modifier =
            Modifier.graphicsLayer {
              alpha = if (amountSwapController.activeAmountSwap != null) 0f else 1f
            },
          primaryAmount = model.amountModel.primaryAmount,
          primaryAmountGhostedSubstringRange = model.amountModel.primaryAmountGhostedSubstringRange,
          primaryAmountAnimationResetKey = amountSwapController.primaryAmountAnimationResetKey,
          primaryAmountLabelType = LabelType.Display1,
          contextLine = model.amountModel.secondaryAmount,
          contextLineTreatment = model.amountContextLineTreatment,
          centerContent = true,
          disabled = model.amountDisabled,
          onSwapClick =
            model.onSwapCurrencyClick?.let { onSwapCurrencyClick ->
              {
                lastKeypadFeedback = null
                amountSwapController.prepareForSwap()
                onSwapCurrencyClick()
              }
            }
        )
        amountSwapController.activeAmountSwap?.let { amountSwap ->
          AnimatedAmountSwap(
            sourcePrimaryAmount = amountSwap.sourcePrimaryAmount,
            sourceSecondaryAmount = amountSwap.sourceSecondaryAmount,
            targetPrimaryAmount = amountSwap.targetPrimaryAmount,
            targetSecondaryAmount = amountSwap.targetSecondaryAmount,
            contextLineTreatment = amountSwap.contextLineTreatment,
            disabled = amountSwap.disabled,
            showSwapIcon = amountSwap.showSwapIcon,
            shouldAnimate = amountSwap.shouldAnimate,
            modifier = Modifier.fillMaxWidth()
          )
        }
      }

      Spacer(Modifier.weight(1F))

      if (model.useSmartBar) {
        SmartBar(
          modifier = Modifier
            .align(CenterHorizontally)
            .padding(bottom = 16.dp),
          model = model.cardModel
        )
      } else {
        Spacer(Modifier.height(16.dp))
      }

      Keypad(
        modifier =
          Modifier.padding(horizontal = horizontalPadding),
        showDecimal = model.keypadModel.showDecimal,
        hapticsEffectForButtonPress = {
            keypadButton ->
          feedbackForKeypadButton(keypadButton).hapticsEffect
        },
        onButtonPress = rememberTrackingKeypadPresses(model.keypadModel, onKeypadButtonPressed = {
          lastKeypadFeedback = feedbackForKeypadButton(it)
          keypadPressCount += 1
        })
      )
    },
    footerContent = {
      Button(
        modifier = Modifier.padding(horizontal = horizontalPadding),
        model = model.primaryButton
      )
    }
  )
}

/**
 * Owns the transient swap-overlay state so the main screen stays focused on layout.
 *
 * We first pin the current hero amount in place, then wait for the swapped model to arrive before
 * starting the cross-fade / motion overlay. That keeps the hero from animating through unrelated
 * intermediate values during a fiat/BTC toggle.
 */
@Composable
private fun rememberAmountSwapController(model: TransferAmountBodyModel): AmountSwapController {
  var primaryAmountAnimationResetCount by remember { mutableIntStateOf(0) }
  var pendingSwapAnimationReset by remember { mutableStateOf(false) }
  var swapSourcePrimaryAmount by remember { mutableStateOf<String?>(null) }
  var pendingAmountSwap by remember { mutableStateOf<PendingAmountSwap?>(null) }
  var activeAmountSwap by remember { mutableStateOf<ActiveAmountSwap?>(null) }

  LaunchedEffect(
    pendingSwapAnimationReset,
    model.amountModel.primaryAmount
  ) {
    if (!pendingSwapAnimationReset) return@LaunchedEffect

    val swapHasStarted = model.amountModel.primaryAmount != swapSourcePrimaryAmount
    if (!swapHasStarted) return@LaunchedEffect

    primaryAmountAnimationResetCount += 1
    pendingSwapAnimationReset = false
    swapSourcePrimaryAmount = null
  }

  LaunchedEffect(
    pendingAmountSwap,
    model.amountModel.primaryAmount,
    model.amountModel.secondaryAmount,
    model.amountContextLineTreatment,
    model.amountDisabled
  ) {
    val pendingSwap = pendingAmountSwap ?: return@LaunchedEffect
    val swapHasStarted =
      model.amountModel.primaryAmount != pendingSwap.sourcePrimaryAmount ||
        model.amountModel.secondaryAmount != pendingSwap.sourceSecondaryAmount

    if (!swapHasStarted) return@LaunchedEffect

    val secondaryAmount = model.amountModel.secondaryAmount ?: return@LaunchedEffect
    activeAmountSwap =
      ActiveAmountSwap(
        sourcePrimaryAmount = pendingSwap.sourcePrimaryAmount,
        sourceSecondaryAmount = pendingSwap.sourceSecondaryAmount,
        targetPrimaryAmount = model.amountModel.primaryAmount,
        targetSecondaryAmount = secondaryAmount,
        contextLineTreatment = model.amountContextLineTreatment,
        disabled = model.amountDisabled,
        showSwapIcon = model.onSwapCurrencyClick != null,
        shouldAnimate = true
      )
    pendingAmountSwap = null
  }

  LaunchedEffect(activeAmountSwap) {
    val swapAnimation = activeAmountSwap ?: return@LaunchedEffect
    delay(AMOUNT_SWAP_ANIMATION_DURATION_MS.toLong())
    if (activeAmountSwap == swapAnimation) {
      activeAmountSwap = null
    }
  }

  return AmountSwapController(
    activeAmountSwap = activeAmountSwap,
    primaryAmountAnimationResetKey = primaryAmountAnimationResetCount,
    prepareForSwap = {
      model.amountModel.secondaryAmount?.let { secondaryAmount ->
        swapSourcePrimaryAmount = model.amountModel.primaryAmount
        pendingSwapAnimationReset = true
        activeAmountSwap =
          ActiveAmountSwap(
            sourcePrimaryAmount = model.amountModel.primaryAmount,
            sourceSecondaryAmount = secondaryAmount,
            targetPrimaryAmount = model.amountModel.primaryAmount,
            targetSecondaryAmount = secondaryAmount,
            contextLineTreatment = model.amountContextLineTreatment,
            disabled = model.amountDisabled,
            showSwapIcon = true,
            shouldAnimate = false
          )
        pendingAmountSwap =
          PendingAmountSwap(
            sourcePrimaryAmount = model.amountModel.primaryAmount,
            sourceSecondaryAmount = secondaryAmount
          )
      }
    }
  )
}

/**
 * Renders the fiat/BTC swap overlay.
 *
 * The overlay is intentionally separate from [AnimatedHeroAmount] so we can animate between two
 * different label hierarchies without disturbing the steady-state hero layout.
 */
@Composable
@Suppress("CyclomaticComplexMethod")
private fun AnimatedAmountSwap(
  sourcePrimaryAmount: String,
  sourceSecondaryAmount: String,
  targetPrimaryAmount: String,
  targetSecondaryAmount: String,
  contextLineTreatment: LabelTreatment,
  disabled: Boolean,
  showSwapIcon: Boolean,
  shouldAnimate: Boolean,
  modifier: Modifier = Modifier,
) {
  val targetSecondaryTrailingDecimals = remember(targetSecondaryAmount) {
    targetSecondaryAmount.toTrailingDecimalSuffixOrNull()
  }
  val sourceSecondaryTrailingDecimals = remember(sourceSecondaryAmount) {
    sourceSecondaryAmount.toTrailingDecimalSuffixOrNull()
  }
  val primaryBottomSuffix =
    targetSecondaryTrailingDecimals?.takeIf { it.baseAmount == sourcePrimaryAmount }
  val secondaryBottomSuffix =
    sourceSecondaryTrailingDecimals?.takeIf { it.baseAmount == targetPrimaryAmount }
  val movingPrimaryAmount = primaryBottomSuffix?.baseAmount ?: sourcePrimaryAmount
  val movingSecondaryAmount = secondaryBottomSuffix?.baseAmount ?: sourceSecondaryAmount
  var animationStarted by remember { mutableStateOf(false) }
  LaunchedEffect(shouldAnimate) {
    animationStarted = shouldAnimate
  }
  val progress by animateFloatAsState(
    targetValue = if (animationStarted) 1f else 0f,
    animationSpec = tween(
      durationMillis = AMOUNT_SWAP_ANIMATION_DURATION_MS,
      easing = LinearOutSlowInEasing
    ),
    label = "amount-swap-progress"
  )
  val iconProgress by animateFloatAsState(
    targetValue = if (animationStarted) 1f else 0f,
    animationSpec = tween(
      durationMillis = AMOUNT_SWAP_ICON_ANIMATION_DURATION_MS,
      easing = LinearOutSlowInEasing
    ),
    label = "amount-swap-icon-progress"
  )
  val decimalSuffixProgress by animateFloatAsState(
    targetValue = if (animationStarted) 1f else 0f,
    animationSpec = tween(
      durationMillis = AMOUNT_SWAP_DECIMAL_SUFFIX_ANIMATION_DURATION_MS,
      easing = LinearOutSlowInEasing
    ),
    label = "amount-swap-decimal-progress"
  )
  val primaryTreatment = if (disabled) Disabled else Primary
  val secondaryTreatment = if (disabled) Disabled else contextLineTreatment
  val primaryStyle = LabelType.Display1.currentStyle(TextStyle.Default)
  val secondaryStyle = LabelType.Body1Medium.currentStyle(TextStyle.Default)
  val density = LocalDensity.current
  val textMeasurer = rememberTextMeasurer()
  val primaryLineHeightDp = with(density) { primaryStyle.lineHeight.toDp() }
  val secondaryLineHeightDp = with(density) { secondaryStyle.lineHeight.toDp() }
  val bottomBaseOffsetPx = with(density) {
    (primaryLineHeightDp + AMOUNT_SWAP_STACK_OVERLAP + AMOUNT_SWAP_BOTTOM_TOP_PADDING).toPx()
  }
  val swapIconSpacingDp = if (showSwapIcon) 4.dp else 0.dp
  val swapIconSizeDp =
    when {
      !showSwapIcon -> 0.dp
      else -> 20.dp
    }
  val swapIconSpacingPx = with(density) { swapIconSpacingDp.toPx() }
  val bottomTargetTextWidthPx = remember(targetSecondaryAmount, secondaryStyle) {
    textMeasurer.measure(targetSecondaryAmount, style = secondaryStyle).size.width.toFloat()
  }
  val bottomSourceTextWidthPx = remember(sourceSecondaryAmount, secondaryStyle) {
    textMeasurer.measure(sourceSecondaryAmount, style = secondaryStyle).size.width.toFloat()
  }
  val bottomIconLanePx = with(density) { (swapIconSpacingDp + swapIconSizeDp).toPx() }
  val iconTopPaddingDp =
    primaryLineHeightDp +
      AMOUNT_SWAP_STACK_OVERLAP +
      AMOUNT_SWAP_BOTTOM_TOP_PADDING +
      ((secondaryLineHeightDp - swapIconSizeDp) / 2f).coerceAtLeast(0.dp)
  val primaryLineHeightPx = with(density) { primaryStyle.lineHeight.toPx() }
  val secondaryLineHeightPx = with(density) { secondaryStyle.lineHeight.toPx() }
  val movingPrimaryTopTextHeightPx = remember(movingPrimaryAmount, primaryStyle) {
    textMeasurer.measure(movingPrimaryAmount, style = primaryStyle).size.height.toFloat()
  }
  val movingPrimaryBottomTextHeightPx = remember(movingPrimaryAmount, secondaryStyle) {
    textMeasurer.measure(movingPrimaryAmount, style = secondaryStyle).size.height.toFloat()
  }
  val movingSecondaryTopTextHeightPx = remember(movingSecondaryAmount, primaryStyle) {
    textMeasurer.measure(movingSecondaryAmount, style = primaryStyle).size.height.toFloat()
  }
  val movingSecondaryTopTextWidthPx = remember(movingSecondaryAmount, primaryStyle) {
    textMeasurer.measure(movingSecondaryAmount, style = primaryStyle).size.width.toFloat()
  }
  val movingSecondaryBottomTextHeightPx = remember(movingSecondaryAmount, secondaryStyle) {
    textMeasurer.measure(movingSecondaryAmount, style = secondaryStyle).size.height.toFloat()
  }
  val movingPrimaryBottomTextWidthPx = remember(movingPrimaryAmount, secondaryStyle) {
    textMeasurer.measure(movingPrimaryAmount, style = secondaryStyle).size.width.toFloat()
  }
  val primaryBottomSuffixWidthPx = remember(primaryBottomSuffix?.decimals, secondaryStyle) {
    primaryBottomSuffix?.decimals?.let {
      textMeasurer.measure(it, style = secondaryStyle).size.width.toFloat()
    } ?: 0f
  }
  val secondaryBottomSuffixWidthPx = remember(secondaryBottomSuffix?.decimals, secondaryStyle) {
    secondaryBottomSuffix?.decimals?.let {
      textMeasurer.measure(it, style = secondaryStyle).size.width.toFloat()
    } ?: 0f
  }
  val containerHeight = (
    primaryLineHeightDp +
      secondaryLineHeightDp +
      AMOUNT_SWAP_STACK_OVERLAP +
      AMOUNT_SWAP_BOTTOM_TOP_PADDING
  ).coerceAtLeast(primaryLineHeightDp)

  Box(
    modifier = modifier
      .height(containerHeight)
      .semantics(mergeDescendants = true) {
        contentDescription = "$targetPrimaryAmount $targetSecondaryAmount"
      }
  ) {
    val sourceBottomCenterOffsetPx =
      if (showSwapIcon) {
        -(bottomIconLanePx / 2f)
      } else {
        0f
      }
    val sourcePrimaryTranslationXPx =
      (sourceBottomCenterOffsetPx * progress) -
        ((primaryBottomSuffixWidthPx / 2f) * decimalSuffixProgress)
    val sourceSecondaryTranslationXPx =
      (sourceBottomCenterOffsetPx * (1f - progress)) -
        ((secondaryBottomSuffixWidthPx / 2f) * (1f - decimalSuffixProgress))
    val sourcePrimaryStartScale =
      (movingPrimaryTopTextHeightPx / movingPrimaryBottomTextHeightPx).takeIf { it.isFinite() } ?: 1f
    val sourceSecondaryStartScale =
      (movingSecondaryBottomTextHeightPx / movingSecondaryTopTextHeightPx).takeIf { it.isFinite() } ?: 1f
    val sourcePrimaryScale =
      sourcePrimaryStartScale + ((1f - sourcePrimaryStartScale) * progress)
    val sourceSecondaryScale =
      sourceSecondaryStartScale + ((1f - sourceSecondaryStartScale) * progress)
    val primaryTopSlotTopPx =
      ((primaryLineHeightPx - movingPrimaryTopTextHeightPx) / 2f).coerceAtLeast(0f)
    val primaryBottomSlotTopPx =
      bottomBaseOffsetPx +
        ((secondaryLineHeightPx - movingPrimaryBottomTextHeightPx) / 2f).coerceAtLeast(0f)
    val secondaryTopSlotTopPx =
      ((primaryLineHeightPx - movingSecondaryTopTextHeightPx) / 2f).coerceAtLeast(0f)
    val secondaryBottomSlotTopPx =
      bottomBaseOffsetPx +
        ((secondaryLineHeightPx - movingSecondaryBottomTextHeightPx) / 2f).coerceAtLeast(0f)
    val sourcePrimaryVisualTopPx =
      primaryTopSlotTopPx + ((primaryBottomSlotTopPx - primaryTopSlotTopPx) * progress)
    val sourceSecondaryVisualTopPx =
      secondaryBottomSlotTopPx + ((secondaryTopSlotTopPx - secondaryBottomSlotTopPx) * progress)
    val sourcePrimaryTranslationYPx =
      sourcePrimaryVisualTopPx -
        ((movingPrimaryBottomTextHeightPx * (1f - sourcePrimaryScale)) / 2f)
    val sourceSecondaryTranslationYPx =
      sourceSecondaryVisualTopPx -
        ((movingSecondaryTopTextHeightPx * (1f - sourceSecondaryScale)) / 2f)
    val iconTranslationXPx =
      if (showSwapIcon) {
        ((bottomSourceTextWidthPx / 2f) + (swapIconSpacingPx / 2f)) * (1f - iconProgress) +
          ((bottomTargetTextWidthPx / 2f) + (swapIconSpacingPx / 2f)) * iconProgress
      } else {
        0f
      }
    val bottomSuffixText = primaryBottomSuffix?.decimals ?: secondaryBottomSuffix?.decimals
    val bottomSuffixAlpha =
      when {
        primaryBottomSuffix != null -> decimalSuffixProgress
        secondaryBottomSuffix != null -> 1f - decimalSuffixProgress
        else -> 0f
      }
    val bottomSuffixBaseTextWidthPx =
      when {
        primaryBottomSuffix != null -> movingPrimaryBottomTextWidthPx
        secondaryBottomSuffix != null -> movingSecondaryTopTextWidthPx * sourceSecondaryStartScale
        else -> 0f
      }
    val bottomSuffixTranslationXPx = sourceBottomCenterOffsetPx + (bottomSuffixBaseTextWidthPx / 2f)

    if (showSwapIcon) {
      Box(
        modifier =
          Modifier
            .align(Alignment.TopCenter)
            .padding(top = iconTopPaddingDp)
            .graphicsLayer {
              translationX = iconTranslationXPx
            }
      ) {
        IconImage(
          modifier = Modifier.graphicsLayer { rotationZ = 90f },
          model = IconModel(
            icon = Icon.DualRotatingArrows,
            iconSize = IconSize.Custom(20)
          ),
          color = if (disabled) WalletTheme.colors.foreground10 else WalletTheme.colors.foreground60
        )
      }
    }
    if (bottomSuffixText != null && bottomSuffixAlpha > 0f) {
      AutoResizedLabel(
        modifier =
          Modifier
            .fillMaxWidth()
            .align(Alignment.TopCenter)
            .graphicsLayer {
              translationX = bottomSuffixTranslationXPx
              translationY = bottomBaseOffsetPx
              alpha = bottomSuffixAlpha
            },
        text = bottomSuffixText,
        type = LabelType.Body1Medium,
        treatment = secondaryTreatment,
        alignment = TextAlign.Center
      )
    }
    AutoResizedLabel(
      modifier =
        Modifier
          .fillMaxWidth()
          .align(Alignment.TopCenter)
          .graphicsLayer {
            translationX = sourcePrimaryTranslationXPx
            translationY = sourcePrimaryTranslationYPx
            scaleX = sourcePrimaryScale
            scaleY = sourcePrimaryScale
          },
      text = movingPrimaryAmount,
      type = LabelType.Body1Medium,
      treatment = secondaryTreatment,
      alignment = TextAlign.Center
    )
    AutoResizedLabel(
      modifier =
        Modifier
          .fillMaxWidth()
          .align(Alignment.TopCenter)
          .graphicsLayer {
            translationX = sourceSecondaryTranslationXPx
            translationY = sourceSecondaryTranslationYPx
            scaleX = sourceSecondaryScale
            scaleY = sourceSecondaryScale
          },
      text = movingSecondaryAmount,
      type = LabelType.Display1,
      treatment = primaryTreatment,
      alignment = TextAlign.Center
    )
  }
}

private fun String.toTrailingDecimalSuffixOrNull(): TrailingDecimalSuffix? {
  val match = TRAILING_DECIMAL_SUFFIX_REGEX.matchEntire(this) ?: return null
  val (baseAmount, decimals) = match.destructured
  if (baseAmount.isBlank()) return null
  return TrailingDecimalSuffix(
    baseAmount = baseAmount,
    decimals = decimals
  )
}

@Composable
private fun rememberTrackingKeypadPresses(
  keypadModel: KeypadModel,
  onKeypadButtonPressed: (KeypadButton) -> Unit,
): (KeypadButton) -> Unit {
  return remember(keypadModel, onKeypadButtonPressed) {
    { keypadButton ->
      onKeypadButtonPressed(keypadButton)
      keypadModel.onButtonPress(keypadButton)
    }
  }
}

/**
 * A design primitive within the transfer amount screen that allows us to surface actionable behaviours
 * based on the user's context.
 *
 * For instance, the Smart Bar will be able to tell users when the amount they enter would require a
 * HW tap, or when the user looks like they are trying to sweep their wallet. It leverages the same
 * underlying [CardModel] that other card primitives also use today.
 */
@Composable
private fun SmartBar(
  modifier: Modifier = Modifier,
  model: CardModel?,
) {
  Box(
    // banner space is always taken up even when it's not visible.
    // This is a workaround so that the amount above the banner doesn't jump when the banner changes visibility.
    modifier =
      modifier.height(48.dp)
        .clickable(
          interactionSource = MutableInteractionSource(),
          indication = null,
          onClick = { model?.onClick?.invoke() }
        ),
    contentAlignment = Alignment.Center
  ) {
    val showBanner = model != null
    // https://stackoverflow.com/a/73282996/16459196
    val bannerModel = rememberConditionally(condition = showBanner) { model }
    AnimatedVisibility(
      visible = showBanner,
      enter =
        fadeIn() +
          slideInVertically(
            initialOffsetY = { fullHeight -> fullHeight / 2 },
            animationSpec = tween(easing = LinearOutSlowInEasing)
          ),
      exit =
        fadeOut() +
          slideOutVertically(
            targetOffsetY = { fullHeight -> fullHeight / 2 },
            animationSpec = tween(easing = LinearOutSlowInEasing)
          )
    ) {
      bannerModel?.let {
        Card(
          modifier = Modifier.fillMaxHeight(),
          verticalArrangement = Center
        ) {
          CardContent(
            model = it,
            titleType = LabelType.Body2Regular,
            titleTreatment =
              if (it.titleTreatment == CardModel.TitleTreatment.Destructive) {
                LabelTreatment.Destructive
              } else {
                LabelTreatment.Primary
              }
          )
        }
      }
    }
  }
}
