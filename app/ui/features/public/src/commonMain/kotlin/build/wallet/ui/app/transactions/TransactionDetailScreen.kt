package build.wallet.ui.app.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormHeaderModel.Alignment.CENTER
import build.wallet.statemachine.core.form.FormHeaderModel.Alignment.LEADING
import build.wallet.statemachine.core.form.FormHeaderModel.SublineTreatment.MONO
import build.wallet.statemachine.core.form.FormHeaderModel.SublineTreatment.REGULAR
import build.wallet.statemachine.core.form.FormHeaderModel.SublineTreatment.SMALL
import build.wallet.statemachine.core.form.RenderContext.Screen
import build.wallet.statemachine.transactions.TransactionDetailModel
import build.wallet.ui.app.core.form.FooterContent
import build.wallet.ui.app.core.form.FormBodyMainContent
import build.wallet.ui.components.header.CustomHeaderContent
import build.wallet.ui.components.header.Header
import build.wallet.ui.components.icon.IconImage
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.label.LabelTreatment.Primary
import build.wallet.ui.components.label.LabelTreatment.Secondary
import build.wallet.ui.components.label.LabelTreatment.Unspecified
import build.wallet.ui.components.label.buildAnnotatedString
import build.wallet.ui.components.toolbar.CollapsibleToolbar
import build.wallet.ui.components.toolbar.CollapsibleToolbarHorizontalPadding
import build.wallet.ui.components.toolbar.CollapsibleToolbarReservedHeight
import build.wallet.ui.components.toolbar.CollapsibleToolbarTitleCollapseRange
import build.wallet.ui.components.toolbar.fadeOutAlpha
import build.wallet.ui.components.toolbar.inlineTitleFadeIn
import build.wallet.ui.compose.thenIf
import build.wallet.ui.system.BackHandler
import build.wallet.ui.theme.LocalTheme
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType

@Composable
fun TransactionDetailScreen(
  modifier: Modifier = Modifier,
  model: TransactionDetailModel,
) {
  val title = model.formHeaderModel.headline
  model.onBack?.let {
    BackHandler(onBack = it)
  }

  val isFullScreen = model.renderContext == Screen
  val background = WalletTheme.colors.background
  val headerToMainContentSpacing = when (model.formHeaderModel.sublineModel) {
    null -> 24.dp
    else -> 16.dp
  }

  val scrollState = rememberScrollState()
  val collapseRangePx = with(LocalDensity.current) { CollapsibleToolbarTitleCollapseRange.toPx() }
  val collapseProgress by remember(scrollState, collapseRangePx) {
    derivedStateOf {
      if (collapseRangePx <= 0f) {
        0f
      } else {
        (scrollState.value / collapseRangePx).coerceIn(0f, 1f)
      }
    }
  }

  Column(
    modifier = modifier
      .background(background)
      .imePadding()
      .thenIf(isFullScreen) {
        Modifier.fillMaxSize()
      }
  ) {
    Box(
      modifier = Modifier.thenIf(isFullScreen) {
        Modifier.weight(1f)
      }
    ) {
      val contentShadowHeight = 12.dp
      Column(
        modifier = Modifier
          .thenIf(isFullScreen) { Modifier.matchParentSize() }
          .background(background)
          .verticalScroll(scrollState)
          .padding(bottom = contentShadowHeight)
          .padding(horizontal = CollapsibleToolbarHorizontalPadding)
      ) {
        Spacer(modifier = Modifier.height(CollapsibleToolbarReservedHeight))
        TransactionDetailHeader(
          headerModel = model.formHeaderModel,
          collapseProgress = collapseProgress
        )
        Column(
          modifier = Modifier.padding(
            top = headerToMainContentSpacing,
            bottom = TransactionDetailBottomContentPadding
          )
        ) {
          FormBodyMainContent(model)
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

      CollapsibleToolbar(
        toolbarModel = model.toolbar,
        title = title,
        collapseProgress = collapseProgress,
        inlineTitleAlpha = inlineTitleFadeIn(start = 0.62f, end = 0.8f)
      )
    }

    when {
      model.primaryButton != null || model.secondaryButton != null -> {
        Column(
          modifier = Modifier
            .background(background)
            .padding(top = 12.dp, bottom = 28.dp)
            .padding(horizontal = CollapsibleToolbarHorizontalPadding)
        ) {
          FooterContent(
            primaryButton = model.primaryButton,
            secondaryButton = model.secondaryButton
          )
        }
      }
    }
  }
}

@Composable
private fun TransactionDetailHeader(
  headerModel: FormHeaderModel,
  collapseProgress: Float,
) {
  val theme = LocalTheme.current
  val horizontalAlignment = when (headerModel.alignment) {
    LEADING -> Alignment.Start
    CENTER -> Alignment.CenterHorizontally
  }
  val textAlignment = when (headerModel.alignment) {
    LEADING -> TextAlign.Start
    CENTER -> TextAlign.Center
  }

  Header(
    horizontalAlignment = horizontalAlignment,
    iconContent = {
      headerModel.iconModel?.let { iconModel ->
        Spacer(modifier = Modifier.height(iconModel.iconTopSpacing?.dp ?: 24.dp))
        IconImage(model = iconModel)
      }
    },
    customContent = {
      headerModel.customContent?.let { customContent ->
        CustomHeaderContent(model = customContent)
      }
    },
    headlineContent = {
      headerModel.headline?.let { headline ->
        Label(
          modifier = Modifier
            .padding(top = 16.dp)
            .alpha(fadeOutAlpha(start = 0.7f, end = 0.86f)(collapseProgress)),
          text = headline,
          type = headerModel.headlineLabelType,
          treatment = when (theme) {
            Theme.DARK -> Unspecified
            Theme.LIGHT -> Primary
          },
          alignment = textAlignment,
          color = when (theme) {
            Theme.DARK -> Color.White
            Theme.LIGHT -> Color.Unspecified
          }
        )
      }
    },
    sublineContent = {
      headerModel.sublineModel?.buildAnnotatedString()?.let { subline ->
        Label(
          modifier = Modifier.padding(top = 8.dp),
          text = subline,
          type = when (headerModel.sublineTreatment) {
            REGULAR -> LabelType.Body2Regular
            SMALL -> LabelType.Body3Regular
            MONO -> LabelType.Body2Mono
          },
          treatment = when (theme) {
            Theme.DARK -> Unspecified
            Theme.LIGHT -> when {
              headerModel.sublineTreatment == MONO &&
                headerModel.sublineModel is LabelModel.ChunkedAddressModel ->
                Primary
              else -> Secondary
            }
          },
          alignment = textAlignment,
          color = when (theme) {
            Theme.DARK -> Color.White
            Theme.LIGHT -> Color.Unspecified
          }
        )
      }
    }
  )
}

private val TransactionDetailBottomContentPadding = 24.dp
