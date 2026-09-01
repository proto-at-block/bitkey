package build.wallet.ui.app.core.form

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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import build.wallet.statemachine.core.form.FormMainContentVerticalAlignment
import build.wallet.statemachine.core.form.FormScreenLayoutModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.statemachine.core.form.RenderContext.Screen
import build.wallet.ui.components.label.Label
import build.wallet.ui.components.toolbar.CollapsibleToolbar
import build.wallet.ui.components.toolbar.CollapsibleToolbarReservedHeight
import build.wallet.ui.components.toolbar.CollapsibleToolbarTitleCollapseRange
import build.wallet.ui.components.toolbar.EmptyToolbar
import build.wallet.ui.compose.thenIf
import build.wallet.ui.model.toolbar.ToolbarModel
import build.wallet.ui.model.toolbar.largeTitle
import build.wallet.ui.system.BackHandler
import build.wallet.ui.theme.WalletTheme
import build.wallet.ui.tokens.LabelType

/**
 * A slot-based screen for rendering form views.
 *
 * https://www.figma.com/file/aaOrQTgHXp2NpOYCBDoe5E/Wallet-System?node-id=3477%3A4033&t=JWWhnI4XJy2RNvVd-1.
 */
@Composable
fun FormScreen(
  modifier: Modifier = Modifier,
  onBack: (() -> Unit)?,
  renderContext: RenderContext = Screen,
  horizontalPadding: Int = 20,
  headerToMainContentSpacing: Int = 16,
  background: Color = WalletTheme.colors.background,
  toolbarModel: ToolbarModel? = null,
  layout: FormScreenLayoutModel = FormScreenLayoutModel.Legacy,
  toolbarContent: @Composable (() -> Unit)? = null,
  headerContent: @Composable (() -> Unit)? = null,
  mainContent: @Composable (ColumnScope.() -> Unit)? = null,
  footerContent: @Composable (ColumnScope.() -> Unit)? = null,
) {
  val isFullScreen = renderContext == Screen
  onBack?.let {
    BackHandler(onBack = it)
  }

  val largeTitleLayout = layout as? FormScreenLayoutModel.LargeTitle

  if (largeTitleLayout != null) {
    FormScreenLargeTitle(
      modifier = modifier,
      isFullScreen = isFullScreen,
      background = background,
      horizontalPadding = horizontalPadding.dp,
      headerToMainContentSpacing = headerToMainContentSpacing.dp,
      toolbarModel = toolbarModel,
      contentSpacing = largeTitleLayout.contentSpacing.dp,
      isScrollable = largeTitleLayout.scrollable ||
        largeTitleLayout.mainContentVerticalAlignment == FormMainContentVerticalAlignment.TOP,
      mainContentAlignment = largeTitleLayout.mainContentVerticalAlignment,
      headerContent = headerContent,
      mainContent = mainContent,
      footerContent = footerContent
    )
  } else {
    FormScreenLegacy(
      modifier = modifier,
      isFullScreen = isFullScreen,
      background = background,
      horizontalPadding = horizontalPadding,
      headerToMainContentSpacing = headerToMainContentSpacing,
      toolbarContent = toolbarContent,
      headerContent = headerContent,
      mainContent = mainContent,
      footerContent = footerContent
    )
  }
}

@Composable
private fun FormScreenLegacy(
  modifier: Modifier = Modifier,
  isFullScreen: Boolean,
  background: Color,
  horizontalPadding: Int,
  headerToMainContentSpacing: Int,
  toolbarContent: @Composable (() -> Unit)?,
  headerContent: @Composable (() -> Unit)?,
  mainContent: @Composable (ColumnScope.() -> Unit)?,
  footerContent: @Composable (ColumnScope.() -> Unit)?,
) {
  Column(
    modifier =
      modifier
        .background(background)
        .imePadding()
        .thenIf(isFullScreen) {
          Modifier.fillMaxSize()
        }
  ) {
    val contentShadowHeight = 12.dp
    Box(
      modifier = Modifier
        .thenIf(isFullScreen) { Modifier.weight(1F) }
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
          .thenIf(isFullScreen) { Modifier.matchParentSize() }
          .background(background)
          .verticalScroll(rememberScrollState())
          .padding(bottom = contentShadowHeight)
          .padding(horizontal = horizontalPadding.dp)
      ) {
        if (toolbarContent != null) {
          toolbarContent()
        } else {
          EmptyToolbar()
        }
        headerContent?.invoke()
        Spacer(Modifier.height(headerToMainContentSpacing.dp))
        mainContent?.invoke(this)
      }
      Box(
        modifier =
          Modifier.fillMaxWidth()
            .height(contentShadowHeight)
            .align(Alignment.BottomCenter)
            .background(
              brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, background)
              )
            )
      ) {}
    }
    footerContent?.let {
      Column(
        modifier =
          Modifier
            .background(background)
            .padding(top = 12.dp, bottom = 28.dp)
            .padding(horizontal = horizontalPadding.dp)
      ) {
        footerContent()
      }
    }
  }
}

@Composable
private fun FormScreenLargeTitle(
  modifier: Modifier = Modifier,
  isFullScreen: Boolean,
  background: Color,
  horizontalPadding: Dp,
  headerToMainContentSpacing: Dp,
  toolbarModel: ToolbarModel?,
  contentSpacing: Dp,
  isScrollable: Boolean,
  mainContentAlignment: FormMainContentVerticalAlignment,
  headerContent: @Composable (() -> Unit)?,
  mainContent: @Composable (ColumnScope.() -> Unit)?,
  footerContent: @Composable (ColumnScope.() -> Unit)?,
) {
  val largeTitle = toolbarModel?.largeTitle
  val eyebrow = largeTitle?.eyebrow
  val title = largeTitle?.title
  Column(
    modifier =
      modifier
        .background(background)
        .imePadding()
        .thenIf(isFullScreen) {
          Modifier.fillMaxSize()
        }
  ) {
    Box(
      modifier = Modifier
        .thenIf(isFullScreen) { Modifier.weight(1F) }
    ) {
      if (isScrollable) {
        FormScreenLargeTitleScrollable(
          isFullScreen = isFullScreen,
          background = background,
          horizontalPadding = horizontalPadding,
          headerToMainContentSpacing = headerToMainContentSpacing,
          toolbarModel = toolbarModel,
          contentSpacing = contentSpacing,
          headerContent = headerContent,
          mainContent = mainContent
        )
      } else {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = horizontalPadding)
        ) {
          Column(
            modifier = Modifier
              .fillMaxSize()
          ) {
            Spacer(modifier = Modifier.height(CollapsibleToolbarReservedHeight))
            FormScreenLargeTitleBlock(
              eyebrow = eyebrow,
              title = title
            )
            headerContent?.let {
              if (eyebrow != null || title != null) {
                Spacer(modifier = Modifier.height(headerToMainContentSpacing))
              }
              it()
            }

            Box(
              modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(
                  top = if (headerContent != null && mainContent != null) headerToMainContentSpacing else 0.dp,
                  bottom = when (mainContentAlignment) {
                    FormMainContentVerticalAlignment.TOP,
                    FormMainContentVerticalAlignment.BOTTOM,
                    -> FormScreenBottomContentPadding
                    FormMainContentVerticalAlignment.CENTER -> 0.dp
                  }
                ),
              contentAlignment = when (mainContentAlignment) {
                FormMainContentVerticalAlignment.TOP -> Alignment.TopCenter
                FormMainContentVerticalAlignment.CENTER -> Alignment.Center
                FormMainContentVerticalAlignment.BOTTOM -> Alignment.BottomCenter
              }
            ) {
              mainContent?.let { content ->
                Column(
                  modifier = Modifier.fillMaxWidth(),
                  verticalArrangement = Arrangement.spacedBy(contentSpacing),
                  content = content
                )
              }
            }
          }
        }

        CollapsibleToolbar(
          toolbarModel = toolbarModel,
          collapseProgress = 0f,
          horizontalPadding = horizontalPadding,
          background = background
        )
      }
    }

    footerContent?.let {
      Column(
        modifier =
          Modifier
            .background(background)
            .padding(top = 12.dp, bottom = 28.dp)
            .padding(horizontal = horizontalPadding)
      ) {
        it()
      }
    }
  }
}

@Composable
private fun BoxScope.FormScreenLargeTitleScrollable(
  isFullScreen: Boolean,
  background: Color,
  horizontalPadding: Dp,
  headerToMainContentSpacing: Dp,
  toolbarModel: ToolbarModel?,
  contentSpacing: Dp,
  headerContent: @Composable (() -> Unit)?,
  mainContent: @Composable (ColumnScope.() -> Unit)?,
) {
  val largeTitle = toolbarModel?.largeTitle
  val eyebrow = largeTitle?.eyebrow
  val title = largeTitle?.title
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

  val contentShadowHeight = 12.dp
  Column(
    modifier = Modifier
      .thenIf(isFullScreen) { Modifier.matchParentSize() }
      .background(background)
      .verticalScroll(scrollState)
      .padding(bottom = contentShadowHeight)
      .padding(horizontal = horizontalPadding)
  ) {
    Spacer(modifier = Modifier.height(CollapsibleToolbarReservedHeight))
    Column {
      FormScreenLargeTitleBlock(
        eyebrow = eyebrow,
        title = title,
        collapseProgress = collapseProgress
      )
      Column(
        modifier = Modifier.padding(
          top = if (eyebrow != null || title != null) headerToMainContentSpacing else 0.dp,
          bottom = FormScreenBottomContentPadding
        )
      ) {
        headerContent?.invoke()
        if (headerContent != null && mainContent != null) {
          Spacer(modifier = Modifier.height(headerToMainContentSpacing))
        }
        mainContent?.let { content ->
          Column(
            verticalArrangement = Arrangement.spacedBy(contentSpacing),
            content = content
          )
        }
      }
    }
  }

  Box(
    modifier =
      Modifier.fillMaxWidth()
        .height(contentShadowHeight)
        .align(Alignment.BottomCenter)
        .background(
          brush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, background)
          )
        )
  ) {}

  CollapsibleToolbar(
    toolbarModel = toolbarModel,
    collapseProgress = collapseProgress,
    horizontalPadding = horizontalPadding,
    background = background
  )
}

@Composable
private fun FormScreenLargeTitleBlock(
  eyebrow: String?,
  title: String?,
  collapseProgress: Float = 0f,
) {
  Column {
    eyebrow?.let {
      Label(
        modifier = Modifier
          .padding(top = FormScreenLargeTitleTopSpacing)
          .alpha(formScreenExpandedTitleAlpha(collapseProgress)),
        text = it,
        type = LabelType.Body2Mono
      )
    }
    title?.let {
      Label(
        modifier = Modifier
          .padding(top = if (eyebrow != null) FormScreenEyebrowToTitleSpacing else FormScreenLargeTitleTopSpacing)
          .alpha(formScreenExpandedTitleAlpha(collapseProgress)),
        text = it,
        type = LabelType.Display3
      )
    }
  }
}

private fun formScreenExpandedTitleAlpha(collapseProgress: Float): Float =
  (1f - collapseProgress).coerceIn(0f, 1f)

private val FormScreenLargeTitleTopSpacing = 24.dp
private val FormScreenEyebrowToTitleSpacing = 8.dp
private val FormScreenBottomContentPadding = 24.dp
