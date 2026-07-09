package build.wallet.ui.app.core.form

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import bitkey.ui.framework_public.generated.resources.Res
import bitkey.ui.framework_public.generated.resources.beneficiary_onboarding_start
import bitkey.ui.framework_public.generated.resources.bitkey_gallery
import bitkey.ui.framework_public.generated.resources.how_inheritance_works
import build.wallet.statemachine.core.form.HeroFormBodyModel
import build.wallet.statemachine.core.form.HeroFormBodyModel.HeroContent
import build.wallet.ui.components.callout.Callout
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment
import build.wallet.ui.components.toolbar.Toolbar
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.system.BackHandler
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType
import org.jetbrains.compose.resources.DrawableResource

/**
 * Slot-based screen for [HeroFormBodyModel].
 *
 * Renders a large hero image at the top that scrolls away under a sticky toolbar
 * containing the leading accessory, followed by a headline/subline, an optional
 * callout, and a footer with primary / secondary buttons.
 */
@Composable
fun HeroFormScreen(
  model: HeroFormBodyModel,
  modifier: Modifier = Modifier,
) {
  BackHandler(onBack = model.onBack)

  val background = WalletTheme.colors.background
  val horizontalPadding = 20.dp
  val headerToMainContentSpacing = 16.dp
  val drawable = model.heroContent.drawable()

  Column(
    modifier = modifier
      .fillMaxSize()
      .background(background)
      .imePadding()
  ) {
    BoxWithConstraints(modifier = Modifier.weight(1f)) {
      val viewportHeight = maxHeight
      val scrollState = rememberScrollState()
      var heroImageHeightPx by remember { mutableIntStateOf(0) }
      val solidToolbarHeightPx = with(LocalDensity.current) {
        (HeroToolbarTopPadding + HeroToolbarHeight + HeroToolbarBottomPadding).toPx()
      }
      val toolbarBackgroundAlpha by remember(scrollState, heroImageHeightPx, solidToolbarHeightPx) {
        derivedStateOf {
          if (heroImageHeightPx <= 0) {
            1f
          } else {
            val fadeStart = (heroImageHeightPx - solidToolbarHeightPx).coerceAtLeast(0f)
            val fadeEnd = heroImageHeightPx.toFloat()
            if (fadeStart >= fadeEnd) {
              if (scrollState.value >= fadeEnd) 1f else 0f
            } else {
              ((scrollState.value - fadeStart) / (fadeEnd - fadeStart)).coerceIn(0f, 1f)
            }
          }
        }
      }

      val contentShadowHeight = 12.dp
      Column(
        modifier = Modifier
          .matchParentSize()
          .background(background)
          .verticalScroll(scrollState)
          .padding(bottom = contentShadowHeight)
      ) {
        Box(modifier = Modifier.onSizeChanged { heroImageHeightPx = it.height }) {
          Toolbar(
            backgroundDrawable = drawable,
            showDesignSystemChrome = false,
            showDesignSystemBottomGradient = false
          )
        }

        val heroImageHeight = with(LocalDensity.current) { heroImageHeightPx.toDp() }
        Column(
          modifier = Modifier
            .heightIn(min = (viewportHeight - heroImageHeight).coerceAtLeast(0.dp))
            .padding(horizontal = horizontalPadding)
        ) {
          Label(
            modifier = Modifier.padding(top = HeroLargeTitleTopSpacing),
            text = model.headline,
            type = LabelType.Title1
          )
          Spacer(modifier = Modifier.height(HeroHeadlineToSublineSpacing))
          Label(
            text = model.subline,
            type = LabelType.Body2Regular,
            treatment = LabelTreatment.Secondary
          )
          model.callout?.let {
            Spacer(modifier = Modifier.height(headerToMainContentSpacing))
            Callout(model = it)
          }

          if (model.scrollContent) {
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(headerToMainContentSpacing))
            FooterContent(
              primaryButton = model.primaryButton,
              secondaryButton = model.secondaryButton,
              tertiaryButton = null
            )
            Spacer(modifier = Modifier.height(HeroBottomContentPadding))
          } else {
            Spacer(modifier = Modifier.height(HeroBottomContentPadding))
          }
        }
      }

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(contentShadowHeight)
          .align(Alignment.BottomCenter)
          .background(
            brush = Brush.verticalGradient(
              colors = listOf(Color.Transparent, background)
            )
          )
      )

      HeroStickyToolbar(
        leadingAccessoryModel = model.leadingAccessory,
        horizontalPadding = horizontalPadding,
        background = background,
        backgroundAlpha = toolbarBackgroundAlpha
      )
    }

    if (!model.scrollContent) {
      Column(
        modifier = Modifier
          .background(background)
          .padding(top = 12.dp, bottom = 28.dp)
          .padding(horizontal = horizontalPadding)
      ) {
        FooterContent(
          primaryButton = model.primaryButton,
          secondaryButton = model.secondaryButton,
          tertiaryButton = null
        )
      }
    }
  }
}

@Composable
private fun HeroStickyToolbar(
  leadingAccessoryModel: ToolbarAccessoryModel?,
  horizontalPadding: Dp,
  background: Color,
  backgroundAlpha: Float,
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .height(
        HeroToolbarTopPadding +
          HeroToolbarHeight +
          HeroToolbarBottomPadding +
          HeroToolbarBottomGradientHeight
      )
  ) {
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(HeroToolbarTopPadding + HeroToolbarHeight + HeroToolbarBottomPadding)
        .let {
          if (backgroundAlpha > 0f) {
            it.background(background.copy(alpha = background.alpha * backgroundAlpha))
          } else {
            it
          }
        }
    ) {
      Box(
        modifier = Modifier
          .padding(
            top = HeroToolbarTopPadding,
            start = horizontalPadding,
            end = horizontalPadding
          )
          .fillMaxWidth()
          .height(HeroToolbarHeight)
      ) {
        Toolbar(
          model = ToolbarModel(leadingAccessory = leadingAccessoryModel),
          showDesignSystemChrome = false
        )
      }
    }

    if (backgroundAlpha > 0f) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(HeroToolbarBottomGradientHeight)
          .align(Alignment.BottomCenter)
          .background(
            brush = Brush.verticalGradient(
              colors = listOf(
                background.copy(alpha = background.alpha * backgroundAlpha),
                background.copy(alpha = background.alpha * backgroundAlpha * 0.65f),
                Color.Transparent
              )
            )
          )
      )
    }
  }
}

private fun HeroContent.drawable(): DrawableResource =
  when (this) {
    HeroContent.InheritanceSetup -> Res.drawable.beneficiary_onboarding_start
    HeroContent.InheritanceExplainer -> Res.drawable.how_inheritance_works
    HeroContent.PromoCodeHeader -> Res.drawable.bitkey_gallery
  }

private val HeroToolbarTopPadding = 8.dp
private val HeroToolbarHeight = 48.dp
private val HeroToolbarBottomPadding = 8.dp
private val HeroToolbarBottomGradientHeight = 20.dp
private val HeroLargeTitleTopSpacing = 24.dp
private val HeroHeadlineToSublineSpacing = 8.dp
private val HeroBottomContentPadding = 24.dp
